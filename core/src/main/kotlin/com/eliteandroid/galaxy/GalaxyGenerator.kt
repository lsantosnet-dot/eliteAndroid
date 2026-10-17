package com.eliteandroid.galaxy

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Gerador procedural de galáxias — porte fiel de `buildgalaxy()` / `makesystem()`
 * de Text Elite 1.5 (Ian Bell), com a geração de espécies do BBC Elite
 * (rotina TT25 no disassembly de Mark Moxon).
 */
object GalaxyGenerator {

    const val GALAXY_COUNT = 8
    const val SYSTEMS_PER_GALAXY = 256

    /**
     * Tabela de pares de letras dos nomes de planetas. Os pontos são "não imprimíveis"
     * e são removidos do nome final (gerando nomes de 3, 5 ou 7 letras).
     */
    internal const val PAIRS =
        "..LEXEGEZACEBISO" +
        "USESARMAINDIREA." +
        "ERATENBERALAVETI" +
        "EDORQUANTEISRION"

    /** Semente da galáxia [galaxyNumber] (1..8), aplicando `nextgalaxy` (galaxyNumber-1) vezes. */
    fun galaxySeed(galaxyNumber: Int): Seed {
        require(galaxyNumber in 1..GALAXY_COUNT) { "galaxyNumber deve estar em 1..8: $galaxyNumber" }
        val seed = Seed.base()
        repeat(galaxyNumber - 1) { seed.nextGalaxy() }
        return seed
    }

    /** Gera os 256 sistemas da galáxia [galaxyNumber] (1..8). */
    fun buildGalaxy(galaxyNumber: Int): Galaxy {
        val seed = galaxySeed(galaxyNumber)
        val systems = List(SYSTEMS_PER_GALAXY) { i -> makeSystem(galaxyNumber, i, seed) }
        return Galaxy(galaxyNumber, systems)
    }

    /** Gera as 8 galáxias. */
    fun buildAll(): List<Galaxy> = (1..GALAXY_COUNT).map(::buildGalaxy)

    /**
     * Porte direto de `makesystem()`. Consome a semente: avança [seed] 4 vezes
     * (sempre 4 iterações, mesmo para nomes curtos), deixando-a pronta para o próximo sistema.
     */
    internal fun makeSystem(galaxyNumber: Int, index: Int, seed: Seed): PlanetSystem {
        val original = seed.copy()
        val longNameFlag = (seed.w0 and 64) != 0

        val x = seed.w1 ushr 8
        val y = seed.w0 ushr 8

        val govType = (seed.w1 ushr 3) and 7 // bits 3,4,5 de w1

        var economy = (seed.w0 ushr 8) and 7 // bits 8,9,10 de w0
        if (govType <= 1) economy = economy or 2 // Anarquia/Feudal nunca são "Rich"

        var techLevel = ((seed.w1 ushr 8) and 3) + (economy xor 7)
        techLevel += govType ushr 1
        if ((govType and 1) == 1) techLevel += 1 // simula LSR seguido de ADC do 6502

        var population = 4 * techLevel + economy
        population += govType + 1

        var productivity = ((economy xor 7) + 3) * (govType + 4)
        productivity *= population * 8

        val radius = 256 * (((seed.w2 ushr 8) and 15) + 11) + x

        val goatSoupSeed = GoatSoupSeed(
            a = seed.w1 and 0xFF,
            b = seed.w1 ushr 8,
            c = seed.w2 and 0xFF,
            d = seed.w2 ushr 8,
        )

        val species = species(original)

        val pair1 = 2 * ((seed.w2 ushr 8) and 31); seed.tweak()
        val pair2 = 2 * ((seed.w2 ushr 8) and 31); seed.tweak()
        val pair3 = 2 * ((seed.w2 ushr 8) and 31); seed.tweak()
        val pair4 = 2 * ((seed.w2 ushr 8) and 31); seed.tweak()

        val raw = StringBuilder(8)
        raw.append(PAIRS[pair1]).append(PAIRS[pair1 + 1])
        raw.append(PAIRS[pair2]).append(PAIRS[pair2 + 1])
        raw.append(PAIRS[pair3]).append(PAIRS[pair3 + 1])
        if (longNameFlag) { // bit 6 do w0 ORIGINAL indica nome de 4 pares
            raw.append(PAIRS[pair4]).append(PAIRS[pair4 + 1])
        }
        val name = raw.toString().replace(".", "") // stripout(name, '.')

        return PlanetSystem(
            galaxyNumber = galaxyNumber,
            index = index,
            name = name,
            x = x,
            y = y,
            economyCode = economy,
            governmentCode = govType,
            techLevel = techLevel,
            population = population,
            productivity = productivity,
            radius = radius,
            goatSoupSeed = goatSoupSeed,
            species = species,
            seed = original,
        )
    }

    private val SPECIES_SIZE = arrayOf("Large", "Fierce", "Small")
    private val SPECIES_COLOUR = arrayOf("Green", "Red", "Yellow", "Blue", "Black", "Harmless")
    private val SPECIES_LOOK = arrayOf("Slimy", "Bug-Eyed", "Horned", "Bony", "Fat", "Furry")
    private val SPECIES_TYPE =
        arrayOf("Rodents", "Frogs", "Lizards", "Lobsters", "Birds", "Humanoids", "Felines", "Insects")

    /**
     * Espécie dominante do sistema (BBC Elite, TT25). Text Elite não implementa isto,
     * mas faz parte dos atributos originais exibidos na tela "Data on".
     */
    internal fun species(seed: Seed): String {
        if ((seed.s2Lo and 0x80) == 0) return "Human Colonials"

        val parts = ArrayList<String>(4)
        val a = (seed.s2Hi ushr 2) and 7
        if (a < 3) parts += SPECIES_SIZE[a]
        val b = (seed.s2Hi ushr 5) and 7
        if (b < 6) parts += SPECIES_COLOUR[b]
        val c = (seed.s0Hi xor seed.s1Hi) and 7
        if (c < 6) parts += SPECIES_LOOK[c]
        val d = ((seed.s2Hi and 3) + c) and 7
        parts += SPECIES_TYPE[d]
        return parts.joinToString(" ")
    }

    /**
     * Distância entre dois sistemas em décimos de ano-luz (70 = 7.0 LY),
     * porte exato de `distance()` de Text Elite — inclusive a divisão inteira de dy²/4.
     */
    fun distance(a: PlanetSystem, b: PlanetSystem): Int = distance(a.x, a.y, b.x, b.y)

    /** A mesma distância entre coordenadas galácticas (o witchspace fica fora dos sistemas). */
    fun distance(ax: Int, ay: Int, bx: Int, by: Int): Int {
        val dx = ax - bx
        val dy = ay - by
        val sq = dx * dx + (dy * dy) / 4
        return floor(4.0 * sqrt(sq.toDouble()) + 0.5).toInt() // ftoi()
    }
}

/** Uma galáxia gerada: 256 sistemas indexados de 0 a 255. */
class Galaxy(val number: Int, val systems: List<PlanetSystem>) {

    operator fun get(index: Int): PlanetSystem = systems[index]

    val size: Int get() = systems.size

    /** Busca pelo nome (sem diferenciar maiúsculas), como `matchsys` aproximado de Text Elite. */
    fun findByName(name: String): PlanetSystem? =
        systems.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Sistemas alcançáveis a partir de [from] com o [fuel] dado (décimos de LY).
     * O máximo do original é 70 (7.0 LY).
     */
    fun inRange(from: PlanetSystem, fuel: Int = 70): List<PlanetSystem> =
        systems.filter { it.index != from.index && GalaxyGenerator.distance(from, it) <= fuel }

    /** Sistema mais próximo de uma coordenada galáctica (ex.: cursor do mapa). */
    fun nearestTo(x: Int, y: Int): PlanetSystem =
        systems.minBy { s ->
            val dx = s.x - x
            val dy = s.y - y
            dx * dx + (dy * dy) / 4
        }
}
