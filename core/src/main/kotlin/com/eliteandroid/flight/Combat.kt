package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.economy.Commodities
import com.eliteandroid.ships.Newb
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Combate e tráfego, portados da versão em disco do BBC Elite (que tem as naves extras):
 *
 *  - surgimento de naves (loop principal do jogo, partes 2 e 3) a cada 256 ciclos, só dos
 *    tipos presentes no arquivo de naves carregado ([FlightModel.shipFile]);
 *  - IA TACTICS (partes 1-7) guiada pelas flags NEWB: comerciantes rumo ao planeta ou
 *    atracando (DOCKIT), caçadores de recompensas, piratas, polícia, a estação lançando
 *    Shuttles, Transporters e Vipers, a Anaconda lançando Worms, cápsulas de fuga,
 *    mísseis, laser, manobras e aceleração;
 *  - nossos mísseis (T arma, M dispara, U desarma; trava pelo HITCH);
 *  - laser do jogador (HITCH + MA14/ANGRY), colisões (MA58), mortes com recompensa,
 *    contêineres (parte 5), status legal (FIST) e contagem de abates (TALLY);
 *  - E.C.M. inimigo destruindo mísseis.
 *
 * Os limiares do original usam vetores com 1.0 = 96 e produtos escalares com 1.0 = 36;
 * aqui os vetores são unitários e os limiares estão convertidos (ex.: 32/36).
 */
class Combat(private val model: FlightModel) {

    var rng: Random = Random.Default
    private fun dornd() = rng.nextInt(256)

    /** Mensagens para o HUD ("INCOMING MISSILE", recompensas…). */
    val messages = ArrayDeque<String>()

    /** Recompensa ganha (décimos de crédito) — a sessão credita no comandante. */
    var onBounty: (Int) -> Unit = {}

    /** Governo do sistema atual (0 = anarquia … 7 = corporativo). */
    var government = 7

    /** BAD: carga ilegal (escravos + narcóticos)·2 + armas de fogo. */
    var contraband: () -> Int = { 0 }

    /** Contêiner recolhido com o item [index]; retorna false se não houver lugar no porão. */
    var onScoop: (Int) -> Boolean = { false }

    // ------------------------------------------------------------ nossos mísseis

    /** MSAR: míssil armado procurando alvo. */
    var missileArmed = false
        private set
    /** MSTG: alvo travado. */
    var missileTarget: ShipInstance? = null
        private set

    /** ECMA: contagem do E.C.M. ativo (em ciclos). */
    var ecmCycles = 0f
        private set
    val ecmActive: Boolean get() = ecmCycles > 0f
    /** ECMP: o E.C.M. ligado é o nosso (gasta energia). */
    var ourEcm = false
        private set

    /** Ciclos restantes do clarão da bomba de energia. */
    var bombFlash = 0f
        private set

    /** EV: atraso para novos piratas/caçadores. */
    private var extraVesselDelay = 0
    private var nextSlot = 0

    fun reset() {
        missileArmed = false
        missileTarget = null
        ecmCycles = 0f
        ourEcm = false
        bombFlash = 0f
        extraVesselDelay = 0
        messages.clear()
    }

    fun nextSlot() = nextSlot++.also { if (nextSlot > 1_000_000) nextSlot = 0 }

    /** "T": arma o míssil (se houver). */
    fun armMissile() {
        if (model.player.missiles > 0 && missileTarget == null) missileArmed = true
    }

    /** "U": desarma. */
    fun unarmMissile() {
        missileArmed = false
        missileTarget = null
    }

    /**
     * "M" / FRMIS + FRS1: com alvo travado, lança o míssil logo abaixo da nossa linha de
     * visão (0, −28, 14), apontando para a frente, ao dobro da nossa velocidade.
     */
    fun fireMissile(): Boolean {
        val target = missileTarget ?: return false
        if (model.player.missiles <= 0 || target.killed) { unarmMissile(); return false }
        val m = ShipInstance(ShipType.MISSILE, Vector3(0f, -28f, 14f))
        m.setOrientation(Vector3(0f, 0f, 1f))
        m.speed = (2f * model.player.speed).coerceAtMost(ShipType.MISSILE.blueprint.maxSpeed.toFloat())
        m.aiFlag = 0x80
        m.missileTarget = target
        if (!model.spawn(m)) return false // bolha cheia
        angry(target)
        model.player.missiles--
        unarmMissile()
        return true
    }

    /**
     * "E" (MA24 + ECBLB2): liga o nosso E.C.M. por 32 ciclos, se estiver instalado e não
     * houver outro E.C.M. em andamento. Enquanto ligado, destrói todos os mísseis.
     */
    fun fireEcm(): Boolean {
        if (!model.player.hasEcm || ecmActive) return false
        ecmCycles = 32f
        ourEcm = true
        return true
    }

    /**
     * TAB (MA24 + parte 5): a bomba de energia destrói todas as naves por perto, menos a
     * estação, contando os abates. Uso único.
     */
    fun detonateBomb(): Boolean {
        if (!model.player.energyBomb) return false
        model.player.energyBomb = false
        for (ship in model.ships.toList()) {
            if (!ship.type.isStation && !ship.killed) kill(ship, byUs = true)
        }
        bombFlash = 4f
        return true
    }

    /** Uma passada de [f] ciclos: trava de míssil, E.C.M. e explosões. */
    fun step(f: Float) {
        if (bombFlash > 0f) bombFlash = (bombFlash - f).coerceAtLeast(0f)
        // MA16/MA69: o nosso E.C.M. gasta 1 de energia por ciclo; sem energia, desliga.
        if (ourEcm) {
            val p = model.player
            p.energy = (p.energy - f).coerceAtLeast(0f)
            if (p.energy <= 0f) ecmCycles = 0f
        }
        if (ecmCycles > 0f) ecmCycles = (ecmCycles - f).coerceAtLeast(0f)
        if (ecmCycles <= 0f) ourEcm = false
        // Míssil armado: trava no primeiro alvo que estiver na mira (HITCH).
        if (missileArmed && missileTarget == null) {
            model.ships.firstOrNull { !it.killed && it.type != ShipType.MISSILE && inCrosshairs(it) }
                ?.let { missileTarget = it }
        }
        missileTarget?.let { if (it.killed || it !in model.ships) unarmMissile() }
        for (ship in model.ships) {
            if (ship.killed) ship.cloudCounter += 4f * f
            if (ship.laserFlash > 0f) ship.laserFlash -= f
        }
        // Explosões que terminaram e naves que atracaram ou foram recolhidas (NEWB bit 7).
        model.ships.removeAll { (it.killed && it.cloudCounter >= 256f) || (it.newb and Newb.GONE != 0) }
    }

    // ------------------------------------------------------------ laser do jogador

    /** HITCH na vista atual: à frente, |x|,|y| < 256 e x² + y² < área-alvo. */
    fun inCrosshairs(ship: ShipInstance): Boolean {
        val p = model.view.toView(ship.position.x, ship.position.y, ship.position.z, tmp)
        if (p.z <= 0f || abs(p.x) >= 256f || abs(p.y) >= 256f) return false
        return p.x * p.x + p.y * p.y < ship.blueprint.targetableArea
    }

    private val tmp = Vector3()

    /** Loop principal parte 6: nosso tiro de laser com potência [power] (LAS). */
    fun laserShot(power: Int) {
        for (ship in model.ships.toList()) { // cópia: abates podem soltar contêineres
            if (ship.killed || !inCrosshairs(ship)) continue
            if (ship.type.isStation) { angry(ship); continue } // indestrutível
            ship.energy -= power
            if (ship.energy > 0f) angry(ship)
            else kill(ship, byUs = true)
        }
    }

    /**
     * ANGRY: atingir a estação, ou uma nave "inocente" (NEWB bit 5), deixa a estação
     * hostil. Naves com IA passam a agir agressivamente, aceleram (2) e mergulham (arfagem
     * 4); de CYL em diante (não lixo), ficam hostis (NEWB bit 2).
     */
    fun angry(ship: ShipInstance) {
        if (ship.type.isStation) { ship.hostile = true; return }
        if (ship.newb and Newb.INNOCENT != 0) model.station?.let { it.hostile = true }
        if (ship.aiFlag == 0) return
        ship.aiFlag = ship.aiFlag or 0x80
        ship.acceleration = 2f
        ship.pitchCounter = 4f
        if (ship.type.id >= ShipType.COBRA_TRADER.id) ship.hostile = true
    }

    /**
     * Morte (bit 7 do #31): explosão; recompensa (parte 12, nenhuma no witchspace); abater
     * a polícia (NEWB bit 6) nos torna fugitivos (FIST ≥ 64); 50% de chance de soltar até
     * "max canisters" contêineres.
     */
    fun kill(ship: ShipInstance, byUs: Boolean) {
        if (ship.killed) return
        ship.killed = true
        ship.cloudCounter = 18f
        if (byUs) model.player.kills++
        if (ship.newb and Newb.COP != 0) model.player.legalStatus = model.player.legalStatus or 64
        val bounty = ship.blueprint.bounty
        if (bounty > 0 && !model.witchspace) {
            onBounty(bounty)
            messages += "BOUNTY %d.%d CR".format(bounty / 10, bounty % 10)
        }
        if (dornd() < 128) {
            var count = dornd() and ship.blueprint.maxCanisters
            while (count-- > 0) spawnChild(ship, ShipType.CANISTER, 0)
        }
        if (missileTarget === ship) unarmMissile()
    }

    // ------------------------------------------------------------ colisões

    /**
     * MA58: uma nave a menos de 64 unidades em cada eixo bateu em nós — ela é destruída e
     * o dano é 128 + energia/2 (no escudo do lado da batida). Mísseis têm checagem própria.
     *
     * Parte 8: com coletores instalados, o que passar por baixo da nave (y < 0) e puder ser
     * recolhido vira carga — o contêiner, um item aleatório 0..7; os demais, o item do byte
     * #0 do modelo (cápsula = escravos, placa = ligas, fragmento = minerais, Thargon = Alien
     * Items). Sem lugar no porão, o objeto se desfaz sem causar dano.
     */
    fun checkCollisions(): Boolean {
        for (ship in model.ships.toList()) {
            if (ship.killed || ship.type.isStation || ship.type == ShipType.MISSILE) continue
            val p = ship.position
            if (abs(p.x) >= 64f || abs(p.y) >= 64f || abs(p.z) >= 64f) continue
            val item = if (ship.type == ShipType.CANISTER) dornd() and 7 else ship.blueprint.scoopItem
            if (item >= 0 && model.player.fuelScoops && p.y < 0f) {
                if (onScoop(item)) {
                    ship.newb = ship.newb or Newb.GONE
                    model.ships.remove(ship)
                    messages += Commodities.ALL[item].name.uppercase()
                } else {
                    kill(ship, byUs = false)
                }
                continue
            }
            val damage = 128f + ship.energy.coerceAtLeast(0f) / 2f
            kill(ship, byUs = false)
            if (model.player.damage(damage, front = p.z >= 0f)) return true
        }
        return false
    }

    // ------------------------------------------------------------ surgimento

    /** JUNK: lixo na bolha (cápsulas, placas, contêineres, pedras, Shuttles, Transporters). */
    private val junk: Int get() = model.ships.count { it.type.isJunk && !it.killed }

    private fun many(type: ShipType) = model.ships.count { it.type == type && !it.killed }

    /** Loop principal do jogo, partes 2 e 3 (chamado quando MCNT dá a volta, a cada 256). */
    fun spawnCheck() {
        if (model.witchspace) return // no witchspace nada novo aparece
        val safeZone = model.station != null
        // Parte 2: 13% de chance de algo inofensivo (com menos de 3 "lixos" por perto).
        if (dornd() < 35 && junk < 3) {
            val a = dornd(); val x = dornd()
            val xHi = if (rng.nextBoolean()) 2 else 0
            val pos = Vector3(signed(a, xHi * 256f + a), signed(x, x.toFloat()), 38f * 256f)
            if (rng.nextBoolean()) { // DORND com V = 1: comerciante (MTT4)
                spawnTrader(pos)
                return
            }
            val r = dornd(); val rx = dornd()
            if (!safeZone) {
                // OIL + (0 ou 1) + C: contêiner (2%), pedregulho ou asteroide.
                val type = ShipType.byId(5 + (dornd() and 1) + (if (dornd() >= 10) 1 else 0))!!
                val junkShip = ShipInstance(type, pos)
                junkShip.rollCounter = (r or 0b01101111).toSigned()
                if (rng.nextBoolean()) junkShip.speed = ((rx and 31) or 16).toFloat()
                else junkShip.pitchCounter = (rx or 0x7F).toSigned()
                model.spawn(junkShip)
            }
        }
        if (safeZone) return

        // Parte 3: polícia, mais provável quanto pior o nosso status (BAD·2, ou FIST com polícia por perto).
        var badness = (contraband() * 2).coerceAtMost(255)
        if (many(ShipType.VIPER) > 0) badness = badness or model.player.legalStatus
        val (pos, ai, a) = ze()
        if (a < badness) model.spawn(ShipInstance(ShipType.VIPER, pos).also { it.aiFlag = ai })
        if (many(ShipType.VIPER) > 0) return

        // Piratas ou caçadores de recompensas, mais comuns em governos perigosos.
        extraVesselDelay--
        if (extraVesselDelay >= 0) return
        extraVesselDelay = 0
        val r = dornd()
        if (government != 0) {
            if (r >= 120) return
            if ((r and 7) < government) return
        }
        val (pos2, ai2, a2) = ze()
        if (a2 >= 100) {
            // mt1: bando de 1 a 4 piratas, de Sidewinder (PACK) a Cobra Mk III pirata; se o
            // tipo sorteado não estiver no arquivo de naves, tenta o anterior.
            val n = a2 and 3
            extraVesselDelay = n
            repeat(n + 1) {
                var t = dornd() and dornd() and 7
                while (t >= 0) {
                    val type = ShipType.byId(ShipType.SIDEWINDER.id + t)!!
                    if (model.spawn(ShipInstance(type, Vector3(pos2)).also { it.aiFlag = ai2 })) break
                    t--
                }
            }
        } else {
            // Caçador solitário: Cobra Mk III, Asp Mk II, Python ou Fer-de-Lance (CYL2 + 0..3).
            extraVesselDelay++
            val type = ShipType.byId(ShipType.COBRA_PIRATE.id + (a2 and 3))!!
            model.spawn(ShipInstance(type, pos2).also { it.aiFlag = ai2 })
        }
    }

    /**
     * MTT4: comerciante (Cobra Mk III, Python, Boa ou Anaconda) rolando devagar; metade
     * ganha IA e vai atracar na estação (NEWB bit 4).
     */
    private fun spawnTrader(pos: Vector3) {
        val r = dornd() ushr 1
        val r2 = dornd()
        val docking = r2 and 0x80 == 0
        val ai = if (docking) r or 0xC0 else r
        val type = ShipType.byId(ShipType.COBRA_TRADER.id + ((if (docking) ai else r2) and 2) + (if (rng.nextBoolean()) 1 else 0))!!
        val t = ShipInstance(type, pos)
        t.aiFlag = ai
        t.rollCounter = r.toFloat()
        t.speed = ((r and 15) or 16).toFloat()
        if (docking) t.newb = t.newb or Newb.DOCKING
        model.spawn(t)
    }

    /**
     * GTHG: um Thargoid (IA %11111111: agressividade máxima e E.C.M.) numa posição do Ze,
     * acompanhado de um Thargon.
     */
    fun spawnThargoid() {
        val (pos, _, _) = ze()
        model.spawn(ShipInstance(ShipType.THARGOID, Vector3(pos)).also { it.aiFlag = 0xFF })
        model.spawn(ShipInstance(ShipType.THARGON, Vector3(pos)).also { it.aiFlag = 0xFF })
    }

    /** Ze: nave agressiva em (±6400, ±6400, 6400), IA = %11000000 + E.C.M. em 4%. */
    private fun ze(): Triple<Vector3, Int, Int> {
        val a = dornd(); val x = dornd()
        val d = 25f * 256f
        val pos = Vector3(if (a and 0x80 != 0) -d else d, if (x and 0x80 != 0) -d else d, d)
        val ai = 0xC0 or (if (dornd() >= 245) 1 else 0)
        return Triple(pos, ai, dornd())
    }

    private fun signed(signSource: Int, magnitude: Float) = if (signSource and 0x80 != 0) -magnitude else magnitude
    private fun Int.toSigned(): Float = (this and 0x7F).toFloat().let { if (this and 0x80 != 0) -it else it }

    // ------------------------------------------------------------ TACTICS

    private val xx15 = Vector3()
    private val k3 = Vector3()

    /** TACTICS para uma nave com IA (a cada 8 ciclos por nave; mísseis a cada ciclo). */
    fun tactics(ship: ShipInstance) {
        if (ship.killed) return
        if (ship.type == ShipType.MISSILE) { missileTactics(ship); return }
        if (ship.type.isStation) { stationTactics(ship); return }

        // TA13: recarrega 1 de energia.
        if (ship.energy < ship.blueprint.maxEnergy) ship.energy += 1f

        // TA21: sem a nave-mãe, o Thargon perde o E.C.M. e fica à deriva com metade da velocidade.
        if (ship.type == ShipType.THARGON && many(ShipType.THARGOID) == 0) {
            ship.aiFlag = ship.aiFlag and 0xFE
            ship.speed /= 2f
            return
        }

        // TA14/TN1: comerciantes só agem 39% das vezes.
        if (ship.newb and Newb.TRADER != 0 && dornd() >= 100) return
        // Caçadores de recompensas atacam infratores graves e fugitivos (FIST ≥ 40).
        if (ship.newb and Newb.BOUNTY_HUNTER != 0 && model.player.legalStatus >= 40) ship.hostile = true
        if (!ship.hostile) {
            if (ship.newb and Newb.DOCKING != 0) dockIt(ship) else goToPlanet(ship)
            return
        }
        // TN3: na zona segura, piratas perdem a agressividade.
        if (ship.newb and Newb.PIRATE != 0 && model.station != null) ship.aiFlag = ship.aiFlag and 0x81

        // TA19: XX15 = vetor unitário de nós até a nave; CNT = nosev · XX15.
        xx15.set(ship.position).nor()
        var cnt = ship.nosev.dot(xx15)

        // A Anaconda lança Worms (22%).
        if (ship.type == ShipType.ANACONDA && dornd() >= 200) {
            spawnChild(ship, ShipType.WORM, 0b11110001)
            return
        }

        // 2.5%: uma rolagem forte.
        if (dornd() >= 250) ship.rollCounter = (dornd() or 104).toSigned()

        val maxE = ship.blueprint.maxEnergy.toFloat()
        if (ship.energy <= maxE / 2f) {
            // Quase sem energia: abandona a nave numa cápsula de fuga, se tiver uma (E% bit 7).
            if (ship.energy <= maxE / 8f && dornd() >= 230 && ship.type.defaultNewb and 0x80 != 0) {
                ship.aiFlag = 0
                spawnChild(ship, ShipType.ESCAPE_POD, 0b11111110)
                return
            }
            // ta3: dispara um míssil (o Thargoid, um Thargon), mais provável quanto mais tiver.
            if (ship.missiles > 0 && (dornd() and 31) < ship.missiles && !ecmActive) {
                ship.missiles--
                if (ship.type == ShipType.THARGOID) {
                    spawnChild(ship, ShipType.THARGON, ship.aiFlag)
                } else if (spawnChild(ship, ShipType.MISSILE, 0b11000000, hostileToUs = true)) {
                    messages += "INCOMING MISSILE"
                }
                return
            }
        }

        // TA3: laser, se estiver a menos de 32·256 em cada eixo e apontando para nós.
        val p = ship.position
        val inRange = abs(p.x) < 8192f && abs(p.y) < 8192f && abs(p.z) < 8192f
        if (inRange && ship.blueprint.laserPower > 0 && cnt <= -32f / 36f) {
            ship.firingLaser = true
            if (cnt <= -35f / 36f) {
                // HIT: dano = byte #19 / 2, no escudo do lado em que a nave está.
                if (model.player.damage((ship.blueprint.laserByte / 2).toFloat(), front = p.z >= 0f)) {
                    model.playerDestroyed = true
                }
                ship.acceleration -= 1f
                applyAcceleration(ship)
                return
            }
        }

        // TA4/TA5: decide se vem em nossa direção ou se afasta.
        val close = abs(p.z) < 768f && abs(p.x) < 512f && abs(p.y) < 512f
        val attack = !close && (dornd() or 0x80) < ship.aiFlag
        if (attack) { xx15.scl(-1f); cnt = -cnt } // TA20: agora XX15 vai da nave até nós
        steer(ship, xx15, cnt)
    }

    /**
     * Estação: hostil, lança Vipers (6%, até 4 por perto); senão, sem Transporter por perto,
     * lança de vez em quando (1.2%) um Shuttle ou um Transporter.
     */
    private fun stationTactics(st: ShipInstance) {
        if (st.hostile) {
            if (dornd() < 240 || many(ShipType.VIPER) >= 4) return
            spawnChild(st, ShipType.VIPER, 0b11110001)
            return
        }
        if (many(ShipType.TRANSPORTER) > 0 || dornd() < 253) return
        spawnChild(st, if (rng.nextBoolean()) ShipType.SHUTTLE else ShipType.TRANSPORTER, 0b11110001)
    }

    /** GOPL (SPS1 + TA151): segue na direção do planeta. */
    private fun goToPlanet(ship: ShipInstance) {
        val pl = model.planet ?: return
        ta151(ship, xx15.set(pl.position).nor(), rat = 3, rat2 = 4, cnt2 = 22)
    }

    /**
     * TA151: CNT = nosev · direção; virada sempre aplicada (RAT2 = 0) se estiver apontando
     * para o lado oposto (CNT ≤ −24/36); depois a manobra normal (TA15).
     */
    private fun ta151(ship: ShipInstance, dir: Vector3, rat: Int, rat2: Int, cnt2: Int) {
        val cnt = ship.nosev.dot(dir)
        steer(ship, dir, cnt, rat, if (cnt <= -24f / 36f) 0 else rat2, cnt2)
    }

    /**
     * DOCKIT (versão das naves não jogadoras): longe da estação, segue para o planeta; à
     * frente da fenda (cone de ~13°), vira para a estação devagar (velocidade 1) e, quando
     * aponta para ela, alinha a rolagem e atraca — some da bolha (NEWB bit 7); fora do
     * cone, vai para o ponto ideal de aproximação, 8 unidades de nosev à frente da fenda.
     */
    private fun dockIt(ship: ShipInstance) {
        val st = model.station ?: return goToPlanet(ship)
        k3.set(ship.position).sub(st.position) // VCSU1: da estação até a nave
        if (FlightModel.maxSign(k3) > 0) return goToPlanet(ship)
        xx15.set(k3).nor()
        if (st.nosev.dot(xx15) < 35f / 36f) {
            // PH1: rumo ao ponto ideal (estação + 768·nosev).
            xx15.set(st.position).mulAdd(st.nosev, 768f).sub(ship.position).nor()
            ta151(ship, xx15, rat = 3, rat2 = 6, cnt2 = 29)
            return
        }
        if (ship.nosev.dot(xx15) <= -34f / 36f) {
            // PH3/PH32: apontando para a estação — iguala a rolagem e atraca (TN13).
            ship.pitchCounter = 0f
            ship.rollCounter = 0f
            if (abs(st.roofv.dot(ship.sidev)) >= 33f / 36f) {
                ship.acceleration += 1f // TN11
                ship.rollCounter = 127f
            } else {
                slowDown(ship)
            }
            ship.newb = ship.newb or Newb.GONE
            return
        }
        // PH2: vira para a estação devagar.
        ta151(ship, xx15.scl(-1f), rat = 3, rat2 = 6, cnt2 = 29)
        slowDown(ship)
    }

    /** PH22: aceleração 0 e velocidade 1. */
    private fun slowDown(ship: ShipInstance) {
        ship.acceleration = 0f
        ship.speed = 1f
    }

    /**
     * TA15/TA6/TA9 com o nroll do original: arfagem ±[rat] para o lado de XX15 (0 se
     * |roofv·XX15| < [rat2]/72), rolagem ±[rat] se não estiver rolando forte, e aceleração
     * 3 se estiver alinhada (CNT ≥ [cnt2]/36) ou −1 (míssil −2) se desalinhada (|CNT| ≥ 18/36).
     */
    private fun steer(ship: ShipInstance, dir: Vector3, cnt: Float, rat: Int = 3, rat2: Int = 4, cnt2: Int = 22) {
        val r = rat.toFloat()
        val min = rat2 / 72f
        val roofDot = ship.roofv.dot(dir)
        val pitchNegative = roofDot >= 0f // sinal oposto ao do produto escalar
        ship.pitchCounter = if (abs(roofDot) >= min) (if (pitchNegative) -r else r) else 0f
        if (abs(ship.rollCounter) < 16f) {
            val sideDot = ship.sidev.dot(dir)
            val rollNegative = (sideDot < 0f) == pitchNegative
            ship.rollCounter = if (abs(sideDot) >= min) (if (rollNegative) -r else r) else 0f
        }
        when {
            cnt >= cnt2 / 36f -> ship.acceleration = 3f
            abs(cnt) >= 18f / 36f -> ship.acceleration = if (ship.type == ShipType.MISSILE) -2f else -1f
        }
        applyAcceleration(ship)
    }

    /** MVEIT parte 4: velocidade += aceleração, entre 0 e a máxima do modelo; aceleração zera. */
    private fun applyAcceleration(ship: ShipInstance) {
        ship.speed = (ship.speed + ship.acceleration).coerceIn(0f, ship.blueprint.maxSpeed.toFloat())
        ship.acceleration = 0f
    }

    /** TACTICS parte 1: mísseis. */
    private fun missileTactics(m: ShipInstance) {
        if (ecmActive) { kill(m, byUs = false); return }
        val p = m.position
        if (m.isHostileMissile) {
            if (abs(p.x) < 256f && abs(p.y) < 256f && abs(p.z) < 256f) {
                // Acertou-nos: dano 250.
                kill(m, byUs = false)
                if (model.player.damage(250f, front = p.z >= 0f)) model.playerDestroyed = true
                return
            }
            xx15.set(p).nor()
            val cnt = m.nosev.dot(xx15)
            steer(m, xx15.scl(-1f), -cnt) // TA20: em nossa direção
            return
        }
        val target = m.missileTarget
        if (target == null || target.killed || target !in model.ships) { kill(m, byUs = false); return }
        k3.set(p).sub(target.position)
        if (abs(k3.x) < 256f && abs(k3.y) < 256f && abs(k3.z) < 256f) {
            // Chegou ao alvo: a estação só destrói o míssil; outras naves explodem.
            if (!target.type.isStation) kill(target, byUs = true)
            kill(m, byUs = false)
            if (p.len() < 256f) model.player.damage(80f, front = p.z >= 0f)
            return
        }
        // 6%: o alvo tenta o E.C.M.
        if (dornd() < 16 && target.hasEcm) {
            ecmCycles = 32f
            messages += "E.C.M."
            return
        }
        xx15.set(k3).nor() // do alvo até o míssil
        val cnt = m.nosev.dot(xx15)
        steer(m, xx15.scl(-1f), -cnt) // TA20: em direção ao alvo
    }

    /**
     * SFS1: lança uma nave "filha" ([type]) da posição e orientação da nave [parent], com o
     * byte de IA [ai]; ela herda os bits hostil, pirata e atracando do NEWB do pai. Da
     * estação, sai 192 unidades à frente da fenda a velocidade 32; pedaços (placa, contêiner,
     * pedras) saem girando a até 15. Mísseis inimigos ([hostileToUs]) miram em nós.
     * Retorna false se a nave não pôde ser criada (bolha cheia ou fora do arquivo de naves).
     */
    private fun spawnChild(parent: ShipInstance, type: ShipType, ai: Int, hostileToUs: Boolean = false): Boolean {
        val child = ShipInstance(type, Vector3(parent.position))
        child.nosev.set(parent.nosev); child.roofv.set(parent.roofv); child.orthonormalize()
        child.newb = (parent.newb and 0b00011100) or child.newb
        child.aiFlag = ai
        child.speed = if (type == ShipType.MISSILE) parent.speed * 2f else parent.speed
        if (parent.type.isStation) {
            child.position.mulAdd(parent.nosev, 192f)
            child.speed = 32f
        }
        if (type.id in ShipType.ALLOY_PLATE.id..ShipType.SPLINTER.id) {
            val r = dornd(); val x = dornd()
            child.pitchCounter = ((r shl 1) and 0xFF).toSigned()
            child.speed = (x and 15).toFloat()
            child.rollCounter = if (r and 0x80 != 0) 127f else -127f
        }
        if (hostileToUs) child.aiFlag = child.aiFlag or 0x40
        return model.spawn(child)
    }
}
