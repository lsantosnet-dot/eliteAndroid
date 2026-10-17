package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.GameSession
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EquipmentFlightTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)
    private val idle = FlightControls()

    private fun deepSpace(): FlightModel = FlightModel().also {
        it.arrive(galaxy.findByName("RIEDQUAT")!!)
        it.combat.rng = Random(99)
    }

    private fun spawn(m: FlightModel, type: ShipType, pos: Vector3, ai: Int = 0) =
        ShipInstance(type, pos).also { it.aiFlag = ai; m.addShip(it) }

    @Test
    fun `fuel scoops pick up a canister passing below the ship`() {
        val m = deepSpace()
        val got = ArrayList<Int>()
        m.combat.onScoop = { got += it; true }
        m.player.fuelScoops = true
        val can = spawn(m, ShipType.CANISTER, Vector3(0f, -20f, 30f))
        val shields = m.player.foreShield
        assertFalse(m.combat.checkCollisions())
        assertEquals(1, got.size)
        assertTrue(got[0] in 0..7)
        assertFalse(can in m.ships)
        assertEquals(shields, m.player.foreShield)
    }

    @Test
    fun `escape pods are scooped as slaves`() {
        val m = deepSpace()
        val got = ArrayList<Int>()
        m.combat.onScoop = { got += it; true }
        m.player.fuelScoops = true
        spawn(m, ShipType.ESCAPE_POD, Vector3(0f, -20f, 30f))
        m.combat.checkCollisions()
        assertEquals(listOf(3), got)
    }

    @Test
    fun `without scoops, or above us, a canister is a collision`() {
        val m = deepSpace()
        m.combat.onScoop = { true }
        m.player.fuelScoops = true
        spawn(m, ShipType.CANISTER, Vector3(0f, 20f, 30f)) // acima: bate
        m.combat.checkCollisions()
        assertTrue(m.player.foreShield < 255f)
    }

    @Test
    fun `a full hold destroys the canister harmlessly`() {
        val m = deepSpace()
        m.combat.onScoop = { false }
        m.player.fuelScoops = true
        val can = spawn(m, ShipType.CANISTER, Vector3(0f, -20f, 30f))
        m.combat.checkCollisions()
        assertTrue(can.killed)
        assertEquals(255f, m.player.foreShield)
    }

    @Test
    fun `the sun heats the cabin, scoops fuel and can burn us`() {
        val m = deepSpace()
        val sun = assertNotNull(m.sun).let { m.sun!! }
        m.player.fuelScoops = true
        m.player.fuel = 10f
        m.player.applyControls(0f, targetSpeed = 40f)
        repeat(100) { m.player.applyControls(1f, targetSpeed = 40f) }
        // Perto do sol: A = 3·(120²/256) = 168 → CABTMP = 255 − 168 + 30 = 117.
        sun.position.set(120f * 256f, 120f * 256f, -120f * 256f)
        runTicks(m, 32)
        assertEquals(117f, m.player.cabinTemp)
        assertEquals(10f, m.player.fuel)
        // Bem perto: A = 3·(60²/256) = 42 → 243 ≥ 224, coleta 40/8 = 5 décimos.
        sun.position.set(60f * 256f, 60f * 256f, -60f * 256f)
        runTicks(m, 32)
        assertEquals(243f, m.player.cabinTemp)
        assertEquals(15f, m.player.fuel)
        // Dentro da coroa: A < 30 → morte.
        sun.position.set(20f * 256f, 20f * 256f, -20f * 256f)
        var crashed = false
        repeat(32 * 5) { if (m.step(1f, FlightControls().also { c -> c.targetSpeed = 1f }) == FlightEvent.CRASHED) crashed = true }
        assertTrue(crashed)
    }

    /** Avança [n] ciclos sem mover o jogador (o sol continua onde está). */
    private fun runTicks(m: FlightModel, n: Int) {
        val pos = Vector3(m.sun!!.position)
        repeat(n) {
            m.step(1f, idle)
            m.sun!!.position.set(pos)
            m.planet!!.position.set(0f, 0f, 1e9f)
        }
    }

    @Test
    fun `energy unit doubles the recharge`() {
        val a = PlayerShip(); val b = PlayerShip().also { it.energyUnit = true }
        a.energy = 100f; b.energy = 100f
        a.updateSystems(64f); b.updateSystems(64f)
        assertEquals(108f, a.energy)
        assertEquals(116f, b.energy)
    }

    @Test
    fun `our ECM destroys missiles and drains energy`() {
        val m = deepSpace()
        assertFalse(m.combat.fireEcm()) // não instalado
        m.player.hasEcm = true
        val missile = spawn(m, ShipType.MISSILE, Vector3(0f, 0f, 3000f), ai = 0xC0)
        assertTrue(m.combat.fireEcm())
        assertFalse(m.combat.fireEcm()) // já em andamento
        val e = m.player.energy
        m.combat.tactics(missile)
        assertTrue(missile.killed)
        m.combat.step(1f)
        assertTrue(m.player.energy < e)
        repeat(40) { m.combat.step(1f) }
        assertFalse(m.combat.ecmActive)
    }

    @Test
    fun `energy bomb kills every ship but the station, once`() {
        val m = FlightModel(); m.launch(galaxy[7])
        val a = spawn(m, ShipType.SIDEWINDER, Vector3(0f, 0f, 3000f))
        val b = spawn(m, ShipType.ASTEROID, Vector3(500f, 0f, 3000f))
        assertFalse(m.combat.detonateBomb())
        m.player.energyBomb = true
        assertTrue(m.combat.detonateBomb())
        assertTrue(a.killed && b.killed)
        assertFalse(m.station!!.killed)
        assertFalse(m.player.energyBomb)
        assertEquals(2, m.player.kills)
    }

    @Test
    fun `docking computer needs the safe zone and a friendly station`() {
        val m = FlightModel(); m.launch(galaxy[7])
        assertFalse(m.canUseDockingComputer())
        m.player.dockingComputer = true
        assertTrue(m.canUseDockingComputer())
        m.station!!.hostile = true
        assertFalse(m.canUseDockingComputer())
        assertFalse(deepSpace().also { it.player.dockingComputer = true }.canUseDockingComputer())
    }

    @Test
    fun `galactic hyperdrive goes to the next galaxy, nearest to (96, 96)`() {
        val s = GameSession(galaxy, galaxy[7])
        s.launchNow()
        s.model.player.legalStatus = 40
        s.requestGalacticHyperspace()
        assertFalse(s.model.hyperspace.active) // não instalado
        s.model.player.galacticHyperdrive = true
        s.requestGalacticHyperspace()
        assertTrue(s.model.hyperspace.active)
        assertFalse(s.model.player.galacticHyperdrive)
        assertEquals(0, s.model.player.legalStatus)
        s.arriveNow(galaxy[7])
        assertEquals(2, s.galaxy.number)
        val g2 = GalaxyGenerator.buildGalaxy(2)
        assertEquals(g2.nearestTo(96, 96).name, s.currentSystem.name)
        assertEquals(70f, s.model.player.fuel) // distância 0
    }

    @Test
    fun `escape pod saves the commander but not the cargo`() {
        val s = GameSession(galaxy, galaxy[7])
        s.buy(0, 5)
        s.launchNow()
        s.model.player.escapePod = true
        s.model.player.legalStatus = 70
        s.model.player.fuel = 20f
        s.escapePod()
        assertEquals(GameSession.State.DOCKING, s.state)
        assertEquals(0, s.commander.cargo[0])
        assertEquals(0, s.model.player.legalStatus)
        assertEquals(70f, s.model.player.fuel)
        assertFalse(s.model.player.escapePod)
    }
}
