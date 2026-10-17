package com.eliteandroid.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Traços "neon": um halo largo e translúcido por baixo de uma linha fina e brilhante. */
internal class NeonStyle(context: Context) {

    val density = context.resources.displayMetrics.density

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun setup(color: Int, width: Float) {
        glow.color = color; glow.alpha = 70; glow.strokeWidth = width * 4f
        core.color = color; core.strokeWidth = width
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, width: Float = 2f * density) {
        setup(color, width)
        c.drawCircle(x, y, r, glow); c.drawCircle(x, y, r, core)
    }

    fun rect(c: Canvas, r: RectF, color: Int, width: Float = 2f * density, corner: Float = 6f * density) {
        setup(color, width)
        c.drawRoundRect(r, corner, corner, glow); c.drawRoundRect(r, corner, corner, core)
    }

    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float = 2f * density) {
        setup(color, width)
        c.drawLine(x0, y0, x1, y1, glow); c.drawLine(x0, y0, x1, y1, core)
    }

    fun label(c: Canvas, s: String, x: Float, y: Float, color: Int, sizeDp: Float) {
        text.color = color
        text.textSize = sizeDp * density
        // y é o centro vertical do texto.
        c.drawText(s, x, y - (text.descent() + text.ascent()) / 2f, text)
    }

    companion object {
        val GREEN = Color.rgb(64, 255, 110)
        val CYAN = Color.rgb(30, 220, 255)
        val YELLOW = Color.rgb(255, 225, 64)
        val RED = Color.rgb(255, 60, 80)
        val MAGENTA = Color.rgb(220, 90, 255)
        val DIM = Color.argb(150, 60, 160, 110)
        val PANEL = Color.argb(150, 0, 6, 14)
    }
}
