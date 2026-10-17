package com.eliteandroid.render

import com.badlogic.gdx.graphics.Color

/** Paleta retro-futurista. Cores empacotadas (floatBits) prontas para o [NeonLineBatch]. */
object NeonPalette {
    val STATION: Float = Color(0.10f, 0.85f, 1.00f, 1f).toFloatBits()  // ciano
    val PLAYER_SHIP: Float = Color(0.25f, 1.00f, 0.35f, 1f).toFloatBits() // verde fósforo
    val HOSTILE: Float = Color(1.00f, 0.20f, 0.55f, 1f).toFloatBits()    // magenta
    val HUD_DIM: Float = Color(0.20f, 0.55f, 0.30f, 0.6f).toFloatBits()
    val DUST: Float = Color(0.75f, 0.80f, 1.00f, 0.9f).toFloatBits()
}
