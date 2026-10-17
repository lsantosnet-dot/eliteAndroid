package com.eliteandroid.ships

import com.badlogic.gdx.math.Vector3

/**
 * Uma nave no espaço, no referencial do jogador — como no Elite original, o jogador está
 * sempre na origem olhando para +z (x à direita, y para cima) e é o universo que se move.
 *
 * Equivale ao bloco de dados INWK: posição, os três vetores de orientação nosev (nariz),
 * roofv (teto) e sidev (lateral), velocidade (byte #27) e os contadores de rolagem (#29)
 * e arfagem (#30). Um vértice (x, y, z) do modelo vai para
 * `position + sidev·x + roofv·y + nosev·z`.
 *
 * Convenção do original (ZINF/TIDY): sidev = nosev × roofv.
 */
class ShipInstance(
    val blueprint: ShipBlueprint,
    val position: Vector3 = Vector3(),
    /** Cor ARGB empacotada usada pelo renderizador neon (ver [com.eliteandroid.render.NeonPalette]). */
    var color: Float,
) {
    // Orientação padrão do ZINF: nariz apontando para o jogador.
    val nosev = Vector3(0f, 0f, -1f)
    val roofv = Vector3(0f, 1f, 0f)
    val sidev = Vector3(1f, 0f, 0f)

    /** Velocidade própria (byte #27). Cada ciclo a nave anda nosev · speed · 96/64. */
    var speed = 0f

    /**
     * Contadores de rolagem/arfagem (bytes #29/#30): sinal = sentido, |valor| = ciclos
     * restantes girando 1/16 rad por ciclo. 127 = gira para sempre (sem amortecimento).
     */
    var rollCounter = 0f
    var pitchCounter = 0f

    /** Estação hostil (AI byte #32 com bit 7) — atracar nela é fatal. */
    var hostile = false

    private val tmp = Vector3()

    /** Aponta o nariz na direção [nose] mantendo o teto o mais próximo possível de [up]. */
    fun orient(nose: Vector3, up: Vector3 = Vector3.Y): ShipInstance {
        nosev.set(nose).nor()
        roofv.set(up).mulAdd(nosev, -up.dot(nosev)).nor()
        sidev.set(nosev).crs(roofv)
        return this
    }

    /** Equivalente ao TIDY: mantém a base ortonormal (sidev = nosev × roofv). */
    fun orthonormalize() {
        nosev.nor()
        roofv.mulAdd(nosev, -roofv.dot(nosev)).nor()
        sidev.set(nosev).crs(roofv)
    }

    /** Transforma um ponto do modelo para o referencial do jogador. */
    fun toWorld(x: Float, y: Float, z: Float, out: Vector3): Vector3 = out.set(position)
        .mulAdd(sidev, x).mulAdd(roofv, y).mulAdd(nosev, z)

    /**
     * MVEIT partes 3 e 8: movimento e rotação da própria nave.
     * [f] é a fração de um ciclo do loop principal do original (1 = um ciclo completo).
     */
    fun moveSelf(f: Float) {
        if (speed != 0f) position.mulAdd(nosev, speed * 1.5f * f) // nosev_hi(96) · speed / 64

        if (pitchCounter != 0f) {
            mvs5(roofv, nosev, pitchCounter > 0f, f) // pitch: (roofv, nosev)
            pitchCounter = damp(pitchCounter, f)
        }
        if (rollCounter != 0f) {
            mvs5(roofv, sidev, rollCounter > 0f, f) // roll: (roofv, sidev)
            rollCounter = damp(rollCounter, f)
        }
        orthonormalize()
    }

    /**
     * MVEIT partes 5, 6 e 7: aplica a rolagem [alpha] e a arfagem [beta] do jogador
     * (radianos por ciclo, já multiplicados por f) e o avanço [playerDistance] do jogador.
     */
    fun applyPlayerMotion(alpha: Float, beta: Float, playerDistance: Float) {
        // Parte 5: gira a posição (aproximação de ângulo pequeno do original).
        val p = position
        val k2 = p.y - alpha * p.x
        p.z += beta * k2
        p.y = k2 - beta * p.z
        p.x += alpha * p.y
        // Parte 6: o jogador avança, então tudo "vem" em sua direção.
        p.z -= playerDistance
        // Parte 7: MVS4 nos três vetores de orientação.
        mvs4(nosev, alpha, beta); mvs4(roofv, alpha, beta); mvs4(sidev, alpha, beta)
    }

    private fun damp(counter: Float, f: Float): Float {
        val mag = kotlin.math.abs(counter)
        if (mag >= 127f) return counter // 127 = sem amortecimento
        val next = (mag - f).coerceAtLeast(0f)
        return if (counter > 0f) next else -next
    }

    private companion object {
        /** MVS4: rolagem (x/y) e depois arfagem (y/z) com o algoritmo de círculo de Minsky. */
        fun mvs4(v: Vector3, alpha: Float, beta: Float) {
            v.y -= alpha * v.x
            v.x += alpha * v.y
            v.y -= beta * v.z
            v.z += beta * v.y
        }

        /**
         * MVS5: gira o par (a, b) em 1/16 rad por ciclo. Sentido positivo:
         *   a = a·(1 − 1/512) + b/16 ;  b = b·(1 − 1/512) − a/16
         * (aqui com seno/cosseno exatos do ângulo f/16, idêntico no limite f = 1).
         */
        fun mvs5(a: Vector3, b: Vector3, positive: Boolean, f: Float) {
            val angle = f / 16f
            val c = kotlin.math.cos(angle)
            val s = if (positive) kotlin.math.sin(angle) else -kotlin.math.sin(angle)
            val ax = a.x; val ay = a.y; val az = a.z
            a.set(ax * c + b.x * s, ay * c + b.y * s, az * c + b.z * s)
            b.set(b.x * c - ax * s, b.y * c - ay * s, b.z * c - az * s)
        }
    }
}
