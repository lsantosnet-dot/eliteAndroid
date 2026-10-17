package com.eliteandroid.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Rectangle
import com.eliteandroid.flight.FlightControls
import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.flight.View

/**
 * Entrada de voo.
 *
 * Teclado (teclas do original): "<"/"," e ">"/"." rolam, "X" puxa o nariz para cima e "S"
 * para baixo, Espaço acelera e "?"/"/" desacelera; setas também funcionam. F1–F4 ou 1–4
 * escolhem a vista (frente, trás, esquerda, direita) e L/Enter decola.
 *
 * Toque (PROVISÓRIO — substituído pela interface touch da Etapa 5): metade esquerda da
 * tela = manche analógico (arraste a partir do ponto tocado; puxar para baixo levanta o
 * nariz); faixa direita = acelerador; botões de vista no canto superior direito.
 */
class FlightInput : InputAdapter() {

    val controls = FlightControls()

    /** Pedidos de ação para a sessão. */
    var requestedView: View? = null
    var launchRequested = false

    // Áreas em pixels (origem embaixo à esquerda), atualizadas em [layout].
    val viewButtons = Array(4) { Rectangle() }
    val throttleBar = Rectangle()
    val launchButton = Rectangle()
    var stickRadius = 100f

    var stickPointer = -1; private set
    var stickOriginX = 0f; private set
    var stickOriginY = 0f; private set
    var stickX = 0f; private set
    var stickY = 0f; private set
    private var throttlePointer = -1
    var throttleTarget = PlayerShip.LAUNCH_SPEED

    private var screenH = 1f

    fun layout(width: Float, height: Float) {
        screenH = height
        val b = height * 0.11f
        for (i in 0 until 4) viewButtons[i].set(width - (4 - i) * (b * 1.15f) - b * 0.2f, height - b * 1.2f, b, b)
        throttleBar.set(width - b * 1.4f, height * 0.12f, b * 0.9f, height * 0.6f)
        launchButton.set(width / 2f - b * 2f, height * 0.18f, b * 4f, b * 1.1f)
        stickRadius = height * 0.18f
    }

    /** Lê o teclado e monta os controles do quadro. */
    fun poll() {
        val k = Gdx.input
        val rollLeft = k.isKeyPressed(Keys.COMMA) || k.isKeyPressed(Keys.LEFT)
        val rollRight = k.isKeyPressed(Keys.PERIOD) || k.isKeyPressed(Keys.RIGHT)
        val pullUp = k.isKeyPressed(Keys.X) || k.isKeyPressed(Keys.DOWN)
        val diveDown = k.isKeyPressed(Keys.S) || k.isKeyPressed(Keys.UP)
        controls.rollKey = (if (rollRight) 1 else 0) - (if (rollLeft) 1 else 0)
        controls.pitchKey = (if (pullUp) 1 else 0) - (if (diveDown) 1 else 0)
        controls.accelerate = k.isKeyPressed(Keys.SPACE)
        controls.decelerate = k.isKeyPressed(Keys.SLASH)

        val keyboardSteering = controls.rollKey != 0 || controls.pitchKey != 0
        controls.stickX = if (stickPointer >= 0 && !keyboardSteering) stickX else null
        controls.stickY = if (stickPointer >= 0 && !keyboardSteering) stickY else null
        controls.targetSpeed = if (throttlePointer >= 0) throttleTarget else null

        if (k.isKeyJustPressed(Keys.F1) || k.isKeyJustPressed(Keys.NUM_1)) requestedView = View.FRONT
        if (k.isKeyJustPressed(Keys.F2) || k.isKeyJustPressed(Keys.NUM_2)) requestedView = View.REAR
        if (k.isKeyJustPressed(Keys.F3) || k.isKeyJustPressed(Keys.NUM_3)) requestedView = View.LEFT
        if (k.isKeyJustPressed(Keys.F4) || k.isKeyJustPressed(Keys.NUM_4)) requestedView = View.RIGHT
        if (k.isKeyJustPressed(Keys.L) || k.isKeyJustPressed(Keys.ENTER)) launchRequested = true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat(); val y = screenH - screenY
        viewButtons.forEachIndexed { i, r -> if (r.contains(x, y)) { requestedView = View.entries[i]; return true } }
        if (launchButton.contains(x, y)) { launchRequested = true }
        if (x > throttleBar.x - throttleBar.width) {
            throttlePointer = pointer; updateThrottle(y); return true
        }
        if (x < Gdx.graphics.backBufferWidth * 0.5f && stickPointer < 0) {
            stickPointer = pointer; stickOriginX = x; stickOriginY = y; stickX = 0f; stickY = 0f
        }
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val x = screenX.toFloat(); val y = screenH - screenY
        if (pointer == stickPointer) {
            stickX = ((x - stickOriginX) / stickRadius).coerceIn(-1f, 1f)
            stickY = ((stickOriginY - y) / stickRadius).coerceIn(-1f, 1f) // puxar para baixo = nariz para cima
        } else if (pointer == throttlePointer) updateThrottle(y)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == stickPointer) { stickPointer = -1; stickX = 0f; stickY = 0f }
        if (pointer == throttlePointer) throttlePointer = -1
        return true
    }

    private fun updateThrottle(y: Float) {
        val t = ((y - throttleBar.y) / throttleBar.height).coerceIn(0f, 1f)
        throttleTarget = PlayerShip.MIN_SPEED + t * (PlayerShip.MAX_SPEED - PlayerShip.MIN_SPEED)
    }
}
