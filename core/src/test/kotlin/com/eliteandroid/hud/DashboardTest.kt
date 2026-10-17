package com.eliteandroid.hud

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.flight.PlayerShip
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardTest {

    private val blip = Scanner.Blip()

    @Test
    fun `ship at our position sits on the scanner centre`() {
        assertTrue(Scanner.project(Vector3(0f, 0f, 0f), blip))
        assertEquals(123f, blip.x); assertEquals(220f, blip.baseY); assertEquals(220f, blip.dotY)
    }

    @Test
    fun `scanner mapping follows SCAN`() {
        // x_hi = +10, z_hi = +40 (à frente: base sobe 10), y_hi = +20 (acima: ponto sobe 10)
        assertTrue(Scanner.project(Vector3(10f * 256f, 20f * 256f, 40f * 256f), blip))
        assertEquals(133f, blip.x)
        assertEquals(210f, blip.baseY)
        assertEquals(200f, blip.dotY)
        // Atrás e abaixo.
        assertTrue(Scanner.project(Vector3(-5f * 256f, -63f * 256f, -63f * 256f), blip))
        assertEquals(118f, blip.x)
        assertEquals(235f, blip.baseY)
        assertEquals(246f, blip.dotY) // limitado a 246
    }

    @Test
    fun `ships beyond 63 high bytes are off the scanner`() {
        assertFalse(Scanner.project(Vector3(64f * 256f, 0f, 0f), blip))
        assertFalse(Scanner.project(Vector3(0f, 0f, -64f * 256f), blip))
        assertTrue(Scanner.project(Vector3(0f, 0f, 63.9f * 256f), blip))
    }

    @Test
    fun `energy banks fill from the bottom and the top one maxes at 15`() {
        assertArrayEquals(intArrayOf(15, 16, 16, 16), Dashboard.energyBanks(255f))
        assertArrayEquals(intArrayOf(0, 0, 16, 16), Dashboard.energyBanks(128f))
        assertArrayEquals(intArrayOf(0, 0, 0, 10), Dashboard.energyBanks(40f))
        assertArrayEquals(intArrayOf(0, 0, 0, 0), Dashboard.energyBanks(0f))
    }

    @Test
    fun `roll and pitch indicators follow DIALS`() {
        val p = PlayerShip()
        p.applyControls(1f)
        assertEquals(8, Dashboard.rollIndicator(p))
        assertEquals(8, Dashboard.pitchIndicator(p))
        p.applyControls(1f, stickX = 1f, stickY = 1f) // ALP1 = 31, BET1 = 8
        assertEquals(15, Dashboard.rollIndicator(p)) // rolar à direita → α < 0 → 8 + 31/4
        assertEquals(15, Dashboard.pitchIndicator(p))
        p.applyControls(1f, stickX = -1f, stickY = -1f)
        assertEquals(1, Dashboard.rollIndicator(p))
        assertEquals(1, Dashboard.pitchIndicator(p))
    }

    @Test
    fun `shields recharge every 8 cycles when energy is above half`() {
        val p = PlayerShip()
        p.foreShield = 100f; p.aftShield = 100f; p.energy = 200f
        repeat(16) { p.updateSystems(1f) }
        assertEquals(102f, p.foreShield)
        assertEquals(102f, p.aftShield)
        assertEquals(202f, p.energy)
        p.energy = 100f // abaixo de 50%: só a energia recarrega
        repeat(8) { p.updateSystems(1f) }
        assertEquals(102f, p.foreShield)
        assertEquals(101f, p.energy)
    }
}
