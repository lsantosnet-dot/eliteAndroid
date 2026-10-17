package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.Stardust
import com.eliteandroid.ships.Blueprints
import com.eliteandroid.ships.ShipInstance
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

    /** Contagem regressiva do hiperespaço (QQ22). */
    val hyperspace = HyperspaceCountdown()

    /**
     * TT110: sai da estação — ela fica logo atrás (z = −256) com a fenda voltada para nós,
     * e o planeta à frente (SOS1 com z_sign = 1, ou seja z = 65536). Não há sol.
     */
    fun launch(system: PlanetSystem? = null) {
        ships.clear()
        sun = null
        planet = Planet(Vector3(0f, 0f, 65536f), hasCrater = system?.let { (it.techLevel and 2) != 0 } ?: false)
        val st = ShipInstance(Blueprints.CORIOLIS, Vector3(0f, 0f, -256f), NeonPalette.STATION)
            .orient(Vector3(0f, 0f, 1f)) // ZINF + NwS1 (nosev invertido) + TIDY
        st.rollCounter = -127f // NWSPS: contador 255 = rolagem anti-horária máxima, sem amortecimento
        ships += st
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
        station = null
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
     * WARP ("J"): salto dentro do sistema — aproxima planeta e sol em 65536 (z_sign − 1).
     * Recusado se houver estação ou naves por perto, ou se o planeta/sol à frente já estiver
     * a menos de 2 unidades de sign (131072) em todos os eixos.
     */
    fun inSystemJump(): Boolean {
        if (station != null || ships.isNotEmpty()) return false
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
        if (player.updateLaser(f, f / TICK_RATE, controls.fire, view)) checkLaserHits()
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
                10 -> if (!checkAltitude()) return FlightEvent.CRASHED
            }
        }
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
        val st = ShipInstance(Blueprints.CORIOLIS, spot, NeonPalette.STATION)
        st.nosev.set(pl.nosev).scl(-1f); st.roofv.set(pl.roofv); st.orthonormalize()
        st.rollCounter = -127f
        ships += st
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

    /** Bússola: a estação, se existir (zona segura), senão o planeta. */
    val compassTarget: SpaceObject? get() = station ?: planet

    /**
     * HITCH: uma nave está na mira se estiver à frente, com |x| e |y| < 256 e
     * x² + y² menor que a "targetable area" do modelo. Acertar a estação a deixa hostil
     * (MA14); ela não pode ser destruída.
     */
    private fun checkLaserHits() {
        for (ship in ships) {
            val p = ship.position
            if (p.z <= 0f || abs(p.x) >= 256f || abs(p.y) >= 256f) continue
            if (p.x * p.x + p.y * p.y >= ship.blueprint.targetableArea) continue
            if (ship === station) ship.hostile = true
        }
    }

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
