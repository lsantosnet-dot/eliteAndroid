package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Manche virtual flutuante (rolagem/arfagem): o anel aparece onde o polegar toca e o
 * deslocamento do botão central vira JSTX/JSTY em −1..1, como o joystick analógico do
 * original. Puxar para baixo levanta o nariz (convenção de aviação; [invertPitch] inverte).
 */
@SuppressLint("ViewConstructor")
class JoystickView(
    context: Context,
    private val onMove: (x: Float, y: Float) -> Unit,
    private val onRelease: () -> Unit,
) : View(context) {

    private val style = NeonStyle(context)
    var invertPitch = false

    private var pointerId = -1
    private var originX = 0f
    private var originY = 0f
    private var knobX = 0f
    private var knobY = 0f

    private val radius: Float get() = 62f * style.density
    private val deadZone = 0.06f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId != -1) return true
                val i = e.actionIndex
                pointerId = e.getPointerId(i)
                // O anel inteiro precisa caber na área do manche.
                originX = e.getX(i).coerceIn(radius, width - radius)
                originY = e.getY(i).coerceIn(radius, height - radius)
                update(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) update(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && e.getPointerId(e.actionIndex) != pointerId) return true
                pointerId = -1
                onRelease()
                invalidate()
            }
        }
        return true
    }

    private fun update(x: Float, y: Float) {
        var dx = (x - originX) / radius
        var dy = (y - originY) / radius
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        knobX = originX + dx * radius
        knobY = originY + dy * radius
        val ox = if (kotlin.math.abs(dx) < deadZone) 0f else dx
        val oy = if (kotlin.math.abs(dy) < deadZone) 0f else dy
        onMove(ox, if (invertPitch) -oy else oy)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (pointerId == -1) {
            // Posição de repouso: anel apagado no canto, só como indicação.
            val x = radius * 1.4f; val y = height - radius * 1.4f
            style.circle(canvas, x, y, radius, NeonStyle.DIM)
            style.label(canvas, "ROLL / PITCH", x, y, NeonStyle.DIM, 10f)
            return
        }
        style.circle(canvas, originX, originY, radius, NeonStyle.GREEN)
        style.line(canvas, originX - radius, originY, originX + radius, originY, NeonStyle.DIM, style.density)
        style.line(canvas, originX, originY - radius, originX, originY + radius, NeonStyle.DIM, style.density)
        style.circle(canvas, knobX, knobY, radius * 0.32f, NeonStyle.GREEN, 3f * style.density)
    }
}
