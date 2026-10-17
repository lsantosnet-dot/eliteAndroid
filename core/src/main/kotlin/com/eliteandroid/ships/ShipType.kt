package com.eliteandroid.ships

import com.badlogic.gdx.graphics.Color

/**
 * Tipos de nave da versão cassete (tabela XX21; o número é o tipo do original).
 * Pirata ou caçador de recompensas é decidido pelo papel no surgimento, não pelo tipo.
 */
enum class ShipType(val id: Int, val blueprint: ShipBlueprint, rgb: Int) {
    SIDEWINDER(1, OriginalShips.SIDEWINDER, 0xFF3070),
    VIPER(2, OriginalShips.VIPER, 0x4C8CFF),          // COPS: polícia
    MAMBA(3, OriginalShips.MAMBA, 0xFF4040),
    PYTHON(4, OriginalShips.PYTHON, 0xFF7A30),
    THARGOID(6, OriginalShips.THARGOID, 0x9CFF30),    // THG
    COBRA_HUNTER(5, Blueprints.COBRA_MK3, 0xFFB030),  // caçador de recompensas
    COBRA_TRADER(7, Blueprints.COBRA_MK3, 0x40FF90),  // CYL: comerciante
    STATION(8, Blueprints.CORIOLIS, 0x1AD9FF),        // SST
    MISSILE(9, OriginalShips.MISSILE, 0xFFF060),      // MSL
    ASTEROID(10, OriginalShips.ASTEROID, 0x9A9AB0),   // AST
    CANISTER(11, OriginalShips.CANISTER, 0xFFE080),   // OIL
    THARGON(12, OriginalShips.THARGON, 0x70D040),     // TGL
    ESCAPE_POD(13, OriginalShips.ESCAPE_POD, 0xC0C0FF); // ESC

    /** Cor neon (empacotada) usada no wireframe e no radar. */
    val color: Float = Color(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f, 1f).toFloatBits()

    /** Tipos abaixo de CYL com IA são piratas/caçadores (TACTICS os acalma na zona segura). */
    val isPirateClass: Boolean get() = id < COBRA_TRADER.id && this != VIPER
}
