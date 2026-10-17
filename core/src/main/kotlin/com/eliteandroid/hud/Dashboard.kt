package com.eliteandroid.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.render.NeonLineBatch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painel do cockpit (rotinas DIALS, SCAN e COMPAS), no layout do BBC Elite:
 *
 *   FS AS FV CT LT AL  |   radar 3D "prato"   (bússola)  |  SP RL DC 1 2 3 4
 *
 * Tudo é desenhado num sistema virtual de 256 × 64 (x = coluna do BBC, y = linha 192..255,
 * crescendo para baixo) e escalado para a tela, centralizado embaixo — os lados ficam livres
 * para os controles touch.
 *
 * Barras: 16 passos (DILX): FS/AS = escudo/16, FV = combustível/4, CT/LT = temperatura/16,
 * AL = altitude/16, SP = velocidade/2 e os 4 bancos de energia = ENERGY/4 repartido em
 * blocos de 16 (o de baixo enche primeiro; o de cima tem no máximo 15). Cores como em PZW:
 * valores perigosos piscam (8 de cada 16 ciclos do loop principal).
 */
class Dashboard {

    /** Escala virtual → pixels e canto inferior esquerdo do painel na tela. */
    var scale = 1f; private set
    private var left = 0f
    private var bottom = 0f
    val bounds = Rectangle()

    private val blip = Scanner.Blip()
    private val tmp = Vector3()
    private val glyphs = GlyphLayout()

    fun layout(screenW: Float, screenH: Float) {
        scale = minOf(screenH * 0.30f / 64f, screenW * 0.62f / 256f)
        left = (screenW - 256f * scale) / 2f
        bottom = screenH * 0.01f
        bounds.set(left, bottom, 256f * scale, 64f * scale)
    }

    private fun sx(vx: Float) = left + vx * scale
    private fun sy(vy: Float) = bottom + (256f - vy) * scale

    private fun seg(lines: NeonLineBatch, x0: Float, y0: Float, x1: Float, y1: Float, c: Float, hw: Float = 0f) =
        if (hw > 0f) lines.segment(sx(x0), sy(y0), sx(x1), sy(y1), c, hw * scale)
        else lines.segment(sx(x0), sy(y0), sx(x1), sy(y1), c)

    // ---------------------------------------------------------------- linhas

    fun drawLines(lines: NeonLineBatch, model: FlightModel) {
        val p = model.player
        val flash = (p.mainLoopCounter.toInt() and 8) != 0
        drawFrame(lines)
        drawScanner(lines, model)
        drawCompass(lines, model)

        // Lado esquerdo: FS AS FV CT LT AL.
        bar(lines, LEFT_BAR_X, 0, (p.foreShield / 16f), lowIsDanger = true, threshold = 3, flash = flash)
        bar(lines, LEFT_BAR_X, 1, (p.aftShield / 16f), lowIsDanger = true, threshold = 3, flash = flash)
        bar(lines, LEFT_BAR_X, 2, (p.fuel / 4f), lowIsDanger = true, threshold = 3, flash = flash)
        bar(lines, LEFT_BAR_X, 3, (p.cabinTemp / 16f), lowIsDanger = false, threshold = 11, flash = flash)
        bar(lines, LEFT_BAR_X, 4, (p.laserTemp / 16f), lowIsDanger = false, threshold = 11, flash = flash)
        bar(lines, LEFT_BAR_X, 5, (p.altitude / 16f), lowIsDanger = false, threshold = 99, flash = flash)
        drawMissiles(lines, model)

        // Lado direito: SP RL DC e bancos de energia 1..4.
        bar(lines, RIGHT_BAR_X, 0, (p.speed / 2f), lowIsDanger = false, threshold = 14, flash = flash)
        marker(lines, 1, rollIndicator(p))
        marker(lines, 2, pitchIndicator(p))
        energyBanks(p.energy).forEachIndexed { i, v ->
            bar(lines, RIGHT_BAR_X, 3 + i, v.toFloat(), lowIsDanger = true, threshold = 3, flash = flash)
        }

        // Bulbos: S (estação por perto, SPBLB) e E (E.C.M. ativo, ECBLB). O "S" fica
        // aceso (preenchido) para ser visto no celular.
        stationLit = model.station != null
        if (stationLit) { bulb(lines, BULB_S_X, PANEL_COLOR); bulb(lines, BULB_S_X, PANEL_COLOR, 4f) }
        if (model.combat.ecmActive) bulb(lines, BULB_E_X, PANEL_COLOR)
    }

    private fun drawFrame(lines: NeonLineBatch) {
        val c = FRAME_COLOR
        seg(lines, 0f, 192f, 256f, 192f, c); seg(lines, 256f, 192f, 256f, 255f, c)
        seg(lines, 256f, 255f, 0f, 255f, c); seg(lines, 0f, 255f, 0f, 192f, c)
        seg(lines, SCANNER_LEFT, 192f, SCANNER_LEFT, 255f, c)
        seg(lines, SCANNER_RIGHT, 192f, SCANNER_RIGHT, 255f, c)
    }

    /** Uma barra horizontal de 16 passos (2 colunas por passo). */
    private fun bar(
        lines: NeonLineBatch, x0: Float, row: Int, value: Float,
        lowIsDanger: Boolean, threshold: Int, flash: Boolean,
    ) {
        val steps = value.toInt().coerceIn(0, 16)
        val danger = if (lowIsDanger) steps < threshold else steps >= threshold
        val c = if (danger && flash) DANGER_COLOR else PANEL_COLOR
        val y = rowY(row)
        if (steps == 0) return
        seg(lines, x0, y, x0 + steps * 2f, y, c, hw = 1.6f)
    }

    /** Indicadores RL/DC: um traço vertical na posição 0..15 (8 = centro). */
    private fun marker(lines: NeonLineBatch, row: Int, pos: Int) {
        val x = RIGHT_BAR_X + pos.coerceIn(0, 15) * 2f + 1f
        val y = rowY(row)
        seg(lines, x, y - 2.6f, x, y + 2.6f, PANEL_COLOR, hw = 0.8f)
        seg(lines, RIGHT_BAR_X + 16f, y - 3.4f, RIGHT_BAR_X + 16f, y - 2.6f, FRAME_COLOR) // marca central
    }

    /**
     * MSBAR: um bloco por míssil; o da direita (o próximo a ser lançado) fica amarelo quando
     * armado procurando alvo e vermelho quando travado.
     */
    private fun drawMissiles(lines: NeonLineBatch, model: FlightModel) {
        val p = model.player
        val y = rowY(6) + 1.5f
        val c = model.combat
        val activeColor = when {
            c.missileTarget != null -> DANGER_COLOR
            c.missileArmed -> PANEL_COLOR
            else -> MISSILE_COLOR
        }
        for (i in 0 until 4) {
            val x = LEFT_BAR_X + 2f + i * 8f
            if (i < p.missiles) seg(lines, x, y, x + 3f, y, if (i == p.missiles - 1) activeColor else MISSILE_COLOR, hw = 1.6f)
            else seg(lines, x, y, x + 3f, y, FRAME_COLOR, hw = 0.4f)
        }
    }

    /** O "S" aceso: o texto é escrito por cima do bulbo preenchido. */
    private var stationLit = false

    private fun bulb(lines: NeonLineBatch, x: Float, c: Float, r: Float = 3f) {
        val y = 249f
        seg(lines, x - r, y - r, x + r, y - r, c); seg(lines, x + r, y - r, x + r, y + r, c)
        seg(lines, x + r, y + r, x - r, y + r, c); seg(lines, x - r, y + r, x - r, y - r, c)
    }

    /** Radar: prato elíptico com retícula, hastes e pontos (SCAN). */
    private fun drawScanner(lines: NeonLineBatch, model: FlightModel) {
        val cx = Scanner.CENTRE_X; val cy = Scanner.CENTRE_Y
        ellipse(lines, cx, cy, 66f, 17f, SCANNER_COLOR)
        ellipse(lines, cx, cy, 33f, 8.5f, SCANNER_GRID)
        seg(lines, cx - 66f, cy, cx + 66f, cy, SCANNER_GRID)
        seg(lines, cx, cy - 17f, cx, cy + 17f, SCANNER_GRID)
        // Cone de visão frontal (o "V" do painel original).
        seg(lines, cx, cy, cx - 47f, cy - 12f, SCANNER_GRID)
        seg(lines, cx, cy, cx + 47f, cy - 12f, SCANNER_GRID)

        for (ship in model.ships) {
            if (!Scanner.project(ship.position, blip)) continue
            // Haste vertical do prato até o ponto, e o ponto em altura dupla.
            seg(lines, blip.x, blip.baseY, blip.x, blip.dotY, ship.color, hw = 0.6f)
            seg(lines, blip.x - 1.5f, blip.dotY, blip.x + 1.5f, blip.dotY, ship.color, hw = 1.6f)
        }
    }

    /**
     * Bússola (COMPAS/SP2): aponta para a estação na zona segura, senão para o planeta;
     * amarela se à frente, verde se atrás.
     */
    private fun drawCompass(lines: NeonLineBatch, model: FlightModel) {
        ellipse(lines, COMPASS_X, COMPASS_Y, 11f, 11f, FRAME_COLOR)
        val target = model.compassTarget ?: return
        if (target.position.isZero) return
        tmp.set(target.position).nor().scl(96f) // XX15: vetor unitário com 1.0 = 96
        val dx = (abs(tmp.x).toInt() / 10).let { if (tmp.x < 0) -it else it }
        val dy = (abs(tmp.y).toInt() / 10).let { if (tmp.y < 0) -it else it }
        val c = if (tmp.z >= 0f) PANEL_COLOR else COMPASS_BEHIND
        val x = COMPASS_X + dx; val y = COMPASS_Y - dy
        seg(lines, x - 1f, y, x + 1f, y, c, hw = 1.2f)
    }

    private fun ellipse(lines: NeonLineBatch, cx: Float, cy: Float, a: Float, b: Float, c: Float) {
        val n = 48
        var px = cx + a; var py = cy
        for (i in 1..n) {
            val t = i * (2f * Math.PI.toFloat() / n)
            val x = cx + a * cos(t); val y = cy + b * sin(t)
            seg(lines, px, py, x, y, c)
            px = x; py = y
        }
    }

    // ---------------------------------------------------------------- texto

    fun drawText(batch: SpriteBatch, font: BitmapFont) {
        val old = font.data.scaleX
        font.data.setScale(1f)
        font.data.setScale(5.2f * scale / font.capHeight)
        font.color = LABEL_COLOR
        LEFT_LABELS.forEachIndexed { i, s -> label(batch, font, s, 3f, rowY(i)) }
        RIGHT_LABELS.forEachIndexed { i, s -> label(batch, font, s, SCANNER_RIGHT + 2.5f, rowY(i)) }
        if (stationLit) font.color = STATION_LABEL
        label(batch, font, "S", BULB_S_X - 1.8f, 249f)
        font.color = LABEL_COLOR
        label(batch, font, "E", BULB_E_X - 1.8f, 249f)
        font.data.setScale(old)
    }

    private fun label(batch: SpriteBatch, font: BitmapFont, s: String, vx: Float, vyCentre: Float) {
        glyphs.setText(font, s)
        font.draw(batch, s, sx(vx), sy(vyCentre) + glyphs.height / 2f)
    }

    companion object {
        const val SCANNER_LEFT = 48f
        const val SCANNER_RIGHT = 208f
        const val LEFT_BAR_X = 15f
        const val RIGHT_BAR_X = 223f
        const val COMPASS_X = 195f
        const val COMPASS_Y = 203f
        const val BULB_S_X = 186f
        const val BULB_E_X = 200f

        private val LEFT_LABELS = arrayOf("FS", "AS", "FV", "CT", "LT", "AL")
        private val RIGHT_LABELS = arrayOf("SP", "RL", "DC", "1", "2", "3", "4")

        private val PANEL_COLOR = Color(1f, 0.88f, 0.25f, 1f).toFloatBits()     // amarelo (cor 2 do painel)
        private val STATION_LABEL = Color(1f, 0.88f, 0.25f, 1f)                  // o "S" aceso, em amarelo
        private val DANGER_COLOR = Color(1f, 0.15f, 0.25f, 1f).toFloatBits()    // vermelho
        private val SCANNER_COLOR = Color(0.15f, 0.95f, 0.75f, 1f).toFloatBits()
        private val SCANNER_GRID = Color(0.08f, 0.40f, 0.34f, 0.55f).toFloatBits()
        private val COMPASS_BEHIND = Color(0.20f, 1f, 0.40f, 1f).toFloatBits()
        private val FRAME_COLOR = Color(0.20f, 0.45f, 0.65f, 0.8f).toFloatBits()
        private val MISSILE_COLOR = Color(0.25f, 1f, 0.35f, 1f).toFloatBits()
        private val LABEL_COLOR = Color(0.55f, 0.85f, 1f, 1f)

        /** Linha central (y virtual) da linha [row] de indicadores (8 px por linha). */
        fun rowY(row: Int) = 192f + 4.5f + row * 8.6f

        /** Bancos de energia (de cima para baixo) a partir de ENERGY (DIALS: ENERGY/4 em blocos de 16). */
        fun energyBanks(energy: Float): IntArray {
            var q = (energy.toInt().coerceIn(0, 255)) / 4
            val banks = IntArray(4)
            for (i in 3 downTo 0) {
                if (q >= 16) { banks[i] = 16; q -= 16 } else { banks[i] = q; q = 0 }
            }
            return banks
        }

        /** RL: 8 − ALP1/4 com o sinal da rolagem (α positivo = rolagem à esquerda → barra à esquerda). */
        fun rollIndicator(p: PlayerShip): Int {
            val alp1 = (abs(p.alpha) * 256f + 0.5f).toInt()
            val v = alp1 / 4
            return if (p.alpha > 0f) 8 - v else 8 + v
        }

        /** DC: 8 + β, com o deslocamento de 1 do original (posições 1..15). */
        fun pitchIndicator(p: PlayerShip): Int {
            val bet1 = (abs(p.beta) * 256f + 0.5f).toInt()
            return when {
                bet1 == 0 -> 8
                p.beta > 0f -> 7 + bet1
                else -> 9 - bet1
            }
        }
    }
}
