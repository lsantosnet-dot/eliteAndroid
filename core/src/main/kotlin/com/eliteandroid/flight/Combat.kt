package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Combate e tráfego, portados do original:
 *
 *  - surgimento de naves (loop principal do jogo, partes 2 e 3) a cada 256 ciclos;
 *  - IA TACTICS (partes 1-7): mísseis, cápsulas de fuga, estação hostil lançando polícia,
 *    recarga de energia, fuga, disparo de mísseis e laser, manobras e aceleração;
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

    /** EV: atraso para novos piratas/caçadores. */
    private var extraVesselDelay = 0
    private var nextSlot = 0

    fun reset() {
        missileArmed = false
        missileTarget = null
        ecmCycles = 0f
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
        model.addShip(m)
        angry(target)
        model.player.missiles--
        unarmMissile()
        return true
    }

    /** Uma passada de [f] ciclos: trava de míssil, E.C.M. e explosões. */
    fun step(f: Float) {
        if (ecmCycles > 0f) ecmCycles = (ecmCycles - f).coerceAtLeast(0f)
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
        model.ships.removeAll { it.killed && it.cloudCounter >= 256f }
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
            if (ship.type == ShipType.STATION) { angry(ship); continue } // indestrutível
            ship.energy -= power
            if (ship.energy > 0f) angry(ship)
            else kill(ship, byUs = true)
        }
    }

    /**
     * ANGRY: a estação fica hostil; atingir um comerciante também a deixa hostil. Naves com
     * IA passam a agir agressivamente, aceleram (2) e mergulham (contador de arfagem 4).
     */
    fun angry(ship: ShipInstance) {
        when (ship.type) {
            ShipType.STATION -> { ship.hostile = true; ship.aiFlag = ship.aiFlag or 0x80; return }
            ShipType.MISSILE, ShipType.ASTEROID, ShipType.CANISTER, ShipType.ESCAPE_POD -> return
            ShipType.COBRA_TRADER -> model.station?.let { angry(it) }
            else -> Unit
        }
        if (ship.aiFlag == 0) return
        ship.aiFlag = ship.aiFlag or 0x80
        ship.acceleration = 2f
        ship.pitchCounter = 4f
    }

    /**
     * Morte (bit 7 do #31): explosão; recompensa (parte 5); polícia abatida nos torna
     * fugitivos (FIST ≥ 64); 50% de chance de soltar até "max canisters" contêineres.
     */
    fun kill(ship: ShipInstance, byUs: Boolean) {
        if (ship.killed) return
        ship.killed = true
        ship.cloudCounter = 18f
        if (byUs) model.player.kills++
        if (ship.type == ShipType.VIPER) model.player.legalStatus = model.player.legalStatus or 64
        val bounty = ship.blueprint.bounty
        if (bounty > 0) {
            onBounty(bounty)
            messages += "BOUNTY %d.%d CR".format(bounty / 10, bounty % 10)
        }
        if (dornd() < 128) {
            var count = dornd() and ship.blueprint.maxCanisters
            while (count-- > 0) {
                val c = ShipInstance(ShipType.CANISTER, Vector3(ship.position))
                c.setOrientation(ship.nosev, ship.roofv)
                c.speed = ship.speed
                c.rollCounter = (dornd() or 0x60).toSigned()
                model.addShip(c)
            }
        }
        if (missileTarget === ship) unarmMissile()
    }

    // ------------------------------------------------------------ colisões

    /**
     * MA58: uma nave a menos de 64 unidades em cada eixo bateu em nós — ela é destruída e
     * o dano é 128 + energia/2 (no escudo do lado da batida). Mísseis têm checagem própria.
     */
    fun checkCollisions(): Boolean {
        for (ship in model.ships.toList()) {
            if (ship.killed || ship.type == ShipType.STATION || ship.type == ShipType.MISSILE) continue
            val p = ship.position
            if (abs(p.x) >= 64f || abs(p.y) >= 64f || abs(p.z) >= 64f) continue
            val damage = 128f + ship.energy.coerceAtLeast(0f) / 2f
            kill(ship, byUs = false)
            if (model.player.damage(damage, front = p.z >= 0f)) return true
        }
        return false
    }

    // ------------------------------------------------------------ surgimento

    /** Loop principal do jogo, partes 2 e 3 (chamado quando MCNT dá a volta, a cada 256). */
    fun spawnCheck() {
        val ships = model.ships
        val safeZone = model.station != null
        // Parte 2: 13% de chance de algo inofensivo (com menos de 3 asteroides por perto).
        if (dornd() < 35 && ships.count { it.type == ShipType.ASTEROID } < 3) {
            val a = dornd(); val x = dornd()
            val xHi = if (rng.nextBoolean()) 2 else 0
            val pos = Vector3(
                signed(a, xHi * 256f + a), signed(x, (x and 0xFF).toFloat()), 38f * 256f,
            )
            val r = dornd(); val rx = dornd()
            if (rng.nextBoolean()) {
                // MTT4: comerciante (Cobra Mk III), sem IA mas com agressividade latente.
                val t = ShipInstance(ShipType.COBRA_TRADER, pos)
                t.aiFlag = r ushr 1
                t.rollCounter = (r ushr 1).toFloat()
                t.speed = ((r and 31) or 16).toFloat()
                model.addShip(t)
                return
            }
            if (!safeZone) {
                val junk = ShipInstance(if (dornd() >= 5) ShipType.ASTEROID else ShipType.CANISTER, pos)
                junk.rollCounter = (r or 0b01101111).toSigned()
                if (rng.nextBoolean()) junk.speed = ((rx and 31) or 16).toFloat()
                else junk.pitchCounter = (rx or 0x7F).toSigned()
                model.addShip(junk)
                return
            }
        }
        if (safeZone) return

        // Parte 3: polícia, mais provável quanto pior o nosso status (BAD·2, ou FIST com polícia por perto).
        var badness = (contraband() * 2).coerceAtMost(255)
        if (ships.any { it.type == ShipType.VIPER }) badness = badness or model.player.legalStatus
        val (pos, ai, a) = ze()
        if (a < badness) model.addShip(ShipInstance(ShipType.VIPER, pos).also { it.aiFlag = ai })
        if (ships.any { it.type == ShipType.VIPER }) return

        // Piratas ou caçador de recompensas, mais comuns em governos perigosos.
        extraVesselDelay--
        if (extraVesselDelay >= 0) return
        extraVesselDelay = 0
        val r = dornd()
        if (government != 0) {
            if (r >= 90) return
            if ((r and 7) < government) return
        }
        val (pos2, ai2, a2) = ze()
        if (a2 >= 200) {
            // mt1: bando de 1 a 4 piratas (Sidewinders e Mambas).
            val n = a2 and 3
            extraVesselDelay = n
            repeat(n + 1) {
                val type = if (dornd() and 2 == 0) ShipType.SIDEWINDER else ShipType.MAMBA
                model.addShip(ShipInstance(type, Vector3(pos2)).also { it.aiFlag = ai2 })
            }
        } else {
            // Caçador solitário: Mamba, Python ou Cobra (o Thargoid fica para o witchspace).
            extraVesselDelay++
            val type = when ((a2 and 3) + 3) { 3 -> ShipType.MAMBA; 4 -> ShipType.PYTHON; 5 -> ShipType.COBRA_HUNTER; else -> return }
            val x = dornd()
            val ai = (((x shl 1) or (if (x >= 200) 1 else 0)) or 0xC0) and 0xFF
            model.addShip(ShipInstance(type, pos2).also { it.aiFlag = ai })
        }
    }

    /** Ze: nave agressiva em (±8192, ±8192, 8192), IA = %11000000 + E.C.M. em 4%. */
    private fun ze(): Triple<Vector3, Int, Int> {
        val a = dornd(); val x = dornd()
        val pos = Vector3(if (a and 0x80 != 0) -8192f else 8192f, if (x and 0x80 != 0) -8192f else 8192f, 8192f)
        val ai = 0xC0 or (if (x >= 245) 1 else 0)
        return Triple(pos, ai, dornd())
    }

    private fun signed(signSource: Int, magnitude: Float) = if (signSource and 0x80 != 0) -magnitude else magnitude
    private fun Int.toSigned(): Float = (this and 0x7F).toFloat().let { if (this and 0x80 != 0) -it else it }

    // ------------------------------------------------------------ TACTICS

    private val xx15 = Vector3()
    private val k3 = Vector3()

    /** TACTICS para uma nave (a cada 8 ciclos por nave; mísseis a cada ciclo). */
    fun tactics(ship: ShipInstance) {
        if (ship.killed) return
        when (ship.type) {
            ShipType.MISSILE -> { missileTactics(ship); return }
            ShipType.ESCAPE_POD -> {
                // Aponta para o planeta (SPS1) e manobra (TA15).
                val pl = model.planet ?: return
                xx15.set(pl.position).nor()
                steer(ship, xx15, cnt = ship.nosev.dot(xx15))
                return
            }
            ShipType.STATION -> {
                // Estação hostil: 45% de chance de lançar um Viper (até 4 por perto).
                if (dornd() < 140) return
                if (model.ships.count { it.type == ShipType.VIPER } >= 4) return
                spawnChild(ship, ShipType.VIPER, 0b11110001)
                return
            }
            else -> Unit
        }
        // Na zona segura, piratas e caçadores perdem a agressividade.
        if (ship.type.isPirateClass && model.station != null) ship.aiFlag = ship.aiFlag and 0x81

        // Recarrega 1 de energia.
        if (ship.energy < ship.blueprint.maxEnergy) ship.energy += 1f

        // TA21/TA19: XX15 = vetor unitário de nós até a nave; CNT = nosev · XX15.
        xx15.set(ship.position).nor()
        var cnt = ship.nosev.dot(xx15)

        // 2.5%: uma rolagem forte.
        if (dornd() >= 250) ship.rollCounter = (dornd() or 104).toSigned()

        val maxE = ship.blueprint.maxEnergy.toFloat()
        if (ship.energy <= maxE / 2f) {
            if (ship.energy <= maxE / 8f && dornd() >= 230) {
                // Quase sem energia: abandona a nave numa cápsula de fuga (SESCP).
                ship.aiFlag = 0
                spawnChild(ship, ShipType.ESCAPE_POD, 0x80)
                return
            }
            // ta3: dispara um míssil, mais provável quanto mais mísseis tiver.
            if (ship.missiles > 0 && (dornd() and 31) < ship.missiles && !ecmActive) {
                ship.missiles--
                spawnChild(ship, ShipType.MISSILE, 0b11000000, hostileToUs = true)
                messages += "INCOMING MISSILE"
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
     * TA15/TA6/TA9: arfagem 3 para o lado de XX15 (sinal oposto a roofv·XX15), rolagem 5 se
     * não estiver rolando forte, e aceleração 3 se estiver alinhada (CNT ≥ 22/36) ou −1
     * (míssil −2) se estiver bem desalinhada (|CNT| ≥ 18/36).
     */
    private fun steer(ship: ShipInstance, dir: Vector3, cnt: Float) {
        val roofDot = ship.roofv.dot(dir)
        ship.pitchCounter = if (roofDot >= 0f) -3f else 3f
        if (abs(ship.rollCounter) < 16f) {
            val sideDot = ship.sidev.dot(dir)
            val sameSign = (sideDot < 0f) == (ship.pitchCounter < 0f)
            ship.rollCounter = if (sameSign) -5f else 5f
        }
        when {
            cnt >= 22f / 36f -> ship.acceleration = 3f
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
            if (target.type != ShipType.STATION) kill(target, byUs = true)
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
     * byte de IA [ai]. Mísseis inimigos ([hostileToUs]) miram em nós.
     */
    private fun spawnChild(parent: ShipInstance, type: ShipType, ai: Int, hostileToUs: Boolean = false) {
        val child = ShipInstance(type, Vector3(parent.position))
        child.nosev.set(parent.nosev); child.roofv.set(parent.roofv); child.orthonormalize()
        child.aiFlag = ai
        child.speed = if (type == ShipType.MISSILE) parent.speed * 2f else parent.speed
        if (type == ShipType.VIPER) child.speed = type.blueprint.maxSpeed / 2f
        if (hostileToUs) child.aiFlag = child.aiFlag or 0x40
        model.addShip(child)
    }
}
