package com.eliteandroid.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

/**
 * Poeira espacial da vista frontal. O BBC Elite usa NOSTM = 18 partículas no espaço;
 * cada uma se aproxima do jogador conforme a velocidade e é recriada ao sair da vista.
 * (Na Etapa 3 a velocidade e a rotação passam a vir do voo do jogador.)
 */
class Stardust(val count: Int = 18) {

    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val z = FloatArray(count)
    private val tmp = Vector3()

    /** Velocidade em unidades de espaço por segundo. */
    var speed = 120f

    init {
        for (i in 0 until count) respawn(i, MathUtils.random(60f, FAR))
    }

    private fun respawn(i: Int, depth: Float) {
        x[i] = MathUtils.random(-SPREAD, SPREAD)
        y[i] = MathUtils.random(-SPREAD * 0.7f, SPREAD * 0.7f)
        z[i] = depth
    }

    fun update(delta: Float) {
        for (i in 0 until count) {
            z[i] -= speed * delta
            if (z[i] < NEAR) respawn(i, FAR)
        }
    }

    fun draw(projector: WireframeProjector, batch: NeonLineBatch) {
        val w = projector.viewportWidth; val h = projector.viewportHeight
        for (i in 0 until count) {
            if (!projector.projectPoint(x[i], y[i], z[i], tmp)) continue
            if (tmp.x < 0 || tmp.y < 0 || tmp.x > w || tmp.y > h) { respawn(i, FAR); continue }
            // Partículas mais próximas aparecem maiores, como no original.
            batch.dot(tmp.x, tmp.y, NeonPalette.DUST, batch.halfWidth * (if (z[i] < FAR * 0.3f) 2f else 1f))
        }
    }

    private companion object {
        const val NEAR = 30f
        const val FAR = 1200f
        const val SPREAD = 700f
    }
}
