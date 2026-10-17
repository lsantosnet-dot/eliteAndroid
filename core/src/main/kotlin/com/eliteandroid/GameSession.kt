package com.eliteandroid

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.eliteandroid.flight.FlightEvent
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.flight.View
import com.eliteandroid.input.FlightInput
import com.eliteandroid.render.LaunchTunnel
import com.eliteandroid.render.NeonLineBatch
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.SpaceView
import java.util.Locale

/**
 * Ciclo de jogo da Etapa 3: atracado → túnel de lançamento → voo → (atracação → túnel →
 * atracado) ou (destruição → atracado). O mercado e as telas da estação chegam na Etapa 6.
 */
class GameSession(private val stationName: String) {

    enum class State { DOCKED, LAUNCHING, FLYING, DOCKING, DESTROYED }

    val model = FlightModel()
    val input = FlightInput()

    var state = State.DOCKED
        private set
    private var stateTime = 0f
    private var message: String? = null
    private var messageTime = 0f
    private val layout = GlyphLayout()

    fun update(delta: Float) {
        input.poll()
        stateTime += delta
        messageTime -= delta

        input.requestedView?.let { if (state == State.FLYING) model.view = it }
        input.requestedView = null

        when (state) {
            State.DOCKED -> if (input.launchRequested) enter(State.LAUNCHING)
            State.LAUNCHING -> if (stateTime >= LaunchTunnel.DURATION) {
                model.launch()
                input.throttleTarget = model.player.speed
                enter(State.FLYING)
            }
            State.FLYING -> when (model.update(delta, input.controls)) {
                FlightEvent.DOCKED -> enter(State.DOCKING)
                FlightEvent.CRASHED -> enter(State.DESTROYED)
                FlightEvent.BUMPED -> flash("BUMP")
                FlightEvent.NONE -> Unit
            }
            State.DOCKING -> if (stateTime >= LaunchTunnel.DURATION) enter(State.DOCKED)
            State.DESTROYED -> if (stateTime >= 3f) enter(State.DOCKED)
        }
        input.launchRequested = false
    }

    /** Pula direto para o voo (capturas de tela e testes). */
    fun launchNow() {
        model.launch()
        enter(State.FLYING)
    }

    private fun enter(s: State) {
        state = s
        stateTime = 0f
    }

    private fun flash(text: String) {
        message = text
        messageTime = 1f
    }

    fun render(view: SpaceView) {
        val flying = state == State.FLYING
        view.render(
            ships = if (flying) model.ships else emptyList(),
            dust = if (flying) model.dust else null,
            view = model.view,
            overlayLines = { lines -> drawLines(view, lines) },
            overlayText = { batch, font -> drawText(view, batch, font) },
        )
    }

    private fun drawLines(view: SpaceView, lines: NeonLineBatch) {
        val w = view.width; val h = view.height
        when (state) {
            State.LAUNCHING, State.DOCKING ->
                LaunchTunnel.draw(stateTime / LaunchTunnel.DURATION, w, h, lines, NeonPalette.STATION)
            State.FLYING -> {
                drawSight(w, h, lines)
                drawTouchControls(lines)
            }
            State.DOCKED -> rect(input.launchButton, lines, NeonPalette.PLAYER_SHIP)
            State.DESTROYED -> Unit
        }
    }

    /** SIGHT: mira do laser no centro da vista. */
    private fun drawSight(w: Float, h: Float, lines: NeonLineBatch) {
        val cx = w / 2f; val cy = h / 2f
        val a = h * 0.025f; val b = h * 0.075f
        val c = NeonPalette.PLAYER_SHIP
        lines.segment(cx - b, cy, cx - a, cy, c); lines.segment(cx + a, cy, cx + b, cy, c)
        lines.segment(cx, cy - b, cx, cy - a, c); lines.segment(cx, cy + a, cx, cy + b, c)
    }

    /** Controles touch provisórios (Etapa 5 traz a interface definitiva). */
    private fun drawTouchControls(lines: NeonLineBatch) {
        val dim = NeonPalette.HUD_DIM
        input.viewButtons.forEachIndexed { i, r ->
            rect(r, lines, if (View.entries[i] == model.view) NeonPalette.PLAYER_SHIP else dim)
        }
        val t = input.throttleBar
        rect(t, lines, dim)
        val level = t.y + t.height * (model.player.speed - 1f) / 39f
        lines.segment(t.x, level, t.x + t.width, level, NeonPalette.PLAYER_SHIP)
        if (input.stickPointer >= 0) {
            val ox = input.stickOriginX; val oy = input.stickOriginY; val r = input.stickRadius
            var px = ox + r; var py = oy
            for (i in 1..32) {
                val a = i * (2f * Math.PI.toFloat() / 32)
                val x = ox + r * kotlin.math.cos(a); val y = oy + r * kotlin.math.sin(a)
                lines.segment(px, py, x, y, dim); px = x; py = y
            }
            lines.segment(ox, oy, ox + input.stickX * r, oy - input.stickY * r, NeonPalette.PLAYER_SHIP)
        }
    }

    private fun rect(r: Rectangle, lines: NeonLineBatch, c: Float) {
        lines.segment(r.x, r.y, r.x + r.width, r.y, c)
        lines.segment(r.x + r.width, r.y, r.x + r.width, r.y + r.height, c)
        lines.segment(r.x + r.width, r.y + r.height, r.x, r.y + r.height, c)
        lines.segment(r.x, r.y + r.height, r.x, r.y, c)
    }

    private fun drawText(view: SpaceView, batch: SpriteBatch, font: BitmapFont) {
        val w = view.width; val h = view.height
        font.color = HUD_GREEN
        when (state) {
            State.DOCKED -> {
                centered(batch, font, "DOCKED  -  $stationName", w / 2f, h * 0.62f)
                centered(batch, font, "LAUNCH", w / 2f, input.launchButton.y + input.launchButton.height * 0.65f)
            }
            State.FLYING -> {
                centered(batch, font, model.view.label, w / 2f, h - font.lineHeight * 0.6f)
                val p = model.player
                val hud = String.format(
                    Locale.US, "SPEED %2d   ROLL %+4d   PITCH %+4d   SHIELD %3d  ENERGY %3d",
                    p.speed.toInt(), p.jstx.toInt(), p.jsty.toInt(), p.foreShield.toInt(), p.energy.toInt(),
                )
                font.draw(batch, hud, font.lineHeight * 0.5f, font.lineHeight * 1.2f)
                VIEW_LABELS.forEachIndexed { i, s ->
                    val r = input.viewButtons[i]
                    centered(batch, font, s, r.x + r.width / 2f, r.y + r.height * 0.65f)
                }
                if (messageTime > 0f) message?.let { centered(batch, font, it, w / 2f, h * 0.35f) }
            }
            State.DESTROYED -> centered(batch, font, "GAME OVER", w / 2f, h * 0.55f)
            else -> Unit
        }
    }

    private fun centered(batch: SpriteBatch, font: BitmapFont, text: String, x: Float, y: Float) {
        layout.setText(font, text)
        font.draw(batch, text, x - layout.width / 2f, y)
    }

    private companion object {
        val HUD_GREEN = Color(0.35f, 1f, 0.45f, 1f)
        val VIEW_LABELS = arrayOf("F", "R", "L", "D") // frente, ré (trás), esquerda, direita
    }
}
