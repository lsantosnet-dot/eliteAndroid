package com.eliteandroid.save

/**
 * Arquivo do comandante, no espírito do original (bloco NA%, gravado só quando atracado):
 * sistema atual e destino, dinheiro, combustível, porão, capacidade, mísseis, status legal
 * (FIST), abates (TALLY), o mercado da visita (QQ26 + oferta AVL) e os equipamentos
 * (lasers por vista e os itens da loja).
 *
 * Formato texto "chave=valor" por linha, versionado, fácil de inspecionar. A versão 1
 * (sem equipamentos) ainda é lida, com o equipamento de fábrica.
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
    val lasers: IntArray = intArrayOf(15, 0, 0, 0),
    val ecm: Boolean = false,
    val fuelScoops: Boolean = false,
    val escapePod: Boolean = false,
    val energyBomb: Boolean = false,
    val energyUnit: Boolean = false,
    val dockingComputer: Boolean = false,
    val galacticHyperdrive: Boolean = false,
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
        appendLine("lasers=${lasers.joinToString(",")}")
        appendLine("ecm=${ecm.bit}")
        appendLine("scoops=${fuelScoops.bit}")
        appendLine("pod=${escapePod.bit}")
        appendLine("bomb=${energyBomb.bit}")
        appendLine("energyUnit=${energyUnit.bit}")
        appendLine("dock=${dockingComputer.bit}")
        appendLine("ghyp=${galacticHyperdrive.bit}")
    }

    private val Boolean.bit get() = if (this) 1 else 0

    override fun equals(other: Any?) = other is CommanderSave && encode() == other.encode()
    override fun hashCode() = encode().hashCode()

    companion object {
        const val VERSION = 2

        /** Lê um arquivo salvo; null se estiver ausente, corrompido ou de versão desconhecida. */
        fun decode(text: String?): CommanderSave? {
            if (text.isNullOrBlank()) return null
            return try {
                val m = text.lines().filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                val version = m["version"]?.toInt()
                if (version == null || version !in 1..VERSION) return null
                fun ints(k: String) = m.getValue(k).split(',').map { it.trim().toInt() }.toIntArray()
                fun flag(k: String) = (m[k]?.toInt() ?: 0) != 0
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
                    lasers = if (version < 2) intArrayOf(15, 0, 0, 0)
                    else ints("lasers").also { require(it.size == 4 && it.all { l -> l in 0..255 }) },
                    ecm = flag("ecm"),
                    fuelScoops = flag("scoops"),
                    escapePod = flag("pod"),
                    energyBomb = flag("bomb"),
                    energyUnit = flag("energyUnit"),
                    dockingComputer = flag("dock"),
                    galacticHyperdrive = flag("ghyp"),
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
