package com.eliteandroid.save

/**
 * Arquivo do comandante, no espírito do original (bloco NA%, gravado só quando atracado):
 * sistema atual e destino, dinheiro, combustível, porão, capacidade, mísseis, status legal
 * (FIST), abates (TALLY) e o mercado da visita (QQ26 + oferta AVL).
 *
 * Formato texto "chave=valor" por linha, versionado, fácil de inspecionar.
 */
data class CommanderSave(
    val galaxy: Int,
    val system: Int,
    val target: Int?,
    val cash: Long,
    val fuel: Float,
    val cargo: IntArray,
    val cargoCapacity: Int,
    val missiles: Int,
    val legalStatus: Int,
    val kills: Int,
    val marketFluct: Int,
    val marketAvailability: IntArray,
) {
    fun encode(): String = buildString {
        appendLine("version=$VERSION")
        appendLine("galaxy=$galaxy")
        appendLine("system=$system")
        appendLine("target=${target ?: -1}")
        appendLine("cash=$cash")
        appendLine("fuel=$fuel")
        appendLine("cargo=${cargo.joinToString(",")}")
        appendLine("cargoCapacity=$cargoCapacity")
        appendLine("missiles=$missiles")
        appendLine("legal=$legalStatus")
        appendLine("kills=$kills")
        appendLine("fluct=$marketFluct")
        appendLine("avl=${marketAvailability.joinToString(",")}")
    }

    override fun equals(other: Any?) = other is CommanderSave && encode() == other.encode()
    override fun hashCode() = encode().hashCode()

    companion object {
        const val VERSION = 1

        /** Lê um arquivo salvo; null se estiver ausente, corrompido ou de versão desconhecida. */
        fun decode(text: String?): CommanderSave? {
            if (text.isNullOrBlank()) return null
            return try {
                val m = text.lines().filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                if (m["version"]?.toInt() != VERSION) return null
                fun ints(k: String) = m.getValue(k).split(',').map { it.trim().toInt() }.toIntArray()
                CommanderSave(
                    galaxy = m.getValue("galaxy").toInt().also { require(it in 1..8) },
                    system = m.getValue("system").toInt().also { require(it in 0..255) },
                    target = m.getValue("target").toInt().takeIf { it in 0..255 },
                    cash = m.getValue("cash").toLong().also { require(it >= 0) },
                    fuel = m.getValue("fuel").toFloat().coerceIn(0f, 70f),
                    cargo = ints("cargo").also { require(it.size == 17 && it.all { n -> n >= 0 }) },
                    cargoCapacity = m.getValue("cargoCapacity").toInt(),
                    missiles = m.getValue("missiles").toInt().coerceIn(0, 4),
                    legalStatus = m.getValue("legal").toInt().coerceIn(0, 255),
                    kills = m.getValue("kills").toInt().coerceAtLeast(0),
                    marketFluct = m.getValue("fluct").toInt() and 0xFF,
                    marketAvailability = ints("avl").also { require(it.size == 17) },
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Onde o arquivo do comandante é guardado (Preferences do LibGDX no jogo; memória nos testes). */
interface SaveStorage {
    fun read(): String?
    fun write(text: String)
    fun clear()
}

class MemoryStorage(var text: String? = null) : SaveStorage {
    override fun read() = text
    override fun write(text: String) { this.text = text }
    override fun clear() { text = null }
}
