package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Botão neon redondo ou retangular. [onPress]/[onRelease] permitem ações "segurar"
 * (como o FIRE); [onClick] dispara no toque.
 */
@SuppressLint("ViewConstructor")
class NeonButton(
    context: Context,
    var label: String,
    var color: Int,
    private val round: Boolean,
    private val textSizeDp: Float = 14f,
    private val onPress: () -> Unit = {},
    private val onRelease: () -> Unit = {},
    private val onClick: () -> Unit = {},
) : View(context) {

    private val style = NeonStyle(context)
    private val box = RectF()
    private var pressed = false

    /** Destaque permanente (vista selecionada, contagem em andamento). */
    var lit = false
        set(v) { if (field != v) { field = v; invalidate() } }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPress(); onClick()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed = false
                onRelease()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val d = style.density
        val active = pressed || lit
        val c = if (active) color else NeonStyle.DIM
        if (round) {
            val r = minOf(width, height) / 2f - 6f * d
            if (active) { style.fill.color = color; style.fill.alpha = 60; canvas.drawCircle(width / 2f, height / 2f, r, style.fill) }
            style.circle(canvas, width / 2f, height / 2f, r, c, 2.5f * d)
        } else {
            box.set(4 * d, 4 * d, width - 4 * d, height - 4 * d)
            if (active) { style.fill.color = color; style.fill.alpha = 60; canvas.drawRoundRect(box, 6 * d, 6 * d, style.fill) }
            style.rect(canvas, box, c)
        }
        style.label(canvas, label, width / 2f, height / 2f, if (active) color else NeonStyle.GREEN, textSizeDp)
    }
}
