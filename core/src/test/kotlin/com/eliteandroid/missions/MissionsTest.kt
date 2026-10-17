package com.eliteandroid.missions

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.GameSession
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.save.MemoryStorage
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MissionsTest {

    private val g1 = GalaxyGenerator.buildGalaxy(1)
    private val g2 = GalaxyGenerator.buildGalaxy(2)
    private val g3 = GalaxyGenerator.buildGalaxy(3)

    @Test
    fun `mission systems from the original coordinates`() {
        assertEquals("XEER", g1[150].name)
        assertEquals("REESDICE", g1[36].name)
        assertEquals("ERRIUS", g2[253].name)
        val constrictor = g2.systems.single { Missions.isConstrictorSystem(it) }
        assertEquals(144, constrictor.x); assertEquals(33, constrictor.y)
        assertEquals("CEERDI", g3.systems.single { it.x == 215 && it.y == 84 }.name)
        assertEquals("BIRERA", g3.systems.single { it.x == 63 && it.y == 72 }.name)
    }

    @Test
    fun `mission 1 starts at 256 kills in galaxies 1 and 2 only`() {
        assertNull(Missions.onDocked(0, 255, 1, g1[7]).briefing)
        assertNull(Missions.onDocked(0, 300, 3, g3[7]).briefing)
        val e = Missions.onDocked(0, 256, 1, g1[7])
        assertEquals(Missions.Kind.BRIEF, e.briefing!!.kind)
        assertEquals(1, e.tp)
        assertTrue(e.briefing!!.pages.joinToString().contains("Captain Curruthers"))
        assertTrue(e.briefing!!.pages.joinToString().contains("last seen at Reesdice"))
        assertTrue(Missions.onDocked(0, 256, 2, g2[7]).briefing!!.pages.joinToString().contains("Fosdyke Smythe"))
        // Em andamento, nada acontece até o Constrictor ser destruído.
        assertNull(Missions.onDocked(1, 400, 2, g2[7]).briefing)
        val done = Missions.onDocked(0b11, 400, 2, g2[7])
        assertEquals(Missions.Kind.DEBRIEF, done.briefing!!.kind)
        assertEquals(0b10, done.tp)
        assertEquals(50_000L, done.rewardCash)
        assertEquals(256, done.rewardKills)
    }

    @Test
    fun `mission 2 from galaxy 3 via Ceerdi to Birera`() {
        val ceerdi = g3.systems.single { it.x == 215 && it.y == 84 }
        val birera = g3.systems.single { it.x == 63 && it.y == 72 }
        assertNull(Missions.onDocked(0b10, 1279, 3, g3[0]).briefing)
        val start = Missions.onDocked(0b10, 1280, 3, g3[0])
        assertEquals(Missions.Kind.BRIEF2, start.briefing!!.kind)
        assertEquals(0b0110, start.tp)
        assertNull(Missions.onDocked(0b0110, 1280, 3, birera).briefing) // ainda sem os planos
        val plans = Missions.onDocked(0b0110, 1280, 3, ceerdi)
        assertEquals(Missions.Kind.BRIEF3, plans.briefing!!.kind)
        assertEquals(0b1010, plans.tp)
        assertTrue(Missions.carryingPlans(plans.tp))
        val end = Missions.onDocked(0b1010, 1280, 3, birera)
        assertEquals(Missions.Kind.DEBRIEF2, end.briefing!!.kind)
        assertEquals(0b1110, end.tp)
        assertTrue(end.navyEnergyUnit)
        assertNull(Missions.onDocked(0b1110, 5000, 3, birera).briefing)
    }

    @Test
    fun `hints replace the planet description while mission 1 is on`() {
        val xeer = g1[150]
        assertNull(Missions.systemHint(xeer, 0))
        assertEquals("The Constrictor was last seen at Reesdice, Commander.", Missions.systemHint(xeer, 1))
        assertTrue(Missions.systemHint(g2[32], 1, Random(1))!!.contains("Errius")) // Bebege
        assertNotNull(Missions.systemHint(g1[211], 0)) // Teorge: sempre
        assertNull(Missions.systemHint(g1[7], 1)) // Lave: descrição normal
    }

    @Test
    fun `the constrictor system loads ship file G and spawns the Constrictor`() {
        val here = g2.systems.single { Missions.isConstrictorSystem(it) }
        val m = FlightModel()
        m.missionFlags = Missions.CONSTRICTOR_IN_PROGRESS
        m.combat.rng = Random(2)
        m.arrive(here)
        assertEquals(6, m.shipFileIndex)
        assertTrue(m.constrictorSystem)
        var tries = 0
        while (m.ships.none { it.type == ShipType.CONSTRICTOR } && tries++ < 2000) m.combat.spawnCheck()
        val con = m.ships.first { it.type == ShipType.CONSTRICTOR }
        assertEquals(0b11111001, con.aiFlag)
        // Só um por vez.
        repeat(500) { m.combat.spawnCheck() }
        assertEquals(1, m.ships.count { it.type == ShipType.CONSTRICTOR })
    }

    @Test
    fun `only military lasers hurt the Constrictor, and killing it completes mission 1`() {
        val m = FlightModel()
        m.missionFlags = Missions.CONSTRICTOR_IN_PROGRESS
        val con = ShipInstance(ShipType.CONSTRICTOR, Vector3(0f, 0f, 1500f)).also { m.addShip(it) }
        val energy = con.energy
        repeat(50) { m.combat.laserShot(PlayerShip.BEAM_LASER) }
        assertEquals(energy, con.energy)
        m.combat.laserShot(PlayerShip.MILITARY_LASER)
        assertEquals(energy - 23 / 4, con.energy)
        repeat(100) { if (!con.killed) m.combat.laserShot(PlayerShip.MILITARY_LASER) }
        assertTrue(con.killed)
        assertEquals(0b11, m.missionFlags)
    }

    @Test
    fun `mining lasers split asteroids`() {
        var splinters = 0
        for (seed in 1..20) {
            val m = FlightModel(); m.combat.rng = Random(seed)
            m.selectShipFile(2) // C: asteroides e fragmentos
            val rock = ShipInstance(ShipType.ASTEROID, Vector3(0f, 0f, 1500f)).also { m.addShip(it) }
            rock.energy = 1f
            m.combat.laserShot(PlayerShip.MINING_LASER)
            assertTrue(rock.killed)
            splinters += m.ships.count { it.type == ShipType.SPLINTER }
        }
        assertTrue(splinters > 0)
    }

    @Test
    fun `carrying the plans brings Thargoids and the Thargoid ship files`() {
        val m = FlightModel()
        m.missionFlags = 0b1010
        m.combat.rng = Random(4)
        repeat(20) {
            m.loadShipFile(g3[it])
            assertTrue(m.shipFileIndex == 2 || m.shipFileIndex == 3, "arquivo ${m.shipFileIndex}")
        }
        m.arrive(g3.findByName("RIEDQUAT") ?: g3[0])
        var tries = 0
        while (m.ships.none { it.type == ShipType.THARGOID } && tries++ < 500) m.combat.spawnCheck()
        assertTrue(m.ships.any { it.type == ShipType.THARGOID })
    }

    @Test
    fun `docking runs the mission entry and saves the state`() {
        val storage = MemoryStorage()
        val s = GameSession(g1, g1[7]).also { it.storage = storage }
        s.model.player.kills = 256
        s.missionEntry()
        assertEquals(Missions.Kind.BRIEF, s.briefing?.kind)
        assertEquals(2, s.briefing!!.pages.size)
        s.nextBriefingPage(); assertEquals(1, s.briefingPage)
        s.nextBriefingPage(); assertNull(s.briefing)
        assertTrue(s.save())
        val b = GameSession(g1, g1[7]).also { it.storage = storage }
        assertTrue(b.loadSaved())
        assertEquals(1, b.model.missionFlags)
        assertTrue(b.dataScreen(g1[150]).contains("Reesdice"))
        // A recompensa da missão 2: a unidade de energia da marinha recarrega 3 por vez.
        val p = PlayerShip().also { it.navyEnergyUnit = true; it.energy = 100f }
        p.updateSystems(8f)
        assertEquals(103f, p.energy)
        assertFalse(b.model.player.navyEnergyUnit)
    }
}
