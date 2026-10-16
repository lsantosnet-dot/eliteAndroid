package com.eliteandroid.galaxy

/**
 * Semente de 6 bytes do Elite original: três palavras de 16 bits (w0, w1, w2).
 *
 * Fiel ao `seedtype` de Text Elite (Ian Bell) e às variáveis QQ15 do disassembly
 * de Mark Moxon (s0 = w0, s1 = w1, s2 = w2; `_lo` = byte baixo, `_hi` = byte alto).
 *
 * Toda a aritmética é feita em 16 bits sem sinal (mascarada com 0xFFFF), exatamente
 * como a aritmética de 2 bytes do 6502/Z80.
 */
class Seed(w0: Int, w1: Int, w2: Int) {

    var w0: Int = w0 and 0xFFFF
        private set
    var w1: Int = w1 and 0xFFFF
        private set
    var w2: Int = w2 and 0xFFFF
        private set

    val s0Lo: Int get() = w0 and 0xFF
    val s0Hi: Int get() = w0 ushr 8
    val s1Lo: Int get() = w1 and 0xFF
    val s1Hi: Int get() = w1 ushr 8
    val s2Lo: Int get() = w2 and 0xFF
    val s2Hi: Int get() = w2 ushr 8

    /**
     * "Twist" de Fibonacci: avança o gerador para o próximo valor.
     * Equivale a `tweakseed()` de Text Elite / rotina TT54 do BBC Elite:
     *   temp = w0 + w1 + w2 (16 bits); w0 = w1; w1 = w2; w2 = temp
     */
    fun tweak() {
        val temp = (w0 + w1 + w2) and 0xFFFF
        w0 = w1
        w1 = w2
        w2 = temp
    }

    /**
     * Avança para a semente da próxima galáxia: cada um dos 6 bytes é rotacionado
     * 1 bit para a esquerda (rotina `nextgalaxy()` / `twist()` / `rotatel()`).
     * Após 8 rotações a semente volta ao valor original (galáxia 9 == galáxia 1).
     */
    fun nextGalaxy() {
        w0 = twist(w0)
        w1 = twist(w1)
        w2 = twist(w2)
    }

    fun copy(): Seed = Seed(w0, w1, w2)

    override fun equals(other: Any?): Boolean =
        other is Seed && other.w0 == w0 && other.w1 == w1 && other.w2 == w2

    override fun hashCode(): Int = (w0 * 31 + w1) * 31 + w2

    override fun toString(): String = "Seed(%04X %04X %04X)".format(w0, w1, w2)

    companion object {
        /** Semente da galáxia 1 (base0, base1, base2 de Text Elite). */
        const val BASE0 = 0x5A4A
        const val BASE1 = 0x0248
        const val BASE2 = 0xB753

        fun base(): Seed = Seed(BASE0, BASE1, BASE2)

        /** Rotaciona um número de 8 bits 1 bit para a esquerda (`rotatel`). */
        internal fun rotateLeft8(x: Int): Int {
            val temp = x and 0x80
            return (2 * (x and 0x7F)) + (temp ushr 7)
        }

        /** Aplica `rotatel` independentemente nos dois bytes de uma palavra (`twist`). */
        internal fun twist(x: Int): Int =
            (256 * rotateLeft8(x ushr 8)) + rotateLeft8(x and 0xFF)
    }
}
