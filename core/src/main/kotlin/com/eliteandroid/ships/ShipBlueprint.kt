package com.eliteandroid.ships

import com.badlogic.gdx.math.Vector3

/**
 * Modelo 3D de uma nave no formato do Elite original (blocos VERTEX / EDGE / FACE do
 * disassembly de Mark Moxon).
 *
 * Coordenadas no referencial da própria nave: x = lateral (sidev), y = teto (roofv),
 * z = nariz (nosev). Os valores são os inteiros originais do BBC Elite.
 *
 * Remoção de linhas ocultas como no original: uma aresta é desenhada se pelo menos
 * uma das duas faces que ela separa estiver voltada para o observador. A "visibilidade"
 * de arestas é a distância máxima (em unidades de z_hi) em que o detalhe ainda é
 * desenhado — 31 = sempre.
 *
 * As normais das faces são calculadas a partir da geometria (os valores do original são
 * aproximações inteiras dessas mesmas normais).
 */
class ShipBlueprint private constructor(
    val name: String,
    /** x, y, z intercalados. */
    val vertices: FloatArray,
    /** v1, v2, face1, face2 por aresta. */
    val edges: IntArray,
    val edgeVisibility: IntArray,
    /** Arestas de detalhe (fendas, decalques, canhão): não definem o plano das faces. */
    private val edgeDetail: BooleanArray,
    val faceCount: Int,
    /** Byte #13 do modelo: além de z_hi (z/256) maior que isto, a nave vira um ponto. */
    val visibilityDistance: Int,
    /** Bytes #1-2 ("targetable area"): o laser acerta se x² + y² < este valor (HITCH). */
    val targetableArea: Int,
    /** Atributos do bloco do modelo: energia (#14), velocidade (#15), recompensa (#10-11, em décimos de Cr). */
    val maxEnergy: Int,
    val maxSpeed: Int,
    val bounty: Int,
    /** Byte #19: potência do laser nos bits 3-7 e mísseis nos bits 0-2. */
    val laserByte: Int,
    /** Byte #0: máximo de contêineres de carga soltos ao ser destruída. */
    val maxCanisters: Int,
    /** Byte #6/4: vértice de onde sai o laser. */
    val gunVertex: Int,
    /** (Byte #7 − 6)/4: quantos vértices geram nuvens na explosão. */
    val explosionVertices: Int,
) {
    val laserPower: Int get() = laserByte ushr 3
    val missiles: Int get() = laserByte and 7

    val vertexCount: Int get() = vertices.size / 3
    val edgeCount: Int get() = edges.size / 4

    /** Normais unitárias (x, y, z) por face, apontando para fora. */
    val faceNormals: FloatArray = FloatArray(faceCount * 3)

    /** Um ponto sobre cada face (centroide dos vértices da face). */
    val facePoints: FloatArray = FloatArray(faceCount * 3)

    /** Raio da esfera envolvente (para descartar naves fora de vista). */
    val boundingRadius: Float

    init {
        var r2 = 0f
        for (i in 0 until vertexCount) {
            val x = vertices[i * 3]; val y = vertices[i * 3 + 1]; val z = vertices[i * 3 + 2]
            r2 = maxOf(r2, x * x + y * y + z * z)
        }
        boundingRadius = kotlin.math.sqrt(r2)
        computeFaces()
    }

    fun vertex(i: Int, out: Vector3): Vector3 = out.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2])

    /** Índices dos vértices que pertencem à face [f] (via arestas de contorno, não de detalhe). */
    fun faceVertices(f: Int): Set<Int> {
        val set = linkedSetOf<Int>()
        for (e in 0 until edgeCount) {
            val f1 = edges[e * 4 + 2]; val f2 = edges[e * 4 + 3]
            if (!edgeDetail[e] && f1 != f2 && (f1 == f || f2 == f)) {
                set += edges[e * 4]; set += edges[e * 4 + 1]
            }
        }
        return set
    }

    private fun computeFaces() {
        val center = Vector3()
        for (i in 0 until vertexCount) center.add(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2])
        center.scl(1f / vertexCount)

        val a = Vector3(); val b = Vector3(); val c = Vector3(); val n = Vector3(); val tmp = Vector3()
        for (f in 0 until faceCount) {
            val vs = faceVertices(f).toList()
            require(vs.size >= 3) { "$name: face $f tem só ${vs.size} vértices" }

            // Centroide da face.
            val centroid = Vector3()
            vs.forEach { centroid.add(vertex(it, tmp)) }
            centroid.scl(1f / vs.size)

            // Normal pelo triângulo de maior área entre os vértices da face (robusto a quase-colinearidade).
            var best = 0f
            for (i in vs.indices) for (j in i + 1 until vs.size) for (k in j + 1 until vs.size) {
                vertex(vs[i], a); vertex(vs[j], b); vertex(vs[k], c)
                b.sub(a); c.sub(a)
                tmp.set(b).crs(c)
                val len2 = tmp.len2()
                if (len2 > best) { best = len2; n.set(tmp) }
            }
            n.nor()
            // Orienta para fora do centro do casco.
            tmp.set(centroid).sub(center)
            if (n.dot(tmp) < 0f) n.scl(-1f)

            faceNormals[f * 3] = n.x; faceNormals[f * 3 + 1] = n.y; faceNormals[f * 3 + 2] = n.z
            facePoints[f * 3] = centroid.x; facePoints[f * 3 + 1] = centroid.y; facePoints[f * 3 + 2] = centroid.z
        }
    }

    /** DSL que espelha a sintaxe dos blocos VERTEX/EDGE/FACE do disassembly. */
    class Builder(private val name: String) {
        private val verts = ArrayList<Float>()
        private val edgeData = ArrayList<Int>()
        private val edgeVis = ArrayList<Int>()
        private val edgeDet = ArrayList<Boolean>()
        private var faces = 0

        /** Byte #13 do bloco do modelo ("Visibility distance"). */
        var visibilityDistance = 31

        /** Bytes #1-2 do bloco do modelo ("Targetable area"). */
        var targetableArea = 0

        private var energy = 0; private var speed = 0; private var laserByte = 0
        private var bounty = 0; private var canisters = 0; private var gunVertex = 0; private var explosion = 4

        fun stats(energy: Int, speed: Int, laserByte: Int, bounty: Int, canisters: Int, gunVertex: Int, explosionVertices: Int) {
            this.energy = energy; this.speed = speed; this.laserByte = laserByte; this.bounty = bounty
            this.canisters = canisters; this.gunVertex = gunVertex; this.explosion = explosionVertices
        }

        fun vertex(x: Int, y: Int, z: Int) {
            verts += x.toFloat(); verts += y.toFloat(); verts += z.toFloat()
        }

        /**
         * [detail] marca arestas que só "pegam carona" na visibilidade das faces (como o canhão,
         * que sai do casco). Arestas com face1 == face2 são sempre detalhe.
         */
        fun edge(v1: Int, v2: Int, face1: Int, face2: Int, visibility: Int, detail: Boolean = false) {
            edgeData += v1; edgeData += v2; edgeData += face1; edgeData += face2
            edgeVis += visibility
            edgeDet += detail || face1 == face2
            faces = maxOf(faces, face1 + 1, face2 + 1)
        }

        fun build(): ShipBlueprint =
            ShipBlueprint(
                name, verts.toFloatArray(), edgeData.toIntArray(), edgeVis.toIntArray(),
                edgeDet.toBooleanArray(), faces, visibilityDistance, targetableArea,
                energy, speed, bounty, laserByte, canisters, gunVertex, explosion,
            )
    }

    companion object {
        fun define(name: String, block: Builder.() -> Unit): ShipBlueprint = Builder(name).apply(block).build()
    }
}
