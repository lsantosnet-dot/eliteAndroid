package com.eliteandroid.economy

/**
 * Dados do comandante relativos ao comércio (arquivo de comandante NA%): dinheiro em
 * décimos de crédito (CASH), porão (QQ20) e capacidade de carga (CRGO).
 * O comandante padrão (JAMESON) começa com 100.0 Cr e porão vazio de 20 t.
 */
class Commander {

    var cash: Long = START_CASH
        private set
    val cargo = IntArray(Commodities.COUNT)

    /** Capacidade em toneladas (CRGO guarda 22 para 20 t). */
    var cargoCapacity = 20

    enum class TradeResult { OK, NO_STOCK, NO_CASH, NO_ROOM, NONE_IN_HOLD }

    @Synchronized fun reset() {
        cash = START_CASH
        cargo.fill(0)
        cargoCapacity = 20
    }

    /** Toneladas ocupadas (só itens 0..12 contam para o porão). */
    val tonnesUsed: Int get() = (0..12).sumOf { cargo[it] }

    /** tnpr: há lugar para [units] unidades do item [index]? */
    fun hasRoom(index: Int, units: Int): Boolean =
        if (index <= 12) tonnesUsed + units <= cargoCapacity
        else cargo[index] + units < 200

    /** TT219: compra [units] do item [index] no [market] (checa estoque, porão e depois dinheiro). */
    @Synchronized fun buy(market: Market, index: Int, units: Int = 1): TradeResult {
        if (market.availability[index] < units) return TradeResult.NO_STOCK
        if (!hasRoom(index, units)) return TradeResult.NO_ROOM
        val cost = market.prices[index].toLong() * units
        if (cost > cash) return TradeResult.NO_CASH
        cash -= cost
        cargo[index] += units
        market.availability[index] -= units
        return TradeResult.OK
    }

    /** TT208/TT210: vende [units] do item [index] pelo preço atual (volta ao estoque do mercado). */
    @Synchronized fun sell(market: Market, index: Int, units: Int = 1): TradeResult {
        if (cargo[index] < units) return TradeResult.NONE_IN_HOLD
        cash += market.prices[index].toLong() * units
        cargo[index] -= units
        market.availability[index] += units
        return TradeResult.OK
    }

    /** Recebe [tenths] décimos de crédito (recompensas). */
    @Synchronized fun earn(tenths: Long) {
        cash += tenths
    }

    /** BAD: (escravos + narcóticos)·2 + armas de fogo, em toneladas. */
    val contraband: Int get() = (cargo[3] + cargo[6]) * 2 + cargo[10]

    /** Gasta [tenths] décimos de crédito, se houver. */
    @Synchronized fun spend(tenths: Long): Boolean {
        if (tenths > cash) return false
        cash -= tenths
        return true
    }

    companion object {
        const val START_CASH = 1000L // 100.0 Cr

        fun formatCredits(tenths: Long): String = "%d.%d Cr".format(tenths / 10, tenths % 10)

        /** Classificação de combate pelo número de abates (TALLY). */
        fun rating(kills: Int): String = when {
            kills < 8 -> "Harmless"
            kills < 16 -> "Mostly Harmless"
            kills < 32 -> "Poor"
            kills < 64 -> "Average"
            kills < 128 -> "Above Average"
            kills < 512 -> "Competent"
            kills < 2560 -> "Dangerous"
            kills < 6400 -> "Deadly"
            else -> "---- E L I T E ----"
        }

        /** Status legal pelo FIST. */
        fun legal(fist: Int): String = when {
            fist == 0 -> "Clean"
            fist < 50 -> "Offender"
            else -> "Fugitive"
        }
    }
}
