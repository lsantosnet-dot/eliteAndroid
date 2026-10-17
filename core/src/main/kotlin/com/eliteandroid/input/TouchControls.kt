package com.eliteandroid.input

import com.eliteandroid.flight.View

/**
 * Ponte entre a interface touch (Views Android, thread de UI) e o jogo (thread do GL).
 * Os campos são @Volatile; pedidos pontuais (vista, decolar, hiperespaço) são
 * consumidos pelo jogo uma única vez.
 */
class TouchControls {
    /** Manche analógico em −1..1 (NaN = solto). Y positivo = puxar = nariz para cima. */
    @Volatile var stickX = Float.NaN
    @Volatile var stickY = Float.NaN

    /** Velocidade desejada (1..40) definida pelo acelerador; NaN = sem alteração. */
    @Volatile var throttle = Float.NaN

    @Volatile var fire = false

    @Volatile var viewRequest: View? = null
    @Volatile var launchRequest = false
    @Volatile var hyperspaceRequest = false
}
