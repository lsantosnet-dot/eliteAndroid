package com.eliteandroid.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.eliteandroid.flight.FlightControls
import com.eliteandroid.flight.View

/**
 * Junta as duas fontes de controle num [FlightControls]:
 *
 * Teclado (teclas do original): "<"/"," e ">"/"." rolam, "X" puxa o nariz para cima e "S"
 * para baixo, Espaço acelera e "?"/"/" desacelera, "A" dispara o laser, "H" aciona o
 * hiperespaço e "J" o salto dentro do sistema; "T" arma o míssil, "M" dispara e "U"
 * desarma; setas também funcionam. F1–F4 ou 1–4 escolhem a vista e L/Enter decola.
 *
 * Toque: [touch], preenchido pela interface Android (manche, acelerador e botões).
 * O manche analógico funciona como o joystick do original (define JSTX/JSTY direto);
 * ao soltá-lo, o amortecimento do teclado (cntr) recentraliza aos poucos.
 */
class FlightInput {

    val controls = FlightControls()
    val touch = TouchControls()

    /** Pedidos de ação para a sessão (consumidos por ela). */
    var requestedView: View? = null
    var launchRequested = false
    var hyperspaceRequested = false
    var jumpRequested = false
    var missileArm = false
    var missileFire = false
    var missileUnarm = false
    var missileSmart = false

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
        controls.fire = k.isKeyPressed(Keys.A) || touch.fire

        val keyboardSteering = controls.rollKey != 0 || controls.pitchKey != 0
        val sx = touch.stickX; val sy = touch.stickY
        val stickActive = !sx.isNaN() && !sy.isNaN() && !keyboardSteering
        controls.stickX = if (stickActive) sx else null
        controls.stickY = if (stickActive) sy else null
        val t = touch.throttle
        controls.targetSpeed = if (t.isNaN()) null else t

        if (k.isKeyJustPressed(Keys.F1) || k.isKeyJustPressed(Keys.NUM_1)) requestedView = View.FRONT
        if (k.isKeyJustPressed(Keys.F2) || k.isKeyJustPressed(Keys.NUM_2)) requestedView = View.REAR
        if (k.isKeyJustPressed(Keys.F3) || k.isKeyJustPressed(Keys.NUM_3)) requestedView = View.LEFT
        if (k.isKeyJustPressed(Keys.F4) || k.isKeyJustPressed(Keys.NUM_4)) requestedView = View.RIGHT
        touch.viewRequest?.let { requestedView = it; touch.viewRequest = null }

        if (k.isKeyJustPressed(Keys.L) || k.isKeyJustPressed(Keys.ENTER) || touch.launchRequest) {
            launchRequested = true; touch.launchRequest = false
        }
        if (k.isKeyJustPressed(Keys.H) || touch.hyperspaceRequest) {
            hyperspaceRequested = true; touch.hyperspaceRequest = false
        }
        if (k.isKeyJustPressed(Keys.J) || touch.jumpRequest) {
            jumpRequested = true; touch.jumpRequest = false
        }
        missileArm = k.isKeyJustPressed(Keys.T)
        missileFire = k.isKeyJustPressed(Keys.M)
        missileUnarm = k.isKeyJustPressed(Keys.U)
        missileSmart = touch.missileRequest
        touch.missileRequest = false
    }
}
