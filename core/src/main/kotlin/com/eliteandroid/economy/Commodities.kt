package com.eliteandroid.economy

/** Unidade de venda (bits 5-6 do byte #1 da tabela QQ23). */
enum class TradeUnit(val label: String) { TONNES("t"), KILOGRAMS("kg"), GRAMS("g") }

/**
 * Uma mercadoria da tabela QQ23 do original (idêntica à de Text Elite).
 *
 * @property basePrice     byte #0
 * @property factor        fator econômico (byte #1, bits 0-4 com sinal no bit 7)
 * @property baseQuantity  byte #2
 * @property mask          byte #3: quais bits do byte aleatório da visita (QQ26) mexem no preço
 */
data class Commodity(
    val index: Int,
    val name: String,
    val basePrice: Int,
    val factor: Int,
    val unit: TradeUnit,
    val baseQuantity: Int,
    val mask: Int,
) {
    /** Ouro, platina, gemas e itens alienígenas não ocupam o porão (limite de 200 unidades cada). */
    val countsAgainstHold: Boolean get() = index <= 12
}

object Commodities {
    val ALL: List<Commodity> = listOf(
        Commodity(0, "Food", 19, -2, TradeUnit.TONNES, 6, 0b00000001),
        Commodity(1, "Textiles", 20, -1, TradeUnit.TONNES, 10, 0b00000011),
        Commodity(2, "Radioactives", 65, -3, TradeUnit.TONNES, 2, 0b00000111),
        Commodity(3, "Slaves", 40, -5, TradeUnit.TONNES, 226, 0b00011111),
        Commodity(4, "Liquor/Wines", 83, -5, TradeUnit.TONNES, 251, 0b00001111),
        Commodity(5, "Luxuries", 196, 8, TradeUnit.TONNES, 54, 0b00000011),
        Commodity(6, "Narcotics", 235, 29, TradeUnit.TONNES, 8, 0b01111000),
        Commodity(7, "Computers", 154, 14, TradeUnit.TONNES, 56, 0b00000011),
        Commodity(8, "Machinery", 117, 6, TradeUnit.TONNES, 40, 0b00000111),
        Commodity(9, "Alloys", 78, 1, TradeUnit.TONNES, 17, 0b00011111),
        Commodity(10, "Firearms", 124, 13, TradeUnit.TONNES, 29, 0b00000111),
        Commodity(11, "Furs", 176, -9, TradeUnit.TONNES, 220, 0b00111111),
        Commodity(12, "Minerals", 32, -1, TradeUnit.TONNES, 53, 0b00000011),
        Commodity(13, "Gold", 97, -1, TradeUnit.KILOGRAMS, 66, 0b00000111),
        Commodity(14, "Platinum", 171, -2, TradeUnit.KILOGRAMS, 55, 0b00011111),
        Commodity(15, "Gem-Stones", 45, -1, TradeUnit.GRAMS, 250, 0b00001111),
        Commodity(16, "Alien Items", 53, 15, TradeUnit.TONNES, 192, 0b00000111),
    )

    const val COUNT = 17
    const val ALIEN_ITEMS = 16
}
