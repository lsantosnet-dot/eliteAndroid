package com.eliteandroid.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.render.NeonLineBatch
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.WireframeProjector
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Ajuda de navegação (não existe no original, onde só há a bússola do painel): marca o
 * alvo da bússola — a estação, ou o planeta enquanto ela não surge — com colchetes quando
 * ele está na vista, ou com uma seta na borda da tela apontando para onde virar quando
 * está fora dela (ou atrás). Ao surgir, a estação pisca por alguns segundos.
 */
class NavMarker {

    private val tmp = Vector3()
    private val layout = GlyphLayout()

    /** Resultado do último quadro, reaproveitado pelo texto. */
    private var visible = false
    private var onScreen = false
    private var mx = 0f
    private var my = 0f
    private var size = 0f
    private var dirX = 0f
    private var dirY = 0f
    private var label = ""
    private var color = NeonPalette.STATION

    /** Segundos desde que a estação surgiu (para o destaque inicial). */
    private var stationAge = 99f
    private var hadStation = false

    /** Novo voo (decolagem, chegada): a estação que já existe não pisca. */
    fun reset(model: FlightModel) {
        hadStation = model.station != null
        stationAge = 99f
    }

    fun update(delta: Float, model: FlightModel) {
        val has = model.station != null
        if (has && !hadStation) stationAge = 0f
        hadStation = has
        stationAge += delta
    }

    /** O destaque de "estação à vista" que pisca logo depois de ela surgir. */
    val stationJustAppeared: Boolean get() = hadStation && stationAge < 4f

    /** Desenha o marcador numa vista de [w]×[h], acima do painel (topo em [panelTop]). */
    fun drawLines(lines: NeonLineBatch, projector: WireframeProjector, model: FlightModel, w: Float, h: Float, panelTop: Float) {
        visible = false
        val target = model.compassTarget ?: return
        val isStation = target === model.station
        // Pisca nos primeiros segundos depois de a estação surgir.
        if (isStation && stationJustAppeared && ((stationAge * 4f).toInt() % 2 == 1)) return
        color = if (isStation) NeonPalette.STATION else NeonPalette.PLANET
        label = if (isStation) "STATION" else "PLANET"

        val m = h * 0.07f
        val left = m; val right = w - m
        val bottom = panelTop + m; val top = h - m * 1.6f
        val cx = (left + right) / 2f; val cy = (bottom + top) / 2f

        projector.view.toView(target.position.x, target.position.y, target.position.z, tmp)
        visible = true
        if (tmp.z > 0f) {
            val sx = w / 2f + tmp.x / tmp.z * projector.focal
            val sy = h / 2f + tmp.y / tmp.z * projector.focal
            if (sx in left..right && sy in bottom..top) {
                onScreen = true
                mx = sx; my = sy
                size = h * (if (isStation) 0.045f else 0.03f)
                brackets(lines, sx, sy, size)
                return
            }
        }
        // Fora da vista: seta na borda, na direção do alvo (atrás = para onde virar).
        onScreen = false
        if (tmp.z <= 0f) label += " (BEHIND)"
        var dx = tmp.x; var dy = tmp.y
        if (tmp.z <= 0f && abs(dx) < 1e-3f && abs(dy) < 1e-3f) dy = -1f
        val len = hypot(dx, dy)
        dx /= len; dy /= len
        dirX = dx; dirY = dy
        val tx = if (dx > 0f) (right - cx) / dx else if (dx < 0f) (left - cx) / dx else Float.MAX_VALUE
        val ty = if (dy > 0f) (top - cy) / dy else if (dy < 0f) (bottom - cy) / dy else Float.MAX_VALUE
        val t = min(tx, ty)
        mx = cx + dx * t; my = cy + dy * t
        size = h * 0.035f
        arrow(lines, mx, my, dx, dy, size)
    }

    /** O nome do alvo junto ao marcador. */
    fun drawText(batch: SpriteBatch, font: BitmapFont, w: Float) {
        if (!visible) return
        val c = Color(); Color.abgr8888ToColor(c, color)
        font.color = c
        layout.setText(font, label)
        val x: Float; val y: Float
        if (onScreen) {
            x = mx - layout.width / 2f
            y = my - size * 1.25f
        } else if (abs(dirY) > abs(dirX)) {
            // Seta no alto ou embaixo: o nome ao lado dela.
            x = (mx + size * 1.4f).coerceAtMost(w - layout.width - 4f)
            y = my - dirY * size * 0.7f + layout.height / 2f
        } else {
            // Seta numa lateral: o nome logo abaixo, para dentro da tela.
            x = (mx - dirX * size * 0.7f - layout.width / 2f).coerceIn(4f, w - layout.width - 4f)
            y = my - size * 1.4f
        }
        font.draw(batch, label, x, y)
    }

    private fun brackets(lines: NeonLineBatch, x: Float, y: Float, s: Float) {
        val k = s * 0.45f
        for (sxSign in intArrayOf(-1, 1)) for (sySign in intArrayOf(-1, 1)) {
            val cx = x + sxSign * s; val cy = y + sySign * s
            lines.segment(cx, cy, cx - sxSign * k, cy, color)
            lines.segment(cx, cy, cx, cy - sySign * k, color)
        }
    }

    private fun arrow(lines: NeonLineBatch, x: Float, y: Float, dx: Float, dy: Float, s: Float) {
        // Ponta na borda, base para dentro.
        val bx = x - dx * s * 1.4f; val by = y - dy * s * 1.4f
        val px = -dy * s * 0.8f; val py = dx * s * 0.8f
        lines.segment(x, y, bx + px, by + py, color)
        lines.segment(x, y, bx - px, by - py, color)
        lines.segment(bx + px, by + py, bx - px, by - py, color)
        // Uma segunda ponta menor atrás, como um ">>".
        val x2 = x - dx * s * 0.9f; val y2 = y - dy * s * 0.9f
        val b2x = x2 - dx * s * 1.0f; val b2y = y2 - dy * s * 1.0f
        lines.segment(x2, y2, b2x + px * 0.7f, b2y + py * 0.7f, color)
        lines.segment(x2, y2, b2x - px * 0.7f, b2y - py * 0.7f, color)
    }
}
