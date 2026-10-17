package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.GameSession
import com.eliteandroid.economy.Commodities
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.ships.DiscShips
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class WitchspaceTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)
    private val lave = galaxy[7]
    private val diso = galaxy.findByName("DISO")!!

    private fun misjump(): GameSession = GameSession(galaxy, lave).also {
        it.launchNow()
        it.model.combat.rng = Random(7)
        it.forceMisjump = true
        it.arriveNow(diso)
    }

    @Test
    fun `thargoid blueprints from the original source`() {
        assertEquals(20, DiscShips.THARGOID.vertexCount); assertEquals(26, DiscShips.THARGOID.edgeCount)
        assertEquals(240, DiscShips.THARGOID.maxEnergy); assertEquals(500, DiscShips.THARGOID.bounty)
        assertEquals(6, DiscShips.THARGOID.missiles) // Thargons a bordo
        assertEquals(10, DiscShips.THARGON.vertexCount); assertEquals(15, DiscShips.THARGON.edgeCount) // arestas do contêiner
        assertEquals(50, DiscShips.THARGON.bounty)
    }

    @Test
    fun `a mis-jump lands between the stars with four thargoids`() {
        val s = misjump()
        assertTrue(s.inWitchspace)
        assertEquals(GameSession.State.FLYING, s.state)
        val m = s.model
        assertTrue(m.witchspace)
        assertNull(m.planet); assertNull(m.sun); assertNull(m.station)
        assertEquals(4, m.ships.count { it.type == ShipType.THARGOID })
        assertEquals(4, m.ships.count { it.type == ShipType.THARGON })
        assertTrue(m.ships.all { it.aiFlag == 0xFF })
        assertEquals(3, m.dust.active)
        // Combustível da distância gasto; ficamos em (x de Lave, y de Lave com os bits 0..5 invertidos).
        assertEquals(70f - GalaxyGenerator.distance(lave, diso), m.player.fuel)
        assertEquals(lave.x, s.positionX)
        assertEquals(lave.y xor 31, s.positionY)
        assertEquals("LAVE", s.currentSystem.name)
        assertEquals("DISO", s.hyperspaceTarget?.name) // o destino continua escolhido
        assertFalse(m.inSystemJump())
    }

    @Test
    fun `no bounties and no new ships in witchspace`() {
        val s = misjump()
        val cash = s.commander.cash
        val t = s.model.ships.first { it.type == ShipType.THARGOID }
        s.model.combat.kill(t, byUs = true)
        assertEquals(cash, s.commander.cash)
        assertEquals(1, s.model.player.kills)
        val before = s.model.ships.size
        repeat(50) { s.model.combat.spawnCheck() }
        assertEquals(before, s.model.ships.size)
    }

    @Test
    fun `escaping from witchspace is a normal hyperspace jump from where we are`() {
        val s = misjump()
        s.arriveNow(diso)
        assertFalse(s.inWitchspace)
        assertFalse(s.model.witchspace)
        assertEquals("DISO", s.currentSystem.name)
        assertEquals(18, s.model.dust.active)
    }

    @Test
    fun `thargoids launch thargons instead of missiles`() {
        val m = FlightModel(); m.enterWitchspace()
        m.combat.rng = Random(3)
        m.ships.clear()
        val t = ShipInstance(ShipType.THARGOID, Vector3(4000f, 0f, 9000f)).also { it.aiFlag = 0xFF; m.addShip(it) }
        t.energy = 10f
        repeat(400) { if (!t.killed) m.combat.tactics(t); t.energy = 10f }
        assertTrue(m.ships.any { it.type == ShipType.THARGON })
        assertTrue(m.ships.none { it.type == ShipType.MISSILE || it.type == ShipType.ESCAPE_POD })
        assertTrue(m.combat.messages.none { it == "INCOMING MISSILE" })
    }

    @Test
    fun `thargons without a mothership drift`() {
        val m = FlightModel(); m.enterWitchspace()
        m.ships.clear()
        val g = ShipInstance(ShipType.THARGON, Vector3(0f, 0f, 3000f)).also { it.aiFlag = 0xFF; it.speed = 20f; m.addShip(it) }
        m.combat.tactics(g)
        assertEquals(10f, g.speed)
        assertFalse(g.hasEcm)
    }

    @Test
    fun `thargons scoop as alien items`() {
        val m = FlightModel(); m.enterWitchspace()
        m.ships.clear()
        val got = ArrayList<Int>()
        m.combat.onScoop = { got += it; true }
        m.player.fuelScoops = true
        m.addShip(ShipInstance(ShipType.THARGON, Vector3(0f, -20f, 30f)))
        m.combat.checkCollisions()
        assertEquals(listOf(Commodities.ALIEN_ITEMS), got)
    }

    @Test
    fun `the escape pod is fatal in witchspace`() {
        val s = misjump()
        s.model.player.escapePod = true
        s.ejectPod()
        assertEquals(GameSession.State.DESTROYED, s.state)
    }
}
