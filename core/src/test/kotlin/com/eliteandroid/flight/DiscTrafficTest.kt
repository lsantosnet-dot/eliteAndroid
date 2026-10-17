package com.eliteandroid.flight

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.ships.DiscShips
import com.eliteandroid.ships.Newb
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import com.eliteandroid.ships.orient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Tráfego e IA da versão em disco: arquivos de naves, NEWB, comerciantes e atracação. */
class DiscTrafficTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)

    private fun spawn(m: FlightModel, type: ShipType, pos: Vector3, ai: Int = 0): ShipInstance =
        ShipInstance(type, pos).also { it.aiFlag = ai; m.addShip(it) }

    private fun launched(seed: Int = 5): FlightModel = FlightModel().also {
        it.combat.rng = Random(seed)
        it.launch(galaxy[7])
    }

    @Test
    fun `LOMOD picks the ship file from government and tech level`() {
        val m = FlightModel()
        for (s in galaxy.systems.take(60)) {
            repeat(4) {
                m.loadShipFile(s)
                val i = m.shipFileIndex
                assertEquals(s.techLevel >= 10, i and 1 == 1, s.name)
                assertEquals(s.governmentCode >= 3, i and 2 == 2, s.name)
            }
        }
        val hiTech = galaxy.systems.first { it.techLevel >= 10 }
        m.combat.rng = Random(1)
        m.launch(hiTech)
        assertEquals(ShipType.DODO, m.station!!.type) // Dodecaedro nos sistemas avançados
        m.launch(galaxy[7]) // Lave: tech level 5
        assertEquals(ShipType.STATION, m.station!!.type)
    }

    @Test
    fun `ships outside the loaded file cannot spawn, and the bubble holds NOSH`() {
        val m = FlightModel()
        m.selectShipFile(0) // A: tem Krait, não tem Adder
        assertTrue(m.spawn(ShipInstance(ShipType.KRAIT, Vector3(0f, 0f, 5000f))))
        assertFalse(m.spawn(ShipInstance(ShipType.ADDER, Vector3(0f, 0f, 5000f))))
        while (m.spawn(ShipInstance(ShipType.SIDEWINDER, Vector3(0f, 0f, 5000f)))) Unit
        assertEquals(FlightModel.NOSH - 2, m.ships.size)
    }

    @Test
    fun `a docking trader lined up with the slot docks and leaves the bubble`() {
        val m = launched()
        val st = m.station!!
        // 2000 unidades à frente da fenda, apontando para a estação.
        val pos = Vector3(st.position).mulAdd(st.nosev, 2000f)
        val t = spawn(m, ShipType.COBRA_TRADER, pos, ai = 0xC0)
        t.newb = t.newb or Newb.DOCKING
        t.setOrientation(Vector3(st.nosev).scl(-1f))
        var tries = 0
        while (t.newb and Newb.GONE == 0 && tries++ < 50) m.combat.tactics(t) // comerciantes agem 39% das vezes
        assertTrue(t.newb and Newb.GONE != 0, "não atracou")
        m.combat.step(0.1f)
        assertFalse(t in m.ships)
    }

    @Test
    fun `a docking trader off the axis heads for the approach point`() {
        val m = launched()
        val st = m.station!!
        val pos = Vector3(st.position).mulAdd(st.sidev, 3000f) // ao lado da estação
        val t = spawn(m, ShipType.COBRA_TRADER, pos, ai = 0xC0)
        t.newb = t.newb or Newb.DOCKING
        t.speed = 10f
        // Ponto ideal relativo à estação: 768 unidades de nosev à frente da fenda.
        fun distToIdeal() = Vector3(t.position).sub(st.position).sub(Vector3(st.nosev).scl(768f)).len()
        val start = distToIdeal()
        var closest = start
        repeat(300) {
            m.step(1f, FlightControls().apply { targetSpeed = 1f })
            if (t in m.ships) closest = minOf(closest, distToIdeal())
        }
        assertTrue(closest < start / 5f, "não se aproximou: $start → $closest")
    }

    @Test
    fun `traders not docking fly towards the planet`() {
        val m = FlightModel(); m.combat.rng = Random(3); m.arrive(galaxy[7]) // sem estação
        val t = spawn(m, ShipType.ANACONDA, Vector3(0f, 0f, 3000f), ai = 0xC0).orient(Vector3(-1f, 0f, 0f))
        t.newb = t.newb and Newb.HOSTILE.inv()
        val toPlanet = Vector3(m.planet!!.position).nor()
        val before = t.nosev.dot(toPlanet)
        repeat(12 * 30) { m.step(1f, FlightControls().apply { targetSpeed = 1f }) }
        assertTrue(t.nosev.dot(Vector3(m.planet!!.position).nor()) > before + 0.3f)
    }

    @Test
    fun `the station launches shuttles and transporters`() {
        val m = launched()
        m.selectShipFile(7) // H: tem Shuttle
        repeat(2000) { m.combat.tactics(m.station!!) }
        val launched = m.ships.filter { it.type == ShipType.SHUTTLE }
        assertTrue(launched.isNotEmpty())
        val s = launched.first()
        assertTrue(s.newb and Newb.TRADER != 0)
        assertFalse(s.hostile)
    }

    @Test
    fun `the anaconda launches worms`() {
        val m = launched()
        m.selectShipFile(11) // L: Anaconda e Worm
        m.station?.let { m.ships.remove(it) }
        val a = spawn(m, ShipType.ANACONDA, Vector3(0f, 0f, 4000f), ai = 0xFF)
        a.hostile = true
        repeat(200) { m.combat.tactics(a) }
        assertTrue(m.ships.any { it.type == ShipType.WORM })
    }

    @Test
    fun `bounty hunters turn on fugitives`() {
        val m = FlightModel(); m.combat.rng = Random(9); m.arrive(galaxy[7])
        val h = spawn(m, ShipType.FER_DE_LANCE, Vector3(0f, 0f, 4000f), ai = 0xC0)
        assertTrue(h.newb and Newb.BOUNTY_HUNTER != 0)
        m.combat.tactics(h)
        assertFalse(h.hostile)
        m.player.legalStatus = 50
        m.combat.tactics(h)
        assertTrue(h.hostile)
    }

    @Test
    fun `shooting an innocent trader angers the station`() {
        val m = launched()
        val t = spawn(m, ShipType.PYTHON_TRADER, Vector3(0f, 0f, 1500f))
        assertTrue(t.newb and Newb.INNOCENT != 0)
        m.combat.laserShot(15)
        assertTrue(m.station!!.hostile)
    }

    @Test
    fun `alloy plates scoop as alloys`() {
        val m = FlightModel()
        val got = ArrayList<Int>()
        m.combat.onScoop = { got += it; true }
        m.player.fuelScoops = true
        spawn(m, ShipType.ALLOY_PLATE, Vector3(0f, -20f, 30f))
        m.combat.checkCollisions()
        assertEquals(listOf(9), got)
    }

    @Test
    fun `pirates without an escape pod never eject`() {
        val m = FlightModel(); m.combat.rng = Random(11); m.arrive(galaxy[7])
        m.selectShipFile(0)
        val s = spawn(m, ShipType.SIDEWINDER, Vector3(3000f, 0f, 3000f), ai = 0xC0) // E% sem bit 7
        repeat(300) { s.energy = 1f; m.combat.tactics(s) }
        assertFalse(m.ships.any { it.type == ShipType.ESCAPE_POD })
        val k = spawn(m, ShipType.KRAIT, Vector3(-3000f, 0f, 3000f), ai = 0xC0) // com cápsula
        repeat(300) { k.energy = 1f; if (k.aiFlag != 0) m.combat.tactics(k) }
        assertTrue(m.ships.any { it.type == ShipType.ESCAPE_POD })
    }

    @Test
    fun `spawning in an anarchy uses the disc ship types`() {
        val seen = HashSet<ShipType>()
        for (seed in 1..40) {
            val m = FlightModel(); m.combat.rng = Random(seed)
            m.arrive(galaxy.findByName("RIEDQUAT")!!)
            repeat(30) { m.combat.spawnCheck() }
            m.ships.forEach { assertTrue(m.hasShip(it.type), "${it.type} fora do arquivo") }
            seen += m.ships.map { it.type }
        }
        val extras = setOf(ShipType.KRAIT, ShipType.ADDER, ShipType.GECKO, ShipType.COBRA_MK1, ShipType.WORM,
            ShipType.ASP, ShipType.FER_DE_LANCE, ShipType.BOA, ShipType.ANACONDA, ShipType.BOULDER)
        assertTrue((seen intersect extras).size >= 5, "naves extras vistas: $seen")
        assertTrue(DiscShips.SHIP_FILES.size == 16)
    }

    @Test
    fun `ten minutes of traffic around a station and in deep space`() {
        for (name in listOf("LAVE", "RIEDQUAT", "DISO")) {
            val m = FlightModel(); m.combat.rng = Random(name.hashCode())
            m.launch(galaxy.findByName(name)!!)
            var maxShips = 0
            repeat(12 * 600) {
                val e = m.step(1f, FlightControls().apply { targetSpeed = 1f })
                if (e == FlightEvent.CRASHED) m.player.energy = 255f
                maxShips = maxOf(maxShips, m.ships.count { !it.type.isStation })
            }
            assertTrue(maxShips <= FlightModel.NOSH - 2, "$name: $maxShips naves")
        }
    }
}
