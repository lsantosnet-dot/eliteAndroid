package com.eliteandroid.economy

import com.eliteandroid.galaxy.GalaxyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketTest {

    private val galaxy = GalaxyGenerator.buildGalaxy(1)
    private val lave = galaxy.findByName("LAVE")!! // economia 5 (Rich Agri)

    @Test
    fun `seventeen commodities from QQ23`() {
        assertEquals(17, Commodities.ALL.size)
        assertEquals("Food", Commodities.ALL[0].name)
        assertEquals("Alien Items", Commodities.ALL[16].name)
        assertTrue(Commodities.ALL.withIndex().all { (i, c) -> c.index == i })
    }

    @Test
    fun `price formula at Lave with QQ26 = 0`() {
        val m = Market(lave, fluct = 0)
        assertEquals((19 - 10) * 4, m.prices[0])   // Food: 19 + 5·(−2) → 3.6 Cr
        assertEquals((196 + 40) * 4, m.prices[5])  // Luxuries: 196 + 5·8 → 94.4 Cr
        assertEquals(((235 + 145) and 0xFF) * 4, m.prices[6]) // Narcotics estoura 8 bits
        assertEquals((97 - 5) * 4, m.prices[13])   // Gold
    }

    @Test
    fun `quantity formula clips negatives and masks to 6 bits`() {
        val m = Market(lave, fluct = 0)
        assertEquals(16, m.availability[0])  // Food: 6 + 10
        assertEquals(15, m.availability[1])  // Textiles: 10 + 5
        assertEquals(17, m.availability[2])  // Radioactives: 2 + 15
        assertEquals(0, m.availability[3])   // Slaves: 226 + 25 = 251 → bit 7 → 0
        assertEquals(14, m.availability[5])  // Luxuries: 54 − 40
        assertEquals(0, m.availability[16])  // itens alienígenas nunca
    }

    @Test
    fun `fluctuation only touches the masked bits`() {
        val a = Market(lave, fluct = 0)
        val b = Market(lave, fluct = 0xFF)
        Commodities.ALL.forEach { c ->
            assertEquals(((c.basePrice + c.mask + 5 * c.factor) and 0xFF) * 4, b.prices[c.index])
            assertTrue(b.prices[c.index] != a.prices[c.index] || c.mask == 0 || (a.prices[c.index] + c.mask * 4) % 1024 != a.prices[c.index])
        }
    }

    @Test
    fun `buying and selling follow cash, stock and hold rules`() {
        val m = Market(lave, fluct = 0)
        val cmdr = Commander()
        assertEquals(Commander.TradeResult.OK, cmdr.buy(m, 0, 10)) // 10 t de comida a 3.6
        assertEquals(1000L - 360L, cmdr.cash)
        assertEquals(6, m.availability[0])
        assertEquals(Commander.TradeResult.NO_STOCK, cmdr.buy(m, 0, 7))
        assertEquals(Commander.TradeResult.OK, cmdr.buy(m, 1, 10))  // 20 t: porão cheio (6.0 cada)
        assertEquals(40L, cmdr.cash)
        assertEquals(Commander.TradeResult.NO_ROOM, cmdr.buy(m, 2, 1))
        assertEquals(Commander.TradeResult.OK, cmdr.sell(m, 0, 10))  // vende a comida: 4.0 + 36.0
        assertEquals(400L, cmdr.cash)
        assertEquals(16, m.availability[0])
        assertEquals(Commander.TradeResult.OK, cmdr.buy(m, 13, 1))  // ouro (36.8, kg) não ocupa o porão
        assertEquals(10, cmdr.tonnesUsed)
        assertEquals(Commander.TradeResult.NONE_IN_HOLD, cmdr.sell(m, 0, 1))
        val poor = Commander()
        assertEquals(Commander.TradeResult.NO_CASH, poor.buy(m, 5, 2)) // 2 × 94.4 > 100
    }
}
