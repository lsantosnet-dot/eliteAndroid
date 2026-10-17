package com.eliteandroid.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eliteandroid.flight.View

/**
 * Poeira espacial (NOSTM = 18 partículas no espaço, como no original).
 *
 * O original calcula a poeira direto em coordenadas de tela, com uma rotina por vista
 * (STARS1 frente, STARS6 trás, STARS2 laterais). Aqui as partículas vivem em 3D no
 * referencial do jogador e recebem exatamente a mesma rotação/avanço das naves (MVEIT),
 * o que reproduz os mesmos movimentos nas quatro vistas com um só código. Quando uma
 * partícula sai da vista atual ela renasce em posição aleatória da tela, como no original.
 */
class Stardust(val count: Int = 18) {

    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val z = FloatArray(count)
    private val tmp = Vector3()

    /** Meia largura/altura da vista em tangente (x/z, y/z). Atualizado pela [SpaceView]. */
    var halfTanX = 0.8f
    var halfTanY = 0.375f

    /** NOSTM: partículas em uso (o witchspace tem só 3). */
    var active = count
        set(value) { field = value.coerceIn(0, count) }

    fun reset(view: View) {
        for (i in 0 until count) respawn(i, view)
    }

    /** Partícula aleatória dentro do volume visível da vista atual. */
    private fun respawn(i: Int, view: View) {
        val depth = MathUtils.random(NEAR * 2f, FAR)
        val vx = MathUtils.random(-halfTanX, halfTanX) * depth
        val vy = MathUtils.random(-halfTanY, halfTanY) * depth
        view.fromView(vx, vy, depth, tmp)
        x[i] = tmp.x; y[i] = tmp.y; z[i] = tmp.z
    }

    /** Mesma transformação do MVEIT partes 5 e 6. */
    fun update(alpha: Float, beta: Float, distance: Float, view: View) {
        for (i in 0 until active) {
            val k2 = y[i] - alpha * x[i]
            z[i] += beta * k2
            y[i] = k2 - beta * z[i]
            x[i] += alpha * y[i]
            z[i] -= distance

            view.toView(x[i], y[i], z[i], tmp)
            if (tmp.z < NEAR || tmp.z > FAR * 1.2f ||
                kotlin.math.abs(tmp.x) > tmp.z * halfTanX * 1.1f || kotlin.math.abs(tmp.y) > tmp.z * halfTanY * 1.1f
            ) respawn(i, view)
        }
    }

    fun draw(projector: WireframeProjector, batch: NeonLineBatch) {
        val view = projector.view
        for (i in 0 until active) {
            view.toView(x[i], y[i], z[i], tmp)
            val d = tmp.z
            if (!projector.projectPoint(tmp.x, tmp.y, d, tmp)) continue
            // Partículas próximas aparecem maiores, como no original.
            batch.dot(tmp.x, tmp.y, NeonPalette.DUST, batch.halfWidth * if (d < FAR * 0.3f) 2f else 1f)
        }
    }

    private companion object {
        const val NEAR = 20f
        const val FAR = 1200f
    }
}
