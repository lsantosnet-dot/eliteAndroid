package com.eliteandroid.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eliteandroid.ships.ShipInstance

/**
 * Efeitos de combate:
 *  - DOEXP: nuvem de explosão em torno dos primeiros "explosion count" vértices; o
 *    contador cresce de 18 até 256, o raio (em unidades do espaço) é o próprio contador e
 *    as partículas por vértice são contador/8 (subindo e depois descendo);
 *  - LL9 parte 9: laser inimigo, do vértice do canhão até a borda da tela do lado oposto,
 *    numa altura que varia com z_lo da nave.
 */
class CombatRenderer {

    private val w = Vector3()
    private val out = Vector3()

    fun drawExplosion(ship: ShipInstance, projector: WireframeProjector, lines: NeonLineBatch) {
        val bp = ship.blueprint
        val counter = ship.cloudCounter.toInt().coerceIn(0, 255)
        val perVertex = ((if (counter < 128) counter else 255 - counter) / 8) or 1
        val vertices = bp.explosionVertices.coerceIn(1, bp.vertexCount)
        for (v in 0 until vertices) {
            ship.toWorld(bp.vertices[v * 3], bp.vertices[v * 3 + 1], bp.vertices[v * 3 + 2], w)
            projector.view.toView(w.x, w.y, w.z, w)
            if (!projector.projectPoint(w.x, w.y, w.z, out)) continue
            val r = counter / w.z * projector.focal
            repeat(perVertex) {
                val a = MathUtils.random(MathUtils.PI2)
                val d = MathUtils.random(r)
                lines.dot(out.x + d * MathUtils.cos(a), out.y + d * MathUtils.sin(a), ship.color)
            }
        }
    }

    fun drawEnemyLaser(ship: ShipInstance, projector: WireframeProjector, lines: NeonLineBatch, color: Float) {
        if (!projector.projectVertex(ship, ship.blueprint.gunVertex, out)) return
        val endX = if (ship.position.x >= 0f) 0f else projector.viewportWidth
        val zLo = (ship.position.z.toInt() and 0xFF) / 255f
        val endY = projector.viewportHeight * (1f - zLo * 0.75f)
        lines.segment(out.x, out.y, endX, endY, color)
    }
}
