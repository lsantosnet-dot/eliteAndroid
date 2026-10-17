package com.eliteandroid.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Túnel de lançamento/atracação (LAUN → HFS2): para r0 = 8, 9, …, 15, desenha
 * circunferências de raio r0, 2·r0, 4·r0… até passar de 160 (numa vista de 256 px).
 * Cada r0 é um quadro, o que faz os anéis parecerem correr na nossa direção.
 */
object LaunchTunnel {

    const val FRAMES = 8
    const val DURATION = 1.2f
    private const val SEGMENTS = 48

    /** Desenha o quadro correspondente a [progress] (0..1). */
    fun draw(progress: Float, width: Float, height: Float, batch: NeonLineBatch, color: Float) {
        val frame = (progress * FRAMES).toInt().coerceIn(0, FRAMES - 1)
        val scale = width / 256f
        val cx = width / 2f; val cy = height / 2f
        var r = (8 + frame).toFloat()
        while (r <= 160f) {
            circle(cx, cy, r * scale, batch, color)
            r *= 2f
        }
    }

    private fun circle(cx: Float, cy: Float, r: Float, batch: NeonLineBatch, color: Float) {
        var px = cx + r; var py = cy
        for (i in 1..SEGMENTS) {
            val a = i * (2f * Math.PI.toFloat() / SEGMENTS)
            val x = cx + r * cos(a); val y = cy + r * sin(a)
            batch.segment(px, py, x, y, color)
            px = x; py = y
        }
    }
}
