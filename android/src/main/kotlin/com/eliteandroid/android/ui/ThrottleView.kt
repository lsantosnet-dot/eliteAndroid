package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Acelerador deslizante vertical: a posição do dedo define a velocidade desejada (1..40,
 * DELTA do original); a nave acelera/desacelera 1 por ciclo até ela, como as teclas
 * Espaço e "?". Mostra também a velocidade real.
 */
@SuppressLint("ViewConstructor")
class ThrottleView(
    context: Context,
    private val onTarget: (speed: Float) -> Unit,
) : View(context) {

    private val style = NeonStyle(context)
    private val track = RectF()

    /** Velocidade atual (lida do jogo). */
    var speed = 12f
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Alvo escolhido pelo jogador (NaN = ainda não tocou neste voo). */
    var target = Float.NaN
        set(v) { field = v; invalidate() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (track.height() <= 0f) return true
                val t = ((track.bottom - e.y) / track.height()).coerceIn(0f, 1f)
                target = MIN + t * (MAX - MIN)
                onTarget(target)
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = style.density
        track.set(w * 0.25f, 24f * d, w * 0.75f, h - 8f * d)
    }

    private fun yFor(v: Float) = track.bottom - (v - MIN) / (MAX - MIN) * track.height()

    override fun onDraw(canvas: Canvas) {
        val d = style.density
        style.label(canvas, "SPD", width / 2f, 10f * d, NeonStyle.CYAN, 11f)
        style.rect(canvas, track, NeonStyle.DIM, 1.5f * d)

        // Barra da velocidade real (amarela; vermelha a partir de 28, como o SP do painel).
        val color = if (speed >= 28f) NeonStyle.RED else NeonStyle.YELLOW
        style.fill.color = color
        style.fill.alpha = 90
        canvas.drawRoundRect(track.left + 3 * d, yFor(speed), track.right - 3 * d, track.bottom - 3 * d, 4 * d, 4 * d, style.fill)

        if (!target.isNaN()) {
            val y = yFor(target)
            style.line(canvas, track.left - 6 * d, y, track.right + 6 * d, y, NeonStyle.GREEN, 3f * d)
        }
        style.label(canvas, speed.toInt().toString(), width / 2f, track.top + 14f * d, NeonStyle.YELLOW, 12f)
    }

    private companion object {
        const val MIN = 1f
        const val MAX = 40f
    }
}
