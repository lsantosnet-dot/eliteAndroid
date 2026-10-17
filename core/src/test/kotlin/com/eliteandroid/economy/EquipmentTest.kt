package com.eliteandroid.economy

import com.eliteandroid.GameSession
import com.eliteandroid.economy.Equipment.Result
import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.flight.View
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.save.CommanderSave
import com.eliteandroid.save.MemoryStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquipmentTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)
    private val hiTech = galaxy.systems.first { it.techLevel >= 9 }

    private fun rich() = Commander().also { it.earn(1_000_000) }

    @Test
    fun `PRXS prices and the order of the original`() {
        assertEquals(
            listOf(2L, 300L, 4000L, 6000L, 4000L, 10000L, 5250L, 10000L, 9000L, 15000L, 10000L, 50000L),
            Equipment.entries.map { it.price },
        )
    }

    @Test
    fun `items for sale grow with the tech level (tek + 3, up to 12)`() {
        assertEquals(3, Equipment.available(0).size)
        assertEquals(7, Equipment.available(galaxy[7].techLevel).size) // Lave: tech 5 (interno 4)
        assertEquals(12, Equipment.available(9).size)
        assertEquals(12, Equipment.available(14).size)
    }

    @Test
    fun `cannot buy what the system does not sell`() {
        val p = PlayerShip()
        assertEquals(Result.NOT_AVAILABLE, Equipment.buy(Equipment.ESCAPE_POD, 4, p, rich()))
    }

    @Test
    fun `missiles up to four`() {
        val p = PlayerShip(); val c = rich()
        assertEquals(Result.OK, Equipment.buy(Equipment.MISSILE, 14, p, c))
        assertEquals(4, p.missiles)
        assertEquals(Result.ALL_PRESENT, Equipment.buy(Equipment.MISSILE, 14, p, c))
    }

    @Test
    fun `one of each item, without paying twice`() {
        val p = PlayerShip(); val c = rich()
        assertEquals(Result.OK, Equipment.buy(Equipment.ECM, 14, p, c))
        val cash = c.cash
        assertEquals(Result.PRESENT, Equipment.buy(Equipment.ECM, 14, p, c))
        assertEquals(cash, c.cash)
        assertTrue(p.hasEcm)
    }

    @Test
    fun `not enough cash`() {
        val p = PlayerShip(); val c = Commander() // 100 Cr
        assertEquals(Result.NO_CASH, Equipment.buy(Equipment.ECM, 14, p, c))
        assertFalse(p.hasEcm)
        assertEquals(Commander.START_CASH, c.cash)
    }

    @Test
    fun `large cargo bay raises the hold to 35 t`() {
        val p = PlayerShip(); val c = rich()
        Equipment.buy(Equipment.LARGE_CARGO_BAY, 14, p, c)
        assertEquals(35, c.cargoCapacity)
        assertEquals(Result.PRESENT, Equipment.buy(Equipment.LARGE_CARGO_BAY, 14, p, c))
    }

    @Test
    fun `pulse laser only on an empty view, beam replaces pulse with a refund`() {
        val p = PlayerShip(); val c = rich()
        assertEquals(Result.PRESENT, Equipment.buy(Equipment.PULSE_LASER, 14, p, c, View.FRONT))
        assertEquals(Result.OK, Equipment.buy(Equipment.PULSE_LASER, 14, p, c, View.REAR))
        assertEquals(PlayerShip.PULSE_LASER, p.lasers[View.REAR.ordinal])

        val before = c.cash
        assertEquals(Result.OK, Equipment.buy(Equipment.BEAM_LASER, 14, p, c, View.FRONT))
        assertEquals(before - 10000 + 4000, c.cash)
        assertEquals(PlayerShip.BEAM_LASER, p.lasers[View.FRONT.ordinal])
        assertEquals(Result.PRESENT, Equipment.buy(Equipment.BEAM_LASER, 14, p, c, View.FRONT))
        assertEquals(Result.PRESENT, Equipment.buy(Equipment.PULSE_LASER, 14, p, c, View.FRONT))
    }

    @Test
    fun `beam laser fires every main loop cycle, pulse every 10 vsyncs`() {
        fun shots(power: Int): Int {
            val p = PlayerShip(); p.lasers[0] = power
            var n = 0
            // 1 s a 60 passos/s, 12 ciclos/s (laser frio o bastante para não superaquecer).
            repeat(60) { if (p.updateLaser(0.2f, 1f / 60f, true, View.FRONT)) n++ }
            return n
        }
        assertTrue(shots(PlayerShip.PULSE_LASER) in 4..6)
        assertTrue(shots(PlayerShip.BEAM_LASER) in 11..13)
    }

    @Test
    fun `fuel costs 2 tenths per 0_1 LY`() {
        val s = GameSession(galaxy, galaxy[7])
        s.model.player.fuel = 30f
        assertEquals(80L, s.refuelCost)
        assertEquals(Result.OK, s.buyEquipment(Equipment.FUEL))
        assertEquals(70f, s.model.player.fuel)
        assertEquals(Result.PRESENT, s.buyEquipment(Equipment.FUEL))
    }

    @Test
    fun `equipment is saved and old version 1 saves still load`() {
        val storage = MemoryStorage()
        val s = GameSession(galaxy, galaxy[7]).also { it.storage = storage }
        s.commander.earn(1_000_000)
        // Atracado num sistema hi-tech (carregando um save editado).
        storage.write(s.snapshot().copy(system = hiTech.index).encode())
        assertTrue(s.loadSaved())
        for (item in listOf(Equipment.ECM, Equipment.FUEL_SCOOPS, Equipment.ESCAPE_POD, Equipment.ENERGY_BOMB,
            Equipment.ENERGY_UNIT, Equipment.DOCKING_COMPUTER, Equipment.GALACTIC_HYPERDRIVE)) {
            assertEquals(Result.OK, s.buyEquipment(item), item.name)
        }
        assertEquals(Result.OK, s.buyEquipment(Equipment.BEAM_LASER, View.LEFT))
        assertTrue(s.save())

        val b = GameSession(galaxy, galaxy[7]).also { it.storage = storage }
        assertTrue(b.loadSaved())
        val p = b.model.player
        assertTrue(p.hasEcm && p.fuelScoops && p.escapePod && p.energyBomb && p.energyUnit && p.dockingComputer && p.galacticHyperdrive)
        assertEquals(PlayerShip.BEAM_LASER, p.lasers[View.LEFT.ordinal])

        val v1 = s.snapshot().encode().lines()
            .filterNot { it.substringBefore('=') in setOf("lasers", "ecm", "scoops", "pod", "bomb", "energyUnit", "dock", "ghyp") }
            .joinToString("\n").replace("version=2", "version=1")
        val old = CommanderSave.decode(v1)!!
        assertEquals(listOf(15, 0, 0, 0), old.lasers.toList())
        assertFalse(old.ecm)
    }
}
