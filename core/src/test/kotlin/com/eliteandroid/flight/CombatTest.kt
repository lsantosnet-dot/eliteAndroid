package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.ships.OriginalShips
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import com.eliteandroid.ships.orient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CombatTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)

    /** Modelo em pleno espaço (sem estação = fora da zona segura), num sistema anárquico. */
    private fun deepSpace(): FlightModel {
        val m = FlightModel()
        m.arrive(galaxy.findByName("RIEDQUAT")!!) // anarquia
        m.combat.rng = Random(1234)
        return m
    }

    private fun spawn(m: FlightModel, type: ShipType, pos: Vector3, ai: Int = 0): ShipInstance =
        ShipInstance(type, pos).also { it.aiFlag = ai; m.addShip(it) }

    @Test
    fun `blueprints extracted from the original source`() {
        assertEquals(10, OriginalShips.SIDEWINDER.vertexCount); assertEquals(15, OriginalShips.SIDEWINDER.edgeCount)
        assertEquals(15, OriginalShips.VIPER.vertexCount); assertEquals(20, OriginalShips.VIPER.edgeCount)
        assertEquals(25, OriginalShips.MAMBA.vertexCount); assertEquals(28, OriginalShips.MAMBA.edgeCount)
        assertEquals(11, OriginalShips.PYTHON.vertexCount); assertEquals(26, OriginalShips.PYTHON.edgeCount)
        assertEquals(17, OriginalShips.MISSILE.vertexCount); assertEquals(24, OriginalShips.MISSILE.edgeCount)
        assertEquals(70, OriginalShips.SIDEWINDER.maxEnergy); assertEquals(37, OriginalShips.SIDEWINDER.maxSpeed)
        assertEquals(50, OriginalShips.SIDEWINDER.bounty); assertEquals(2, OriginalShips.SIDEWINDER.laserPower)
        assertEquals(200, OriginalShips.PYTHON.bounty); assertEquals(3, OriginalShips.PYTHON.missiles)
        ShipType.entries.forEach { assertTrue(it.blueprint.faceCount > 0, it.name) }
    }

    @Test
    fun `an aggressive pirate turns to face us`() {
        val m = deepSpace()
        val pirate = spawn(m, ShipType.SIDEWINDER, Vector3(0f, 0f, 6000f), ai = 0xFE).orient(Vector3(1f, 0f, 0f))
        repeat(12 * 20) { m.step(1f, FlightControls().apply { targetSpeed = 1f }) }
        val toUs = Vector3(pirate.position).scl(-1f).nor()
        assertTrue(pirate.nosev.dot(toUs) > 0.9f, "nosev·toUs = ${pirate.nosev.dot(toUs)}")
    }

    @Test
    fun `a ship facing us in range hits us with its laser`() {
        val m = deepSpace()
        val pirate = spawn(m, ShipType.MAMBA, Vector3(0f, 0f, 2000f), ai = 0xFE) // nosev (0,0,−1)
        m.combat.tactics(pirate)
        assertTrue(pirate.firingLaser)
        assertEquals(255f - OriginalShips.MAMBA.laserByte / 2, m.player.foreShield)
    }

    @Test
    fun `our laser kills a sidewinder for its bounty`() {
        val m = deepSpace()
        var earned = 0
        m.combat.onBounty = { earned += it }
        val s = spawn(m, ShipType.SIDEWINDER, Vector3(0f, 0f, 1500f))
        repeat(5) { m.combat.laserShot(15) } // 70 de energia, laser de pulso 15
        assertTrue(s.killed)
        assertEquals(50, earned)
        assertEquals(1, m.player.kills)
        repeat(12 * 10) { m.step(1f, FlightControls()) } // a nuvem da explosão termina
        assertFalse(s in m.ships)
    }

    @Test
    fun `our missile locks, flies and destroys its target`() {
        val m = deepSpace()
        val target = spawn(m, ShipType.PYTHON, Vector3(0f, 0f, 3000f))
        target.speed = 0f
        m.combat.armMissile()
        m.step(1f, FlightControls())
        assertEquals(target, m.combat.missileTarget)
        assertTrue(m.combat.fireMissile())
        assertEquals(2, m.player.missiles)
        var ticks = 0
        while (!target.killed && ticks++ < 600) m.step(1f, FlightControls().apply { targetSpeed = 1f })
        assertTrue(target.killed, "alvo sobreviveu")
    }

    @Test
    fun `an incoming missile hits us for 250`() {
        val m = deepSpace()
        val missile = spawn(m, ShipType.MISSILE, Vector3(0f, 0f, 1500f), ai = 0b11000000)
        missile.speed = 30f
        var ticks = 0
        while (missile in m.ships && !missile.killed && ticks++ < 300) m.step(1f, FlightControls().apply { targetSpeed = 1f })
        assertTrue(missile.killed)
        assertEquals(5f, m.player.foreShield, 1f)
    }

    @Test
    fun `ramming a ship damages us and destroys it`() {
        val m = deepSpace()
        val rock = spawn(m, ShipType.ASTEROID, Vector3(0f, 0f, 40f))
        m.step(1f, FlightControls())
        assertTrue(rock.killed)
        assertTrue(m.player.foreShield < 255f - 128f)
    }

    @Test
    fun `shooting the police makes us a fugitive and the station hostile spawns vipers`() {
        val m = FlightModel()
        m.launch(galaxy[7])
        m.combat.rng = Random(7)
        val cop = spawn(m, ShipType.VIPER, Vector3(0f, 0f, 1500f))
        repeat(20) { m.combat.laserShot(15) }
        assertTrue(cop.killed)
        assertTrue(m.player.legalStatus >= 64)
        val st = m.station!!
        m.combat.angry(st)
        assertTrue(st.hostile && st.hasAi)
        repeat(20) { m.combat.tactics(st) }
        assertTrue(m.ships.any { it.type == ShipType.VIPER && !it.killed })
    }

    @Test
    fun `anarchies spawn pirates and safe zones do not`() {
        val anarchy = deepSpace()
        repeat(60) { anarchy.combat.spawnCheck() }
        assertTrue(anarchy.ships.any { it.type.isPirateClass }, "nenhum pirata em 60 tentativas")

        val docked = FlightModel()
        docked.launch(galaxy.findByName("RIEDQUAT")!!) // estação por perto: zona segura
        docked.combat.rng = Random(1234)
        repeat(60) { docked.combat.spawnCheck() }
        assertFalse(docked.ships.any { it.type.isPirateClass || it.type == ShipType.VIPER })
    }

    @Test
    fun `pirates block the in-system jump`() {
        val m = deepSpace()
        m.planet!!.position.set(0f, 0f, 5f * 65536f)
        spawn(m, ShipType.MAMBA, Vector3(3000f, 0f, 3000f), ai = 0xC0)
        assertFalse(m.inSystemJump())
        assertNotNull(m.ships.firstOrNull())
    }
}
