package com.eliteandroid.flight

import com.eliteandroid.galaxy.GalaxyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrivalTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)
    private val diso = galaxy.findByName("DISO")!!

    @Test
    fun `launch puts the planet 65536 ahead with no sun`() {
        val m = FlightModel()
        m.launch(galaxy[7])
        assertEquals(65536f, m.planet!!.position.z)
        assertNull(m.sun)
        assertNotNull(m.station)
    }

    @Test
    fun `arrival places planet ahead and sun behind from the system seed`() {
        val m = FlightModel()
        m.arrive(diso)
        val seed = diso.seed
        val zSign = ((seed.s0Hi and 7) + 6) ushr 1
        assertEquals(zSign * 65536f, m.planet!!.position.z)
        assertTrue(m.sun!!.position.z < 0f)
        assertNull(m.station)
        assertEquals(m.planet, m.compassTarget)
    }

    @Test
    fun `in-system jump brings the planet 65536 closer until too near`() {
        val m = FlightModel()
        m.arrive(diso)
        // Planeta bem à frente, centralizado (se estiver deslocado para o lado em 2+ unidades,
        // o original continua deixando saltar mesmo depois de passar por ele).
        m.planet!!.position.set(0f, 0f, 5f * 65536f)
        assertTrue(m.inSystemJump())
        assertEquals(4f * 65536f, m.planet!!.position.z)
        var jumps = 1
        while (jumps < 20 && m.inSystemJump()) jumps++
        assertEquals(4, jumps) // 5 → 4 → 3 → 2 → 1: com z_sign 1 já está perto demais
        assertEquals(65536f, m.planet!!.position.z)
        assertFalse(m.inSystemJump())
    }

    @Test
    fun `station appears when flying close to the planet`() {
        val m = FlightModel()
        m.arrive(diso)
        // Coloca o planeta logo à frente (a ~1.6 raios) e espera o ciclo de checagem (a cada 32).
        m.planet!!.position.set(0f, 0f, 40000f)
        var appeared = false
        repeat(64) { if (m.step(1f, FlightControls().apply { targetSpeed = 1f }) == FlightEvent.STATION_APPEARED) appeared = true }
        assertTrue(appeared)
        val st = m.station!!
        // A 2 raios do centro do planeta, com a fenda voltada para ele.
        val d = st.position.dst(m.planet!!.position)
        assertEquals(2f * Planet.RADIUS, d, 400f)
        assertNull(m.sun)
        assertEquals(st, m.compassTarget)
    }

    @Test
    fun `flying into the planet is fatal`() {
        val m = FlightModel()
        m.arrive(diso)
        m.planet!!.position.set(0f, 0f, Planet.RADIUS - 100f)
        var crashed = false
        repeat(40) { if (m.step(1f, FlightControls()) == FlightEvent.CRASHED) crashed = true }
        assertTrue(crashed)
    }
}
