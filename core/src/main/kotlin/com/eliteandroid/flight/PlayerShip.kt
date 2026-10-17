package com.eliteandroid.flight

import kotlin.math.abs

/**
 * Estado de voo do jogador, com a mesma conversão do original (loop principal, parte 2):
 *
 *  - JSTX / JSTY: taxa de rolagem/arfagem do "manche", −127..+127 (no original 1..255
 *    com centro em 128). Aqui JSTX > 0 = rolar à direita e JSTY > 0 = puxar o nariz para
 *    cima ("X" no original);
 *  - ALP1 = |JSTX|/8 se |JSTX| < 32, senão |JSTX|/4  → 0..31;  α = ±ALP1/256 rad por ciclo
 *    (rolar à direita gira o universo no sentido oposto: α negativo);
 *  - BET1 = (|JSTY|+4)/16, metade se < 3  → 0..8;  β = ±BET1/256 rad por ciclo;
 *  - DELTA: velocidade 1..40 (unidades por ciclo).
 *
 * Teclado (DOKEY): cada ciclo com a tecla pressionada soma ±7 em JSTX e ±14 em JSTY;
 * sem tecla, o amortecimento (cntr) puxa JSTX 2 e JSTY 1 em direção ao centro, e apertar
 * o sentido oposto recentraliza na hora (auto-recentre).
 */
class PlayerShip {

    var jstx = 0f
        private set
    var jsty = 0f
        private set

    /** DELTA: velocidade atual (1..40). */
    var speed = 12f
        private set

    /** Energia (0..255) e escudos dianteiro/traseiro (FSH/ASH, 0..255). */
    var energy = 255f
    var foreShield = 255f
    var aftShield = 255f

    /** α e β do ciclo atual (rad por ciclo). */
    var alpha = 0f
        private set
    var beta = 0f
        private set

    fun reset(launchSpeed: Float = LAUNCH_SPEED) {
        jstx = 0f; jsty = 0f; speed = launchSpeed
        energy = 255f; foreShield = 255f; aftShield = 255f
        alpha = 0f; beta = 0f
    }

    /**
     * Aplica os controles de um (fragmento de) ciclo [f].
     *
     * Modo teclado: [rollKey]/[pitchKey] em −1, 0, +1. Modo analógico (joystick / touch):
     * [stickX]/[stickY] em −1..1 definem JSTX/JSTY diretamente, como o joystick do original.
     */
    fun applyControls(
        f: Float,
        rollKey: Int = 0,
        pitchKey: Int = 0,
        stickX: Float? = null,
        stickY: Float? = null,
        accelerate: Boolean = false,
        decelerate: Boolean = false,
        targetSpeed: Float? = null,
    ) {
        jstx = if (stickX != null) (stickX * 127f).coerceIn(-127f, 127f)
        else keyAxis(jstx, rollKey, 7f * f, 2f * f)
        jsty = if (stickY != null) (stickY * 127f).coerceIn(-127f, 127f)
        else keyAxis(jsty, pitchKey, 14f * f, 1f * f)

        if (accelerate) speed = (speed + f).coerceAtMost(MAX_SPEED)
        if (decelerate) speed = (speed - f).coerceAtLeast(MIN_SPEED)
        if (targetSpeed != null) {
            val t = targetSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
            speed = if (speed < t) (speed + f).coerceAtMost(t) else (speed - f).coerceAtLeast(t)
        }

        alpha = -alp1(jstx) / 256f * kotlin.math.sign(jstx)
        beta = bet1(jsty) / 256f * kotlin.math.sign(jsty)
    }

    /** Batida leve (MA67): para a nave (DELTA = 1) e causa 5 de dano por ciclo. */
    fun bump(f: Float): Boolean {
        speed = MIN_SPEED
        return damage(5f * f, front = true)
    }

    /**
     * OOPS: o dano vai primeiro para o escudo do lado atingido; o que sobrar sai da energia.
     * Retorna true se a nave foi destruída.
     */
    fun damage(amount: Float, front: Boolean): Boolean {
        var rest = amount
        if (front) {
            val absorbed = minOf(foreShield, rest); foreShield -= absorbed; rest -= absorbed
        } else {
            val absorbed = minOf(aftShield, rest); aftShield -= absorbed; rest -= absorbed
        }
        energy -= rest
        return energy < 0f
    }

    companion object {
        const val MIN_SPEED = 1f
        const val MAX_SPEED = 40f
        /** Velocidade ao sair da estação (TT110: DELTA = 12). */
        const val LAUNCH_SPEED = 12f

        /** ALP1: magnitude do ângulo de rolagem (0..31) a partir de JSTX. */
        fun alp1(jstx: Float): Float {
            val a = abs(jstx).toInt()
            return (if (a < 32) a / 8 else a / 4).toFloat()
        }

        /** BET1: magnitude do ângulo de arfagem (0..8) a partir de JSTY. */
        fun bet1(jsty: Float): Float {
            var b = (abs(jsty).toInt() + 4) / 16
            if (b < 3) b /= 2
            return b.toFloat()
        }

        private fun keyAxis(value: Float, key: Int, step: Float, damping: Float): Float = when {
            // BUMP2/REDU2: se o resultado ainda estiver do lado oposto, recentraliza (auto-recentre).
            key > 0 -> (value + step).let { if (it < 0f) 0f else it }.coerceAtMost(127f)
            key < 0 -> (value - step).let { if (it > 0f) 0f else it }.coerceAtLeast(-127f)
            value > 0f -> (value - damping).coerceAtLeast(0f)                       // cntr
            value < 0f -> (value + damping).coerceAtMost(0f)
            else -> 0f
        }
    }
}
