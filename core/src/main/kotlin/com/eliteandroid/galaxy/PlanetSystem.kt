package com.eliteandroid.galaxy

/** Tipos de economia (0..7), na ordem original. Índices baixos = industrial e rica. */
enum class Economy(val label: String, val shortLabel: String) {
    RICH_INDUSTRIAL("Rich Industrial", "Rich Ind"),
    AVERAGE_INDUSTRIAL("Average Industrial", "Average Ind"),
    POOR_INDUSTRIAL("Poor Industrial", "Poor Ind"),
    MAINLY_INDUSTRIAL("Mainly Industrial", "Mainly Ind"),
    MAINLY_AGRICULTURAL("Mainly Agricultural", "Mainly Agri"),
    RICH_AGRICULTURAL("Rich Agricultural", "Rich Agri"),
    AVERAGE_AGRICULTURAL("Average Agricultural", "Average Agri"),
    POOR_AGRICULTURAL("Poor Agricultural", "Poor Agri");

    companion object {
        fun of(code: Int): Economy = entries[code and 7]
    }
}

/** Tipos de governo (0..7), na ordem original. 0 = Anarquia (mais perigoso). */
enum class Government(val label: String) {
    ANARCHY("Anarchy"),
    FEUDAL("Feudal"),
    MULTI_GOVERNMENT("Multi-Government"),
    DICTATORSHIP("Dictatorship"),
    COMMUNIST("Communist"),
    CONFEDERACY("Confederacy"),
    DEMOCRACY("Democracy"),
    CORPORATE_STATE("Corporate State");

    companion object {
        fun of(code: Int): Government = entries[code and 7]
    }
}

/**
 * Semente de 4 bytes usada pelo gerador de descrições ("goat soup").
 * a = s1_lo, b = s1_hi, c = s2_lo, d = s2_hi da semente do sistema.
 */
data class GoatSoupSeed(val a: Int, val b: Int, val c: Int, val d: Int)

/**
 * Um sistema planetário, equivalente ao `plansys` de Text Elite.
 *
 * Os valores são guardados exatamente como no original (valores internos);
 * as propriedades `display*` convertem para o que o jogo mostra na tela "Data on".
 *
 * @property index        índice 0..255 dentro da galáxia
 * @property x            coordenada galáctica X (0..255)
 * @property y            coordenada galáctica Y (0..255) — o mapa é "achatado": distâncias usam y/2
 * @property economyCode  0..7 (ver [Economy])
 * @property governmentCode 0..7 (ver [Government])
 * @property techLevel    0..14 interno (exibido como techLevel + 1)
 * @property population   em centenas de milhões (exibido como population / 10 bilhões)
 * @property productivity em milhões de créditos (M CR)
 * @property radius       raio médio em km
 * @property seed         cópia da semente de 6 bytes no momento da geração do sistema
 */
data class PlanetSystem(
    val galaxyNumber: Int,
    val index: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val economyCode: Int,
    val governmentCode: Int,
    val techLevel: Int,
    val population: Int,
    val productivity: Int,
    val radius: Int,
    val goatSoupSeed: GoatSoupSeed,
    val species: String,
    val seed: Seed,
) {
    val economy: Economy get() = Economy.of(economyCode)
    val government: Government get() = Government.of(governmentCode)

    /** Tech level como mostrado no jogo (1..15). */
    val displayTechLevel: Int get() = techLevel + 1

    /** População em bilhões, como mostrado no jogo (ex.: 2.5). */
    val displayPopulationBillions: Double get() = population / 10.0

    /** Descrição procedural ("goat soup"), ex.: "Lave is most famous for ...". */
    val description: String by lazy { GoatSoup.describe(this) }

    /** Nome com só a primeira letra maiúscula (ex.: "Lave"). */
    val displayName: String get() = capitalizeName(name)

    /** Texto equivalente à tela "Data on <planeta>" (comando `local`/`info` de Text Elite). */
    fun dataScreen(): String = buildString {
        appendLine("System:  $name")
        appendLine("Position ($x,$y)")
        appendLine("Economy: (${economyCode}) ${economy.shortLabel}")
        appendLine("Government: (${governmentCode}) ${government.label}")
        appendLine("Tech Level: %2d".format(displayTechLevel))
        appendLine("Turnover: $productivity")
        appendLine("Radius: $radius")
        // O jogo original exibe population / 10 (bilhões) com uma casa decimal.
        appendLine("Population: %.1f Billion".format(java.util.Locale.US, displayPopulationBillions))
        appendLine("($species)")
        append(description)
    }

    companion object {
        internal fun capitalizeName(raw: String): String =
            if (raw.isEmpty()) raw else raw[0].uppercaseChar() + raw.substring(1).lowercase()
    }
}
