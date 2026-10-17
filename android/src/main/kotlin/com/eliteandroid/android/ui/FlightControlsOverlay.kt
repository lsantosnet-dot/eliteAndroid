package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.eliteandroid.EliteGame
import com.eliteandroid.GameSession
import com.eliteandroid.flight.View as SpaceDirection

/**
 * Interface touch de voo (Views Android sobre a superfície LibGDX):
 *
 *  - manche flutuante na esquerda (rolagem/arfagem);
 *  - acelerador vertical na borda direita, FIRE (segurar), MISSILE, HYPER e JUMP;
 *  - câmeras F/R/L/D no canto superior direito.
 * (Atracado, quem aparece é o [StationPanel].)
 *
 * Tudo escreve em [com.eliteandroid.input.TouchControls] (thread-safe) e o estado do jogo
 * é lido a cada 100 ms para mostrar/esconder controles e atualizar mostradores.
 */
@SuppressLint("ViewConstructor")
class FlightControlsOverlay(context: Context, private val game: EliteGame) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val touch get() = game.sessionOrNull?.input?.touch

    private val joystick = JoystickView(
        context,
        onMove = { x, y -> touch?.let { it.stickX = x; it.stickY = y } },
        onRelease = { touch?.let { it.stickX = Float.NaN; it.stickY = Float.NaN } },
    )

    private val throttle = ThrottleView(context) { s -> touch?.throttle = s }

    private val fire = NeonButton(
        context, "FIRE", NeonStyle.RED, round = true, textSizeDp = 16f,
        onPress = { touch?.fire = true },
        onRelease = { touch?.fire = false },
    )

    private val hyper = NeonButton(
        context, "HYPER", NeonStyle.MAGENTA, round = false, textSizeDp = 12f,
        onClick = { touch?.hyperspaceRequest = true },
    )

    /** Míssil: 1º toque arma (procura alvo na mira), com alvo travado dispara; armado sem alvo, desarma. */
    private val missile = NeonButton(
        context, "MSL 3", NeonStyle.YELLOW, round = true, textSizeDp = 12f,
        onClick = { touch?.missileRequest = true },
    )

    /** WARP ("J"): salto dentro do sistema em direção ao planeta. */
    private val jump = NeonButton(
        context, "JUMP", NeonStyle.CYAN, round = false, textSizeDp = 12f,
        onClick = { touch?.jumpRequest = true },
    )

    private val viewButtons = SpaceDirection.entries.map { dir ->
        NeonButton(context, VIEW_LABELS[dir.ordinal], NeonStyle.GREEN, round = false, textSizeDp = 14f,
            onClick = { touch?.viewRequest = dir })
    }

    private val flightControls = listOf<View>(joystick, throttle, fire, missile, hyper, jump)
    private val viewRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        viewButtons.forEach { addView(it, LinearLayout.LayoutParams(dp(52f), dp(52f))) }
    }

    private var lastState: GameSession.State? = null

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            postDelayed(this, 100)
        }
    }

    init {
        addView(joystick, LayoutParams(0, 0, Gravity.START or Gravity.BOTTOM))
        addView(throttle, LayoutParams(dp(64f), 0, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(6f); bottomMargin = dp(12f)
        })
        addView(fire, LayoutParams(dp(104f), dp(104f), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(76f); bottomMargin = dp(14f)
        })
        // Coluna da direita, de baixo para cima: FIRE, MISSILE, HYPER, JUMP.
        addView(missile, LayoutParams(dp(80f), dp(80f), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(88f); bottomMargin = dp(122f)
        })
        addView(hyper, LayoutParams(dp(92f), dp(46f), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(82f); bottomMargin = dp(206f)
        })
        addView(jump, LayoutParams(dp(92f), dp(46f), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(82f); bottomMargin = dp(256f)
        })
        addView(viewRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).apply {
            topMargin = dp(6f); marginEnd = dp(6f)
        })
        setFlightControlsVisible(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            // Manche: 40% da largura, abaixo da faixa superior (onde ficam DATA e as câmeras).
            joystick.layoutParams = (joystick.layoutParams as LayoutParams).apply {
                width = (w * 0.40f).toInt(); height = (h * 0.82f).toInt()
            }
            throttle.layoutParams = (throttle.layoutParams as LayoutParams).apply { height = (h * 0.58f).toInt() }
            requestLayout()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(poll)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(poll)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        val session = game.sessionOrNull ?: return
        val state = session.state
        if (state != lastState) {
            val flying = state == GameSession.State.FLYING
            setFlightControlsVisible(flying)
            if (state == GameSession.State.FLYING && lastState == GameSession.State.LAUNCHING) {
                // Novo voo: o acelerador volta a acompanhar a velocidade de lançamento.
                session.input.touch.throttle = Float.NaN
                throttle.target = Float.NaN
            }
            if (!flying) session.input.touch.fire = false
            lastState = state
        }
        if (state != GameSession.State.FLYING) return
        val model = session.model
        throttle.speed = model.player.speed
        viewButtons.forEachIndexed { i, b -> b.lit = model.view.ordinal == i }
        val c = model.combat
        missile.label = when {
            c.missileTarget != null -> "LOCK"
            c.missileArmed -> "ARM"
            else -> "MSL ${model.player.missiles}"
        }
        missile.color = if (c.missileTarget != null) NeonStyle.RED else NeonStyle.YELLOW
        missile.lit = c.missileArmed || c.missileTarget != null
        missile.invalidate()
        val countdown = model.hyperspace.shown
        hyper.lit = countdown > 0
        hyper.label = if (countdown > 0) "HYP $countdown" else "HYPER"
        hyper.invalidate()
    }

    private fun setFlightControlsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        flightControls.forEach { it.visibility = v }
        viewRow.visibility = v
    }

    private companion object {
        /** Frente, ré (trás), esquerda, direita — como no HUD do jogo. */
        val VIEW_LABELS = arrayOf("F", "R", "L", "D")
    }
}
