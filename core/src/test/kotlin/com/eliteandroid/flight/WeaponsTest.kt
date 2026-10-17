package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponsTest {

    @Test
    fun `pulse laser fires five times per second and heats up`() {
        val p = PlayerShip()
        var shots = 0
        // 1 s em passos de 1/60 s (12 ciclos/s → f = 0.2).
        repeat(60) { if (p.updateLaser(0.2f, 1f / 60f, firing = true, view = View.FRONT)) shots++ }
        assertEquals(5, shots)
        // +8 por tiro, −1 por ciclo (12 ciclos): 40 − 12 = 28 (o último tiro ainda não esfriou tudo)
        assertEquals(28f, p.laserTemp, 0.5f)
        assertEquals(250f, p.energy) // DENGY: −1 por tiro
    }

    @Test
    fun `no laser fitted in the rear view`() {
        val p = PlayerShip()
        assertFalse(p.updateLaser(0.2f, 1f / 60f, firing = true, view = View.REAR))
    }

    @Test
    fun `overheated laser stops firing`() {
        val p = PlayerShip()
        var shots = 0
        repeat(60 * 20) { if (p.updateLaser(0.2f, 1f / 60f, firing = true, view = View.FRONT)) shots++ }
        assertTrue(p.laserTemp >= 234f, "temp=${p.laserTemp}")
        // Equilíbrio: aquecimento de 8 por tiro contra 12/s de resfriamento → ~1.5 tiro/s.
        assertTrue(shots < 5 * 20, "shots=$shots")
    }

    @Test
    fun `shooting the station makes it hostile and docking then fails`() {
        val model = FlightModel()
        model.launch()
        val st = model.station!!
        st.position.set(0f, 0f, 700f)
        st.orient(Vector3(0f, 0f, -1f), Vector3(1f, 0f, 0f))
        model.step(1f, FlightControls().apply { fire = true })
        assertTrue(st.hostile)
        assertFalse(FlightModel.isDockingAligned(st))
    }

    @Test
    fun `hyperspace countdown takes 85 cycles`() {
        val c = HyperspaceCountdown()
        assertTrue(c.start())
        assertFalse(c.start()) // não reinicia durante a contagem
        assertEquals(15, c.shown)
        var cycles = 0
        while (!c.update(1f)) cycles++
        assertEquals(84, cycles) // dispara no 85º ciclo
        assertFalse(c.active)
    }
}
