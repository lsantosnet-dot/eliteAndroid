package com.eliteandroid.galaxy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GalaxyGeneratorTest {

    private val galaxy1 = GalaxyGenerator.buildGalaxy(1)

    @Test
    fun `galaxy 1 starts with the classic system names`() {
        val expected = listOf("TIBEDIED", "QUBE", "LELEER", "BIARGE", "XEQUERIN", "TIRAOR", "RABEDIRA", "LAVE")
        assertEquals(expected, galaxy1.systems.take(8).map { it.name })
    }

    @Test
    fun `Lave matches the original data screen`() {
        val lave = galaxy1[7]
        assertEquals("LAVE", lave.name)
        assertEquals(20, lave.x)
        assertEquals(173, lave.y)
        assertEquals(Economy.RICH_AGRICULTURAL, lave.economy)
        assertEquals(Government.DICTATORSHIP, lave.government)
        assertEquals(5, lave.displayTechLevel)
        assertEquals(25, lave.population) // 2.5 bilhões
        assertEquals(7000, lave.productivity)
        assertEquals(4116, lave.radius)
        assertEquals("Human Colonials", lave.species)
        assertEquals("Lave is most famous for its vast rain forests and the Lavian tree grub.", lave.description)
    }

    @Test
    fun `canonical descriptions and species`() {
        fun sys(n: String) = galaxy1.findByName(n)!!
        assertEquals(
            "This planet is most notable for Tibediedian Arnu brandy but ravaged by unpredictable solar activity.",
            sys("TIBEDIED").description,
        )
        assertEquals("This planet is a tedious place.", sys("ZAONCE").description)
        assertEquals("The planet Leesti is reasonably fabled for Zero-G cricket and Leestian evil juice.", sys("LEESTI").description)
        assertEquals("Black Furry Felines", sys("DISO").species)
        assertEquals(Government.ANARCHY, sys("RIEDQUAT").government)
        assertEquals(Government.CORPORATE_STATE, sys("ZAONCE").government)
    }

    @Test
    fun `famous galaxy 1 systems exist`() {
        listOf("LAVE", "DISO", "LEESTI", "ZAONCE", "RIEDQUAT", "REORTE", "ORERVE").forEach {
            assertNotNull(galaxy1.findByName(it), "sistema $it não encontrado")
        }
    }

    @Test
    fun `every galaxy has 256 systems with valid attributes`() {
        GalaxyGenerator.buildAll().forEach { g ->
            assertEquals(256, g.size)
            g.systems.forEach { s ->
                assertTrue(s.name.length in 2..8, s.name)
                assertTrue(s.x in 0..255 && s.y in 0..255)
                assertTrue(s.techLevel in 0..14)
                if (s.governmentCode <= 1) assertTrue(s.economyCode and 2 != 0)
                assertTrue(s.description.endsWith("."))
            }
        }
    }

    @Test
    fun `galactic hyperdrive wraps after 8 jumps`() {
        val seed = Seed.base()
        repeat(8) { seed.nextGalaxy() }
        assertEquals(Seed.base(), seed)
    }

    @Test
    fun `generation is deterministic`() {
        val again = GalaxyGenerator.buildGalaxy(1)
        assertEquals(galaxy1.systems.map { it.name to it.description }, again.systems.map { it.name to it.description })
    }

    @Test
    fun `distance matches Text Elite`() {
        val lave = galaxy1.findByName("LAVE")!!
        assertEquals(0, GalaxyGenerator.distance(lave, lave))
        val inRange = galaxy1.inRange(lave).associate { it.name to GalaxyGenerator.distance(lave, it) }
        assertEquals(
            mapOf("DISO" to 36, "LEESTI" to 38, "REORTE" to 44, "ZAONCE" to 57, "ORERVE" to 68, "RIEDQUAT" to 70),
            inRange,
        )
    }
}
