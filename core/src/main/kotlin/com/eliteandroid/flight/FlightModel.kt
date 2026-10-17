package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.Stardust
import com.eliteandroid.ships.Blueprints
import com.eliteandroid.ships.ShipInstance
import kotlin.math.abs

/** Controles lidos a cada passo (teclado, toque ou joystick). */
class FlightControls {
    var rollKey = 0
    var pitchKey = 0
    var stickX: Float? = null
    var stickY: Float? = null
    var accelerate = false
    var decelerate = false
    var targetSpeed: Float? = null
}

/** Resultado de um passo de simulação. */
enum class FlightEvent { NONE, DOCKED, BUMPED, CRASHED }

/**
 * A "bolha local" do universo e a física de voo do original:
 *
 *  - [PlayerShip.applyControls]: loop principal partes 2 e 3 (α, β, DELTA);
 *  - para cada nave: MVEIT (movimento próprio, rotação do universo pela rolagem/arfagem
 *    do jogador e avanço do jogador);
 *  - ISDK: checagem de atracação na estação.
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
    val dust = Stardust()

    var view = View.FRONT
        set(value) {
            if (field != value) { field = value; dust.reset(value) } // LOOK1 → NWSTARS
        }

    private var accumulator = 0f

    /** TT110: sai da estação — ela fica logo atrás (z = −256) com a fenda voltada para nós. */
    fun launch() {
        ships.clear()
        val st = ShipInstance(Blueprints.CORIOLIS, Vector3(0f, 0f, -256f), NeonPalette.STATION)
            .orient(Vector3(0f, 0f, 1f)) // ZINF + NwS1 (nosev invertido) + TIDY
        st.rollCounter = -127f // NWSPS: contador 255 = rolagem anti-horária máxima, sem amortecimento
        ships += st
        station = st
        player.reset()
        view = View.FRONT
        dust.reset(view)
    }

    /** Avança o tempo real [delta] (s). Retorna o primeiro evento relevante ocorrido. */
    fun update(delta: Float, controls: FlightControls): FlightEvent {
        accumulator += delta.coerceAtMost(0.25f)
        val stepDt = 1f / STEP_RATE
        var result = FlightEvent.NONE
        while (accumulator >= stepDt) {
            accumulator -= stepDt
            val e = step(TICK_RATE / STEP_RATE, controls)
            if (e == FlightEvent.DOCKED || e == FlightEvent.CRASHED) { accumulator = 0f; return e }
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
        val alpha = player.alpha * f
        val beta = player.beta * f
        val distance = player.speed * f

        for (ship in ships) {
            ship.moveSelf(f)
            ship.applyPlayerMotion(alpha, beta, distance)
        }
        dust.update(alpha, beta, distance, view)

        return checkDocking(f)
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
