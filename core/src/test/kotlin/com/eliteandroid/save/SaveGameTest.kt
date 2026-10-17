package com.eliteandroid.save

import com.eliteandroid.GameSession
import com.eliteandroid.galaxy.GalaxyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveGameTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)

    private fun session(storage: SaveStorage) = GameSession(galaxy, galaxy[7]).also { it.storage = storage }

    @Test
    fun `encode and decode round-trip`() {
        val s = session(MemoryStorage())
        s.buy(0, 5); s.buy(13, 2)
        val snap = s.snapshot()
        assertEquals(snap, CommanderSave.decode(snap.encode()))
    }

    @Test
    fun `a saved commander comes back where it left off`() {
        val storage = MemoryStorage()
        val a = session(storage)
        a.buy(0, 7)
        a.hyperspaceTarget = galaxy.findByName("DISO")
        a.model.player.kills = 9
        assertTrue(a.save())

        val b = session(storage) // "abrir o app de novo"
        assertTrue(b.loadSaved())
        assertEquals(a.commander.cash, b.commander.cash)
        assertEquals(7, b.commander.cargo[0])
        assertEquals("DISO", b.hyperspaceTarget?.name)
        assertEquals(9, b.model.player.kills)
        assertEquals(a.market.prices.toList(), b.market.prices.toList())
        assertEquals(a.market.availability.toList(), b.market.availability.toList())
        assertEquals(GameSession.State.DOCKED, b.state)
    }

    @Test
    fun `cannot save in flight`() {
        val s = session(MemoryStorage())
        s.launchNow()
        assertFalse(s.save())
    }

    @Test
    fun `new game erases the save`() {
        val storage = MemoryStorage()
        val s = session(storage)
        s.buy(0, 3); s.save()
        s.newGame()
        assertFalse(s.hasSave)
        assertEquals(1000L, s.commander.cash)
        assertEquals(0, s.commander.cargo[0])
    }

    @Test
    fun `corrupted files are ignored`() {
        assertNull(CommanderSave.decode(null))
        assertNull(CommanderSave.decode("lixo"))
        assertNull(CommanderSave.decode("version=1\nsystem=999"))
        assertFalse(session(MemoryStorage("version=1\ncash=-5")).loadSaved())
    }
}
