package com.eliteandroid.galaxy

/**
 * Gerador de descrições planetárias ("goat soup") — porte fiel de `goat_soup()` e
 * `gen_rnd_number()` de Text Elite 1.5.
 *
 * Os textos usam "tokens" de 1 byte >= 0x80:
 *  - 0x81..0xA4: escolhe uma das 5 opções de [DESC_LIST] com base no próximo número aleatório
 *  - 0xB0: nome do planeta
 *  - 0xB1: nome do planeta + "ian" (ex.: "Lavian")
 *  - 0xB2: nome aleatório
 */
object GoatSoup {

    /** Tabela de pares usada para nomes aleatórios (token 0xB2). */
    private const val PAIRS0 =
        "ABOUSEITILETSTONLONUTHNO" +
        "ALLEXEGEZACEBISOUSESARMAINDIREA.ERATENBERALAVETIEDORQUANTEISRION"

    /** Texto inicial: "<0x8F> is <0x97>." */
    private const val TEMPLATE = "\u008F is \u0097."

    private val DESC_LIST: Array<Array<String>> = arrayOf(
        /* 81 */ arrayOf("fabled", "notable", "well known", "famous", "noted"),
        /* 82 */ arrayOf("very", "mildly", "most", "reasonably", ""),
        /* 83 */ arrayOf("ancient", "\u0095", "great", "vast", "pink"),
        /* 84 */ arrayOf("\u009E \u009D plantations", "mountains", "\u009C", "\u0094 forests", "oceans"),
        /* 85 */ arrayOf("shyness", "silliness", "mating traditions", "loathing of \u0086", "love for \u0086"),
        /* 86 */ arrayOf("food blenders", "tourists", "poetry", "discos", "\u008E"),
        /* 87 */ arrayOf("talking tree", "crab", "bat", "lobst", "²"),
        /* 88 */ arrayOf("beset", "plagued", "ravaged", "cursed", "scourged"),
        /* 89 */ arrayOf("\u0096 civil war", "\u009B \u0098 \u0099s", "a \u009B disease", "\u0096 earthquakes", "\u0096 solar activity"),
        /* 8A */ arrayOf("its \u0083 \u0084", "the ± \u0098 \u0099", "its inhabitants' \u009A \u0085", "¡", "its \u008D \u008E"),
        /* 8B */ arrayOf("juice", "brandy", "water", "brew", "gargle blasters"),
        /* 8C */ arrayOf("²", "± \u0099", "± ²", "± \u009B", "\u009B ²"),
        /* 8D */ arrayOf("fabulous", "exotic", "hoopy", "unusual", "exciting"),
        /* 8E */ arrayOf("cuisine", "night life", "casinos", "sit coms", " ¡ "),
        /* 8F */ arrayOf("°", "The planet °", "The world °", "This planet", "This world"),
        /* 90 */ arrayOf("n unremarkable", " boring", " dull", " tedious", " revolting"),
        /* 91 */ arrayOf("planet", "world", "place", "little planet", "dump"),
        /* 92 */ arrayOf("wasp", "moth", "grub", "ant", "²"),
        /* 93 */ arrayOf("poet", "arts graduate", "yak", "snail", "slug"),
        /* 94 */ arrayOf("tropical", "dense", "rain", "impenetrable", "exuberant"),
        /* 95 */ arrayOf("funny", "wierd", "unusual", "strange", "peculiar"),
        /* 96 */ arrayOf("frequent", "occasional", "unpredictable", "dreadful", "deadly"),
        /* 97 */ arrayOf("\u0082 \u0081 for \u008A", "\u0082 \u0081 for \u008A and \u008A", "\u0088 by \u0089", "\u0082 \u0081 for \u008A but \u0088 by \u0089", "a\u0090 \u0091"),
        /* 98 */ arrayOf("\u009B", "mountain", "edible", "tree", "spotted"),
        /* 99 */ arrayOf("\u009F", " ", "\u0087oid", "\u0093", "\u0092"),
        /* 9A */ arrayOf("ancient", "exceptional", "eccentric", "ingrained", "\u0095"),
        /* 9B */ arrayOf("killer", "deadly", "evil", "lethal", "vicious"),
        /* 9C */ arrayOf("parking meters", "dust clouds", "ice bergs", "rock formations", "volcanoes"),
        /* 9D */ arrayOf("plant", "tulip", "banana", "corn", "²weed"),
        /* 9E */ arrayOf("²", "± ²", "± \u009B", "inhabitant", "± ²"),
        /* 9F */ arrayOf("shrew", "beast", "bison", "snake", "wolf"),
        /* A0 */ arrayOf("leopard", "cat", "monkey", "goat", "fish"),
        /* A1 */ arrayOf("\u008C \u008B", "± \u009F ¢", "its \u008D   ¢", "£ ¤", "\u008C \u008B"),
        /* A2 */ arrayOf("meat", "cutlet", "steak", "burgers", "soup"),
        /* A3 */ arrayOf("ice", "mud", "Zero-G", "vacuum", "± ultra"),
        /* A4 */ arrayOf("hockey", "cricket", "karate", "polo", "tennis"),
    )

    /** Gera a descrição do sistema. Determinística: depende só da semente do sistema. */
    fun describe(system: PlanetSystem): String {
        val rng = Rng(system.goatSoupSeed)
        val out = StringBuilder(128)
        expand(TEMPLATE, system, rng, out)
        return out.toString()
    }

    private fun expand(source: String, sys: PlanetSystem, rng: Rng, out: StringBuilder) {
        for (ch in source) {
            val c = ch.code
            when {
                c < 0x80 -> out.append(ch)
                c <= 0xA4 -> {
                    val rnd = rng.next()
                    val option = (if (rnd >= 0x33) 1 else 0) + (if (rnd >= 0x66) 1 else 0) +
                        (if (rnd >= 0x99) 1 else 0) + (if (rnd >= 0xCC) 1 else 0)
                    expand(DESC_LIST[c - 0x81][option], sys, rng, out)
                }
                c == 0xB0 -> out.append(PlanetSystem.capitalizeName(sys.name))
                c == 0xB1 -> out.append(adjective(sys.name))
                c == 0xB2 -> randomName(rng, out)
                else -> error("Token inválido nos dados: 0x%X".format(c))
            }
        }
    }

    /** "<planeta>ian": remove um 'E' ou 'I' final antes de acrescentar "ian". */
    private fun adjective(name: String): String {
        val sb = StringBuilder()
        sb.append(name[0])
        for (i in 1 until name.length) {
            val last = i == name.length - 1
            if (!last || (name[i] != 'E' && name[i] != 'I')) sb.append(name[i].lowercaseChar())
        }
        return sb.append("ian").toString()
    }

    private fun randomName(rng: Rng, out: StringBuilder) {
        val len = rng.next() and 3
        for (i in 0..len) {
            val x = rng.next() and 0x3E
            val c1 = PAIRS0[x]
            val c2 = PAIRS0[x + 1]
            // Como em Text Elite 1.5: 1º caractere maiúsculo, demais minúsculos.
            // O '.' do par "A." é tratado como não imprimível (como nos nomes de planetas).
            if (c1 != '.') out.append(if (i == 0) c1 else c1.lowercaseChar())
            if (c2 != '.') out.append(c2.lowercaseChar())
        }
    }

    /** `gen_rnd_number()`: PRNG de 4 bytes que simula as flags de carry do 6502. */
    private class Rng(seed: GoatSoupSeed) {
        private var a = seed.a
        private var b = seed.b
        private var c = seed.c
        private var d = seed.d

        fun next(): Int {
            var x = (a * 2) and 0xFF
            var acc = x + c
            if (a > 127) acc++
            a = acc and 0xFF
            c = x

            acc /= 256 // carry da soma anterior
            x = b
            acc = (acc + x + d) and 0xFF
            b = acc
            d = x
            return acc
        }
    }
}
