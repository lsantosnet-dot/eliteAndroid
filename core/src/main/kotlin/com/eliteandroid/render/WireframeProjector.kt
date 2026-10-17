package com.eliteandroid.render

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.ships.ShipInstance

/** Destino dos segmentos 2D (em pixels, origem no canto inferior esquerdo). */
fun interface SegmentSink {
    fun segment(x0: Float, y0: Float, x1: Float, y1: Float, color: Float)
}

/**
 * Projeta o wireframe de uma nave na tela, com a mesma lógica do LL9 do Elite:
 *  1. faces visíveis = normal voltada para o observador (o jogador na origem);
 *  2. aresta desenhada se uma de suas faces for visível e se a nave estiver perto o
 *     bastante para o nível de detalhe da aresta;
 *  3. projeção em perspectiva x/z, y/z, com recorte no plano próximo.
 *
 * Projeção: no BBC Elite a vista tem 256 px de largura e a projeção é x/z·256 a partir do
 * centro, o que dá ~53° de campo horizontal. Usamos a mesma razão: focal = largura da vista.
 */
class WireframeProjector {

    var viewportWidth = 256f
    var viewportHeight = 192f
    var nearZ = 1f

    /** Unidades de distância por nível de visibilidade das arestas (z_hi do original = z/256). */
    var lodUnit = 256f

    private val camModel = Vector3()
    private val tmp = Vector3()
    private var world = FloatArray(64 * 3)
    private var faceVisible = BooleanArray(32)

    val focal: Float get() = viewportWidth

    /** Projeta [ship] e envia os segmentos visíveis para [sink]. Retorna o nº de segmentos. */
    fun project(ship: ShipInstance, sink: SegmentSink): Int {
        val bp = ship.blueprint
        val pos = ship.position

        // Descarta naves inteiramente atrás do plano próximo.
        if (pos.z + bp.boundingRadius < nearZ) return 0

        // Posição do observador (origem) no referencial do modelo: Rᵀ·(0 − pos).
        tmp.set(pos).scl(-1f)
        camModel.set(tmp.dot(ship.sidev), tmp.dot(ship.roofv), tmp.dot(ship.nosev))

        if (faceVisible.size < bp.faceCount) faceVisible = BooleanArray(bp.faceCount)
        for (f in 0 until bp.faceCount) {
            val i = f * 3
            val d = bp.faceNormals[i] * (camModel.x - bp.facePoints[i]) +
                bp.faceNormals[i + 1] * (camModel.y - bp.facePoints[i + 1]) +
                bp.faceNormals[i + 2] * (camModel.z - bp.facePoints[i + 2])
            faceVisible[f] = d > 0f
        }

        if (world.size < bp.vertexCount * 3) world = FloatArray(bp.vertexCount * 3)
        for (v in 0 until bp.vertexCount) {
            ship.toWorld(bp.vertices[v * 3], bp.vertices[v * 3 + 1], bp.vertices[v * 3 + 2], tmp)
            world[v * 3] = tmp.x; world[v * 3 + 1] = tmp.y; world[v * 3 + 2] = tmp.z
        }

        val lodLevel = (pos.len() / lodUnit).toInt()
        var count = 0
        for (e in 0 until bp.edgeCount) {
            if (bp.edgeVisibility[e] < lodLevel) continue
            val f1 = bp.edges[e * 4 + 2]; val f2 = bp.edges[e * 4 + 3]
            if (!faceVisible[f1] && !faceVisible[f2]) continue
            if (emit(bp.edges[e * 4], bp.edges[e * 4 + 1], ship.color, sink)) count++
        }
        return count
    }

    private fun emit(a: Int, b: Int, color: Float, sink: SegmentSink): Boolean {
        var x0 = world[a * 3]; var y0 = world[a * 3 + 1]; var z0 = world[a * 3 + 2]
        var x1 = world[b * 3]; var y1 = world[b * 3 + 1]; var z1 = world[b * 3 + 2]
        if (z0 < nearZ && z1 < nearZ) return false
        // Recorte no plano próximo z = nearZ.
        if (z0 < nearZ) {
            val t = (nearZ - z0) / (z1 - z0)
            x0 += (x1 - x0) * t; y0 += (y1 - y0) * t; z0 = nearZ
        } else if (z1 < nearZ) {
            val t = (nearZ - z1) / (z0 - z1)
            x1 += (x0 - x1) * t; y1 += (y0 - y1) * t; z1 = nearZ
        }
        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val f = focal
        sink.segment(cx + x0 / z0 * f, cy + y0 / z0 * f, cx + x1 / z1 * f, cy + y1 / z1 * f, color)
        return true
    }

    /** Projeta um ponto isolado (poeira estelar). Retorna false se estiver atrás do plano próximo. */
    fun projectPoint(x: Float, y: Float, z: Float, out: Vector3): Boolean {
        if (z < nearZ) return false
        out.set(viewportWidth * 0.5f + x / z * focal, viewportHeight * 0.5f + y / z * focal, z)
        return true
    }
}
