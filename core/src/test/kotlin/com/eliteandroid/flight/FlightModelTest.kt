package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.ships.Blueprints
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.orient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class FlightModelTest {

    private fun fly(model: FlightModel, controls: FlightControls, maxTicks: Int = 400): FlightEvent {
        repeat(maxTicks) {
            val e = model.step(1f, controls)
            if (e != FlightEvent.NONE) return e
        }
        return FlightEvent.NONE
    }

    /** Estação à frente, com a fenda voltada para nós e horizontal (roofv lateral). */
    private fun approach(model: FlightModel, speed: Float): ShipInstance {
        model.launch()
        val st = model.station!!
        st.position.set(0f, 0f, 700f)
        st.orient(Vector3(0f, 0f, -1f), Vector3(1f, 0f, 0f))
        model.player.applyControls(1f, targetSpeed = speed)
        repeat(40) { model.player.applyControls(1f, targetSpeed = speed) }
        return st
    }

    @Test
    fun `launch puts the station just behind with its slot facing us`() {
        val model = FlightModel()
        model.launch()
        val st = model.station!!
        assertEquals(-256f, st.position.z)
        assertEquals(1f, st.nosev.z, 1e-6f)
        assertEquals(12f, model.player.speed)
        // Um ciclo à velocidade 12 afasta a estação 12 unidades.
        model.step(1f, FlightControls())
        assertEquals(-268f, st.position.z, 1e-3f)
    }

    @Test
    fun `matching the station roll docks successfully`() {
        val model = FlightModel()
        approach(model, speed = 20f)
        // A estação gira 1/16 rad por ciclo; ALP1 = 16 (JSTX = 64) com rolagem à esquerda anula o giro.
        val controls = FlightControls().apply { stickX = -64.5f / 127f; targetSpeed = 20f }
        assertEquals(FlightEvent.DOCKED, fly(model, controls))
    }

    @Test
    fun `not matching the roll at speed crashes`() {
        val model = FlightModel()
        approach(model, speed = 20f)
        assertEquals(FlightEvent.CRASHED, fly(model, FlightControls().apply { targetSpeed = 20f }))
    }

    @Test
    fun `slow misaligned contact is only a bump`() {
        val model = FlightModel()
        approach(model, speed = 3f)
        assertEquals(FlightEvent.BUMPED, fly(model, FlightControls().apply { targetSpeed = 3f }, maxTicks = 1000))
        assertEquals(1f, model.player.speed)
        assertTrue(model.player.foreShield < 255f)
    }

    @Test
    fun `docking from the side is a crash`() {
        val model = FlightModel()
        model.launch()
        val st = model.station!!
        st.position.set(0f, 0f, 700f)
        st.orient(Vector3(1f, 0f, 0f)) // fenda virada para o lado
        st.rollCounter = 0f
        assertEquals(FlightEvent.CRASHED, fly(model, FlightControls().apply { targetSpeed = 20f }))
    }

    @Test
    fun `rolling right makes objects on the right rise`() {
        val model = FlightModel()
        model.launch()
        val st = model.station!!
        st.position.set(500f, 0f, 1000f)
        model.step(1f, FlightControls().apply { stickX = 1f })
        assertTrue(st.position.y > 0f, "y=${st.position.y}")
    }

    @Test
    fun `pulling up makes objects ahead sink`() {
        val model = FlightModel()
        model.launch()
        val st = model.station!!
        st.position.set(0f, 0f, 1000f)
        repeat(5) { model.step(1f, FlightControls().apply { stickY = 1f }) }
        assertTrue(st.position.y < 0f, "y=${st.position.y}")
    }

    @Test
    fun `alpha and beta follow the original conversion`() {
        assertEquals(3f, PlayerShip.alp1(31f))
        assertEquals(8f, PlayerShip.alp1(32f))
        assertEquals(16f, PlayerShip.alp1(64f))
        assertEquals(31f, PlayerShip.alp1(127f))
        assertEquals(1f, PlayerShip.bet1(40f))
        assertEquals(3f, PlayerShip.bet1(48f))
        assertEquals(8f, PlayerShip.bet1(127f))
    }

    @Test
    fun `keyboard roll accumulates and damps back to centre`() {
        val p = PlayerShip()
        repeat(3) { p.applyControls(1f, rollKey = 1) }
        assertEquals(21f, p.jstx)
        p.applyControls(1f)
        assertEquals(19f, p.jstx) // cntr: −2 por ciclo
        p.applyControls(1f, rollKey = -1)
        assertEquals(0f, p.jstx) // auto-recentre ao inverter
    }

    @ParameterizedTest
    @EnumSource(View::class)
    fun `view transform round-trips`(view: View) {
        val a = Vector3(); val b = Vector3()
        view.toView(1f, 2f, 3f, a)
        view.fromView(a.x, a.y, a.z, b)
        assertEquals(Vector3(1f, 2f, 3f), b)
    }

    @Test
    fun `left view looks along minus x`() {
        val v = Vector3()
        View.LEFT.toView(-100f, 0f, 0f, v)
        assertEquals(100f, v.z)
        View.RIGHT.toView(100f, 0f, 0f, v)
        assertEquals(100f, v.z)
    }
}
