package com.eliteandroid.ships

import com.badlogic.gdx.math.Vector3

/**
 * Uma nave no espaço, no referencial do jogador — como no Elite original, o jogador está
 * sempre na origem olhando para +z (x à direita, y para cima) e é o universo que se move.
 *
 * A orientação usa os três vetores do bloco de dados da nave (INWK): nosev (nariz),
 * roofv (teto) e sidev (lateral). Um vértice (x, y, z) do modelo vai para
 * `position + sidev·x + roofv·y + nosev·z`.
 */
class ShipInstance(
    val blueprint: ShipBlueprint,
    val position: Vector3 = Vector3(),
    /** Cor ARGB empacotada usada pelo renderizador neon (ver [com.eliteandroid.render.NeonPalette]). */
    var color: Float,
) {
    val nosev = Vector3(0f, 0f, 1f)
    val roofv = Vector3(0f, 1f, 0f)
    val sidev = Vector3(1f, 0f, 0f)

    private val tmp = Vector3()

    /** Aponta o nariz na direção [nose] mantendo o teto o mais próximo possível de [up]. */
    fun orient(nose: Vector3, up: Vector3 = Vector3.Y): ShipInstance {
        nosev.set(nose).nor()
        roofv.set(up).mulAdd(nosev, -up.dot(nosev)).nor()
        sidev.set(roofv).crs(nosev) // sidev = roofv × nosev (base do original)
        return this
    }

    /** Rolagem: gira roofv e sidev em torno de nosev. */
    fun roll(angle: Float) = rotatePair(roofv, sidev, angle)

    /** Arfagem (pitch): gira nosev e roofv em torno de sidev. */
    fun pitch(angle: Float) = rotatePair(nosev, roofv, angle)

    private fun rotatePair(a: Vector3, b: Vector3, angle: Float) {
        val c = kotlin.math.cos(angle); val s = kotlin.math.sin(angle)
        tmp.set(a).scl(c).mulAdd(b, s)
        b.scl(c).mulAdd(a, -s)
        a.set(tmp)
        orthonormalize()
    }

    /** Equivalente ao TIDY do original: mantém a base ortonormal contra erros acumulados. */
    fun orthonormalize() {
        nosev.nor()
        roofv.mulAdd(nosev, -roofv.dot(nosev)).nor()
        sidev.set(roofv).crs(nosev)
    }

    /** Transforma um ponto do modelo para o referencial do jogador. */
    fun toWorld(x: Float, y: Float, z: Float, out: Vector3): Vector3 = out.set(position)
        .mulAdd(sidev, x).mulAdd(roofv, y).mulAdd(nosev, z)
}
