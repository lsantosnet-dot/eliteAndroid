package com.eliteandroid.economy

import com.eliteandroid.galaxy.PlanetSystem

/**
 * Mercado de um sistema durante uma visita. Fórmulas do original (GVL e TT151), iguais a
 * `genmarket()` de Text Elite, com aritmética de 8 bits:
 *
 *   variação   = QQ26 AND máscara        (QQ26 = byte aleatório sorteado a cada chegada)
 *   produto    = economia × fator
 *   preço      = ((preço_base + variação + produto) AND 255) × 4      (décimos de crédito)
 *   quantidade = (qtd_base + variação − produto) AND 255; negativo (bit 7) → 0; AND 63
 *
 * Itens alienígenas nunca estão disponíveis. Preço de compra = preço de venda.
 */
class Market(val system: PlanetSystem, val fluct: Int, initialAvailability: IntArray? = null) {

    /** Preços em décimos de crédito (ex.: 36 = 3.6 Cr). */
    val prices = IntArray(Commodities.COUNT) { price(Commodities.ALL[it], system.economyCode, fluct) }

    /** Unidades disponíveis (AVL). */
    val availability: IntArray = initialAvailability?.copyOf()
        ?: IntArray(Commodities.COUNT) { quantity(Commodities.ALL[it], system.economyCode, fluct) }

    companion object {
        fun price(c: Commodity, economy: Int, fluct: Int): Int =
            ((c.basePrice + (fluct and c.mask) + economy * c.factor) and 0xFF) * 4

        fun quantity(c: Commodity, economy: Int, fluct: Int): Int {
            if (c.index == Commodities.ALIEN_ITEMS) return 0
            val q = (c.baseQuantity + (fluct and c.mask) - economy * c.factor) and 0xFF
            return if (q and 0x80 != 0) 0 else q and 0x3F
        }

        /** Disponibilidade do comandante padrão em Lave (arquivo NA%, com QQ26 = 0). */
        val JAMESON_AVAILABILITY = intArrayOf(16, 15, 17, 0, 3, 28, 14, 0, 0, 10, 0, 17, 58, 7, 9, 8, 0)
    }
}
