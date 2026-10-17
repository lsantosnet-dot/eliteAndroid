package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.audio.SoundEffects
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.missions.Missions
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.Stardust
import com.eliteandroid.ships.DiscShips
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import com.eliteandroid.ships.SpaceObject
import com.eliteandroid.ships.orient
import kotlin.math.abs
import kotlin.math.sqrt

/** Controles lidos a cada passo (teclado, toque ou joystick). */
class FlightControls {
    var rollKey = 0
    var pitchKey = 0
    var stickX: Float? = null
    var stickY: Float? = null
    var accelerate = false
    var decelerate = false
    var targetSpeed: Float? = null
    var fire = false
}

/** Resultado de um passo de simulação. */
enum class FlightEvent { NONE, DOCKED, BUMPED, CRASHED, HYPERSPACE, STATION_APPEARED }

/**
 * A "bolha local" do universo e a física de voo do original:
 *
 *  - [PlayerShip.applyControls]: loop principal partes 2 e 3 (α, β, DELTA);
 *  - para cada nave: MVEIT (movimento próprio, rotação do universo pela rolagem/arfagem
 *    do jogador e avanço do jogador);
 *  - ISDK: checagem de atracação na estação;
 *  - planeta e sol (SOS1/SOLAR, MV40), surgimento da estação perto do planeta (parte 14),
 *    altitude (parte 15) e o salto dentro do sistema (WARP).
 *
 * O tempo é medido em "ciclos" do loop principal do original. Como a duração de um ciclo
 * variava no BBC Micro, usamos [TICK_RATE] ciclos por segundo e simulamos em passos fixos
 * de 1/[STEP_RATE] s, cada um valendo f = TICK_RATE/STEP_RATE de um ciclo.
 */
class FlightModel {

    val player = PlayerShip()
    val ships = ArrayList<ShipInstance>()
    var station: ShipInstance? = null
        private set
    var planet: Planet? = null
        private set
    /** O sol (só aparece ao chegar pelo hiperespaço; some quando a estação surge). */
    var sun: SpaceObject? = null
        private set
    val dust = Stardust()

    var view = View.FRONT
        set(value) {
            if (field != value) { field = value; dust.reset(value) } // LOOK1 → NWSTARS
        }

    private var accumulator = 0f

    /** Combate, tráfego e mísseis. */
    val combat = Combat(this)

    /** Os efeitos sonoros (NOISE); sem sintetizador, ficam mudos. */
    val sound = SoundEffects()

    init {
        // OOPS: dano que passa dos escudos e não mata soa como uma colisão (EXNO3).
        player.onHullHit = { sound.exno3() }
    }

    /** Morte causada pelo combate (laser ou míssil inimigo) neste passo. */
    var playerDestroyed = false

    /** Contagem regressiva do hiperespaço (QQ22). */
    val hyperspace = HyperspaceCountdown()

    /**
     * LOMOD: o arquivo de naves carregado (D.MOA … D.MOP da versão em disco). Só os tipos
     * presentes nele podem surgir; a estação é a Coriolis ou a Dodecaedro conforme o arquivo.
     */
    var shipFile: Map<Int, com.eliteandroid.ships.ShipBlueprint> = DiscShips.SHIP_FILES[0]
        private set
    var shipFileIndex = 0
        private set

    /** TP: o estado das missões (ver [Missions]); a sessão o mantém e o grava. */
    var missionFlags = 0

    /** THERE: estamos no sistema do Constrictor (missão 1). */
    var constrictorSystem = false
        private set

    /**
     * LOMOD: escolhe o arquivo de naves de um sistema: 2 bits aleatórios, depois governo
     * ≥ 3 (estável) e tech level ≥ 10 (interno) — "A" a "P". No sistema do Constrictor, o
     * arquivo G (o único com ele); carregando os planos da missão 2, C ou D (com Thargoids).
     */
    fun loadShipFile(system: PlanetSystem?) {
        constrictorSystem = system != null && Missions.isConstrictorSystem(system)
        if (system == null) return selectShipFile(0)
        if (constrictorSystem) return selectShipFile(6)
        var a = combat.rng.nextInt(256) and 3
        a = (a shl 1) or (if (system.governmentCode >= 3) 1 else 0)
        a = (a shl 1) or (if (system.techLevel >= 10) 1 else 0)
        if (Missions.carryingPlans(missionFlags)) a = (a and 1) or 2
        selectShipFile(a)
    }

    fun selectShipFile(index: Int) {
        shipFileIndex = index
        shipFile = DiscShips.SHIP_FILES[index]
    }

    /** O tipo [type] está no arquivo de naves carregado (o míssil está sempre). */
    fun hasShip(type: ShipType): Boolean = type == ShipType.MISSILE || shipFile[type.id] === type.blueprint

    /** Tipo da estação no arquivo atual: Dodecaedro (sistemas de tech level alto) ou Coriolis. */
    val stationType: ShipType get() = if (hasShip(ShipType.DODO)) ShipType.DODO else ShipType.STATION

    /** MJ: estamos no witchspace (sem planeta, sol nem estação; só Thargoids). */
    var witchspace = false
        private set

    /**
     * TT110: sai da estação — ela fica logo atrás (z = −256) com a fenda voltada para nós,
     * e o planeta à frente (SOS1 com z_sign = 1, ou seja z = 65536). Não há sol.
     */
    fun launch(system: PlanetSystem? = null) {
        ships.clear()
        witchspace = false
        dust.active = dust.count
        combat.reset()
        system?.let { combat.government = it.governmentCode }
        loadShipFile(system) // RSHIPS
        playerDestroyed = false
        sun = null
        planet = Planet(Vector3(0f, 0f, 65536f), hasCrater = system?.let { (it.techLevel and 2) != 0 } ?: false)
        val st = newStation(Vector3(0f, 0f, -256f)).orient(Vector3(0f, 0f, 1f)) // ZINF + NwS1 (nosev invertido) + TIDY
        addShip(st)
        station = st
        player.reset()
        hyperspace.cancel()
        view = View.FRONT
        dust.reset(view)
    }

    /**
     * TT18 + SOLAR: chegada pelo hiperespaço. Sem estação; o planeta fica à frente e o sol
     * atrás, em posições tiradas da semente do sistema (QQ15):
     *  - planeta: z_sign = (s0_hi AND 7 + 6) / 2 (3..7), e x_sign = y_sign = z_sign/2 com o
     *    sinal vindo do bit que sobrou da divisão;
     *  - sol: z_sign = −((s1_hi AND 7) OR 1); x_sign = x_hi = s2_hi AND 3; y_sign do planeta.
     * (Cada unidade de "sign" vale 65536.)
     */
    fun arrive(system: PlanetSystem) {
        ships.clear()
        witchspace = false
        dust.active = dust.count
        station = null
        combat.reset()
        combat.government = system.governmentCode
        loadShipFile(system)
        playerDestroyed = false
        player.legalStatus = player.legalStatus ushr 1 // SOLAR: LSR FIST
        val seed = system.seed
        val a = (seed.s0Hi and 7) + 6
        val zSign = a ushr 1
        val xyMag = zSign ushr 1
        val xy = if (a and 1 != 0) -xyMag else xyMag
        planet = Planet(
            Vector3(xy * SIGN_UNIT, xy * SIGN_UNIT, zSign * SIGN_UNIT),
            hasCrater = (system.techLevel and 2) != 0,
        )
        val sunX = (seed.s2Hi and 3).let { it * SIGN_UNIT + it * 256f }
        sun = SpaceObject(Vector3(sunX, xy * SIGN_UNIT, -((seed.s1Hi and 7) or 1) * SIGN_UNIT))
        player.resetAfterJump()
        hyperspace.cancel()
        view = View.FRONT
        dust.reset(view)
    }

    /**
     * MJP: o salto falhou e caímos no witchspace, entre as estrelas. Não há planeta, sol nem
     * estação, a poeira estelar quase some (NOSTM = 3) e surgem 4 Thargoids, cada um com um
     * Thargon (GTHG). O status legal não é reduzido (não há SOLAR).
     */
    fun enterWitchspace() {
        ships.clear()
        station = null
        planet = null
        sun = null
        combat.reset()
        playerDestroyed = false
        witchspace = true
        selectShipFile(3) // MJP: arquivo D, um dos que têm Thargoids
        while (ships.count { it.type == ShipType.THARGOID } < 4) combat.spawnThargoid()
        player.resetAfterJump()
        hyperspace.cancel()
        dust.active = 3
        view = View.FRONT
        dust.reset(view)
    }

    /**
     * WARP ("J"): salto dentro do sistema — aproxima planeta e sol em 65536 (z_sign − 1).
     * Recusado se houver estação ou naves por perto, ou se o planeta/sol à frente já estiver
     * a menos de 2 unidades de sign (131072) em todos os eixos.
     */
    fun inSystemJump(): Boolean {
        // O "lixo" (JUNK: cápsulas, placas, contêineres, pedras, Shuttles e Transporters) não
        // impede o salto; qualquer outra nave, sim.
        if (witchspace || station != null || ships.any { !it.killed && !it.type.isJunk }) return false
        for (body in listOfNotNull(planet, sun)) {
            val p = body.position
            if (p.z >= 0f && maxSign(p) < 2) return false
        }
        planet?.position?.let { it.z -= SIGN_UNIT }
        sun?.position?.let { it.z -= SIGN_UNIT }
        return true
    }

    /** Avança o tempo real [delta] (s). Retorna o primeiro evento relevante ocorrido. */
    fun update(delta: Float, controls: FlightControls): FlightEvent {
        accumulator += delta.coerceAtMost(0.25f)
        val stepDt = 1f / STEP_RATE
        var result = FlightEvent.NONE
        while (accumulator >= stepDt) {
            accumulator -= stepDt
            val e = step(TICK_RATE / STEP_RATE, controls)
            if (e == FlightEvent.DOCKED || e == FlightEvent.CRASHED || e == FlightEvent.HYPERSPACE) { accumulator = 0f; return e }
            if (e != FlightEvent.NONE) result = e
        }
        return result
    }

    /** Um passo de [f] ciclos do original. */
    fun step(f: Float, controls: FlightControls): FlightEvent {
        player.applyControls(
            f, controls.rollKey, controls.pitchKey, controls.stickX, controls.stickY,
            controls.accelerate, controls.decelerate, controls.targetSpeed,
        )
        if (player.updateLaser(f, f / TICK_RATE, controls.fire, view)) {
            sound.noise(SoundEffects.Sfx.LASER)
            combat.laserShot(player.lasers[view.ordinal])
        }
        val alpha = player.alpha * f
        val beta = player.beta * f
        val distance = player.speed * f

        for (ship in ships) {
            ship.moveSelf(f)
            ship.applyPlayerMotion(alpha, beta, distance)
        }
        planet?.let { it.moveSelf(f); it.applyPlayerMotion(alpha, beta, distance) }
        sun?.applyPlayerMotion(alpha, beta, distance) // MV40 + MV45: o sol não gira
        dust.update(alpha, beta, distance, view)

        // Tarefas agendadas pelo contador do loop principal (a cada 32 ciclos).
        val before = player.mainLoopCounter.toInt()
        player.updateSystems(f)
        var event = FlightEvent.NONE
        for (tick in before + 1..player.mainLoopCounter.toInt()) {
            when (tick % 32) {
                0 -> if (spawnStation()) event = FlightEvent.STATION_APPEARED
                10 -> {
                    if (player.energy < 50f) { combat.messages += "ENERGY LOW"; sound.beep() }
                    if (!checkAltitude()) return FlightEvent.CRASHED
                }
                20 -> if (!sunHeat()) return FlightEvent.CRASHED
            }
            if (tick % 256 == 0) combat.spawnCheck()
            // MVEIT parte 2: TACTICS a cada 8 ciclos por nave (escalonado pelo slot); mísseis sempre.
            for (ship in ships.toList()) {
                if (ship.hasAi && (ship.type == ShipType.MISSILE || ((tick xor ship.slot) and 7) == 0)) combat.tactics(ship)
            }
        }
        combat.step(f)
        // Naves que se afastam demais saem da bolha local.
        ships.removeAll { it !== station && maxSign(it.position) >= 1 }
        if (combat.checkCollisions() || playerDestroyed) return FlightEvent.CRASHED
        if (hyperspace.update(f)) return FlightEvent.HYPERSPACE

        val docking = checkDocking(f)
        return if (docking != FlightEvent.NONE) docking else event
    }

    /**
     * Parte 14: perto do planeta (todos os eixos < 65536), a estação surge em
     * planeta + 2·nosev (uma altitude igual ao raio) se esse ponto estiver a menos de
     * 192·256 em cada eixo; ela herda a orientação do planeta com o nosev invertido
     * (fenda voltada para o planeta). O sol é retirado (WPLS).
     */
    private fun spawnStation(): Boolean {
        val pl = planet ?: return false
        if (station != null || maxSign(pl.position) > 0) return false
        val spot = Vector3(pl.position).mulAdd(pl.nosev, 2f * Planet.RADIUS)
        if (abs(spot.x) >= 192f * 256f || abs(spot.y) >= 192f * 256f || abs(spot.z) >= 192f * 256f) return false
        val st = newStation(spot)
        st.nosev.set(pl.nosev).scl(-1f); st.roofv.set(pl.roofv); st.orthonormalize()
        addShip(st)
        station = st
        sun = null
        return true
    }

    /**
     * Parte 15: com o planeta a menos de 65536 em todos os eixos, altitude =
     * √(d_hi² − 96²) (d_hi = distância/256; 96 = raio/256). Zero ou menos = colisão.
     * Retorna false se o jogador bateu no planeta.
     */
    private fun checkAltitude(): Boolean {
        player.altitude = 255f
        val pl = planet ?: return true
        if (maxSign(pl.position) > 0) return true
        val d = pl.position.len() / 256f
        val a2 = d * d - 96f * 96f
        if (d * d >= 65536f) return true // MAS3 estourou: ainda longe
        if (a2 <= 0f) return false
        player.altitude = sqrt(a2).coerceAtMost(255f)
        return player.altitude >= 1f
    }

    /**
     * Parte 15, iteração 20: a temperatura da cabine volta a 30 e, perto do sol (todos os
     * eixos < 65536), CABTMP = 255 − A + 30, com A = Σ(coordenada_hi²)/256 (MAS3).
     * Se passar de 255, morremos queimados. A partir de 224, com coletores instalados,
     * recolhemos combustível: QQ14 += DELTA/8, até 70 ("FUEL SCOOPS ON").
     * Retorna false se o jogador morreu.
     */
    private fun sunHeat(): Boolean {
        player.cabinTemp = 30f
        val s = sun ?: return true
        if (station != null || maxSign(s.position) > 0) return true
        val p = s.position
        fun sq(c: Float) = (abs(c) / 256f).toInt().let { it * it / 256 }
        val a = sq(p.x) + sq(p.y) + sq(p.z)
        if (a > 255) return true // MAS3 estourou: A = 255, CABTMP = 31
        val temp = 255 - a + 30
        if (temp > 255) return false
        player.cabinTemp = temp.toFloat()
        if (temp >= 224 && player.fuelScoops) {
            player.fuel = (player.fuel + (player.speed / 8f).toInt()).coerceAtMost(70f)
            combat.messages += "FUEL SCOOPS ON"
            sound.beep()
        }
        return true
    }

    /**
     * "C" (MA64): com o computador de atracação, dentro da zona segura e a estação não
     * hostil, a atracação é imediata (GOIN).
     */
    fun canUseDockingComputer(): Boolean {
        val st = station ?: return false
        return player.dockingComputer && !st.hostile
    }

    /**
     * NWSPS: a estação do arquivo de naves, com IA e E.C.M. (%10000001) e rolagem
     * anti-horária máxima sem amortecimento (contador 255).
     */
    private fun newStation(pos: Vector3): ShipInstance {
        val st = ShipInstance(stationType, pos)
        st.color = NeonPalette.STATION
        st.aiFlag = 0b10000001
        st.rollCounter = -127f
        return st
    }

    /** Põe [ship] na bolha local sem checagens (lançamento, testes e capturas de tela). */
    fun addShip(ship: ShipInstance) {
        ship.slot = combat.nextSlot()
        ships += ship
    }

    /**
     * NWSHP: cria uma nave, se o tipo estiver no arquivo de naves e houver vaga na bolha
     * local (NOSH = 12 posições, uma delas do planeta e outra do sol ou da estação).
     */
    fun spawn(ship: ShipInstance): Boolean {
        if (!hasShip(ship.type)) return false
        val used = ships.count { !it.type.isStation } + 2
        if (used >= NOSH) return false
        addShip(ship)
        return true
    }

    /** Bússola: a estação, se existir (zona segura), senão o planeta. */
    val compassTarget: SpaceObject? get() = station ?: planet

    /**
     * Loop principal partes 7 e 9: com a estação a menos de 128 unidades em cada eixo,
     * o jogador está "dentro" dela. Atraca se:
     *  1. a estação não é hostil;
     *  2. nosev_z ≤ −86/96: a estação aponta para nós (ângulo de aproximação < 26°);
     *  3. o vetor até a estação tem z ≥ 89/96 (cone seguro de 22°);
     *  4. |roofv_x| ≥ 80/96: a fenda está perto da horizontal.
     * Caso contrário: abaixo de velocidade 5 é só uma batida (MA67); acima, morte.
     */
    private fun checkDocking(f: Float): FlightEvent {
        val st = station ?: return FlightEvent.NONE
        val p = st.position
        if (abs(p.x) >= 128f || abs(p.y) >= 128f || abs(p.z) >= 128f) return FlightEvent.NONE

        if (isDockingAligned(st)) return FlightEvent.DOCKED
        if (player.speed < 5f) return if (player.bump(f)) FlightEvent.CRASHED else FlightEvent.BUMPED
        return FlightEvent.CRASHED
    }

    companion object {
        /** NOSH: posições da bolha local (planeta, sol/estação e naves). */
        const val NOSH = 12

        /** Uma unidade do byte de "sinal" das coordenadas de 24 bits do original. */
        const val SIGN_UNIT = 65536f

        /** MAS2: maior |coordenada| em unidades de sign (0 = tudo < 65536). */
        fun maxSign(p: Vector3): Int = (maxOf(abs(p.x), abs(p.y), abs(p.z)) / SIGN_UNIT).toInt()

        /** Ciclos do loop principal por segundo (calibração; no BBC variava com a carga). */
        const val TICK_RATE = 12f
        const val STEP_RATE = 60f

        fun isDockingAligned(st: ShipInstance): Boolean {
            if (st.hostile) return false
            if (st.nosev.z > -86f / 96f) return false
            val len = st.position.len()
            if (len > 1e-3f && st.position.z / len < 89f / 96f) return false
            return abs(st.roofv.x) >= 80f / 96f
        }
    }
}
