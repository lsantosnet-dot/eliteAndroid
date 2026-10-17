package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.eliteandroid.GameSession
import java.util.Locale

/**
 * Mapas estelares do original, em neon:
 *
 *  - Curto alcance (TT23): sistemas com |dx| < 20 e |dy| < 38 em torno do atual, em
 *    x = 104 + 4·dx e y = 90 + 2·dy; estrelas de raio 2 + bit 0 de s2_hi; nomes ao lado.
 *    Nessa escala o círculo de combustível tem raio = combustível em décimos de LY.
 *  - Galáctico (TT22): os 256 sistemas em (x, y/2); círculo de combustível com raio /4.
 *
 * Tocar escolhe o sistema mais próximo do toque como destino do hiperespaço.
 */
@SuppressLint("ViewConstructor")
class ChartView(context: Context, private val session: () -> GameSession?) : View(context) {

    var longRange = false
        set(v) { field = v; invalidate() }

    private val style = NeonStyle(context)
    private val area = RectF()
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val names = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    // Janela virtual do mapa (coordenadas do original) e escala para a tela.
    private val vw get() = if (longRange) 256f else 208f
    private val vh get() = if (longRange) 128f else 168f
    private var scale = 1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 8f * style.density
        area.set(pad, pad + 26f * style.density, w - pad, h - pad - 30f * style.density)
    }

    private fun layoutArea() {
        scale = minOf(area.width() / vw, area.height() / vh)
    }

    private fun sx(vx: Float) = area.centerX() + (vx - vw / 2f) * scale
    private fun sy(vy: Float) = area.centerY() + (vy - vh / 2f) * scale

    /** Coordenadas virtuais de um sistema (relativas ao atual no mapa curto). */
    private fun virtualX(x: Int, hereX: Int) = if (longRange) x.toFloat() else 104f + (x - hereX) * 4f
    private fun virtualY(y: Int, hereY: Int) = if (longRange) y / 2f else 84f + (y - hereY) * 2f

    override fun onDraw(canvas: Canvas) {
        val sess = session() ?: return
        layoutArea()
        // No witchspace, o centro é o ponto onde caímos (entre os sistemas).
        val hereX = sess.positionX; val hereY = sess.positionY
        val target = sess.hyperspaceTarget
        val fuel = sess.model.player.fuel
        val d = style.density

        style.label(canvas, if (longRange) "GALACTIC CHART ${sess.galaxy.number}" else "SHORT RANGE CHART",
            width / 2f, 14f * d, NeonStyle.CYAN, 14f)
        style.rect(canvas, area, NeonStyle.DIM, 1.5f * d, 2f * d)

        val hx = sx(virtualX(hereX, hereX)); val hy = sy(virtualY(hereY, hereY))
        // Círculo de combustível (TT14) e mira no sistema atual.
        val fuelR = (if (longRange) fuel / 4f else fuel) * scale
        style.circle(canvas, hx, hy, fuelR, NeonStyle.GREEN, 1.5f * d)
        cross(canvas, hx, hy, 10f * d, NeonStyle.GREEN)

        names.textSize = 11f * d
        canvas.save()
        canvas.clipRect(area)
        for (s in sess.galaxy.systems) {
            if (!longRange && (kotlin.math.abs(s.x - hereX) >= 20 || kotlin.math.abs(s.y - hereY) >= 38)) continue
            val x = sx(virtualX(s.x, hereX)); val y = sy(virtualY(s.y, hereY))
            val inRange = sess.distanceTo(s) <= fuel
            star.color = if (inRange) NeonStyle.YELLOW else NeonStyle.DIM
            val r = if (longRange) 1.2f * d else (2 + (s.seed.s2Hi and 1)) * scale * 0.9f
            canvas.drawCircle(x, y, r, star)
            if (!longRange) {
                names.color = if (inRange) NeonStyle.YELLOW else NeonStyle.DIM
                canvas.drawText(s.displayName, x + r + 4f * d, y + names.textSize * 0.35f, names)
            }
        }
        canvas.restore()

        // Mira do destino (TT103).
        if (target != null) {
            val tx = sx(virtualX(target.x, hereX)); val ty = sy(virtualY(target.y, hereY))
            cross(canvas, tx, ty, 14f * d, NeonStyle.MAGENTA)
            style.circle(canvas, tx, ty, 7f * d, NeonStyle.MAGENTA, 1.5f * d)
            val dist = sess.distanceTo(target)
            val txt = String.format(Locale.US, "%s   %.1f LIGHT YEARS%s", target.name, dist / 10f,
                if (dist > fuel) "   (OUT OF RANGE)" else "")
            style.label(canvas, txt, width / 2f, height - 14f * d, if (dist > fuel) NeonStyle.RED else NeonStyle.MAGENTA, 13f)
        } else {
            style.label(canvas, "TAP A SYSTEM TO SET THE HYPERSPACE TARGET", width / 2f, height - 14f * d, NeonStyle.DIM, 12f)
        }
    }

    private fun cross(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        style.line(canvas, x - r, y, x + r, y, color, 1.5f * style.density)
        style.line(canvas, x, y - r, x, y + r, color, 1.5f * style.density)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN && e.actionMasked != MotionEvent.ACTION_MOVE) return true
        val sess = session() ?: return true
        layoutArea()
        val vx = (e.x - area.centerX()) / scale + vw / 2f
        val vy = (e.y - area.centerY()) / scale + vh / 2f
        val gx: Int; val gy: Int
        if (longRange) { gx = vx.toInt(); gy = (vy * 2f).toInt() }
        else { gx = sess.positionX + ((vx - 104f) / 4f).toInt(); gy = sess.positionY + ((vy - 84f) / 2f).toInt() }
        val chosen = sess.galaxy.nearestTo(gx.coerceIn(0, 255), gy.coerceIn(0, 255))
        sess.hyperspaceTarget = if (sess.isHere(chosen)) null else chosen
        invalidate()
        return true
    }
}
