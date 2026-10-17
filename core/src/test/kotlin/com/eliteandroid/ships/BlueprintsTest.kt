package com.eliteandroid.ships

import com.badlogic.gdx.math.Vector3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class BlueprintsTest {

    @Test
    fun `coriolis has original counts and equal hull edges`() {
        val c = Blueprints.CORIOLIS
        assertEquals(16, c.vertexCount)
        assertEquals(28, c.edgeCount)
        assertEquals(14, c.faceCount)
        val a = Vector3(); val b = Vector3()
        for (e in 0 until 24) { // as 24 arestas do cuboctaedro têm o mesmo comprimento: 160·√2
            val len = c.vertex(c.edges[e * 4], a).dst(c.vertex(c.edges[e * 4 + 1], b))
            assertEquals(160f * sqrt(2f), len, 0.01f, "aresta $e")
        }
    }

    @Test
    fun `cobra has original counts`() {
        val c = Blueprints.COBRA_MK3
        assertEquals(28, c.vertexCount)
        assertEquals(38, c.edgeCount)
        assertEquals(13, c.faceCount)
    }

    /**
     * Confere a transcrição: os vértices de cada face devem ser coplanares e a face deve
     * ser um plano de suporte do casco convexo (nenhum vértice do casco "fora" dela).
     */
    @ParameterizedTest
    @MethodSource("blueprints")
    fun `faces are planar supporting planes`(bp: ShipBlueprint) {
        val v = Vector3(); val p = Vector3(); val n = Vector3()
        val hull = (0 until bp.faceCount).flatMap { bp.faceVertices(it) }.toSet()
        for (f in 0 until bp.faceCount) {
            n.set(bp.faceNormals[f * 3], bp.faceNormals[f * 3 + 1], bp.faceNormals[f * 3 + 2])
            p.set(bp.facePoints[f * 3], bp.facePoints[f * 3 + 1], bp.facePoints[f * 3 + 2])
            bp.faceVertices(f).forEach {
                val d = n.dot(bp.vertex(it, v).sub(p))
                assertTrue(abs(d) < 1.5f, "${bp.name}: face $f vértice $it fora do plano ($d)")
            }
            hull.forEach {
                val d = n.dot(bp.vertex(it, v).sub(p))
                assertTrue(d < 1.5f, "${bp.name}: vértice $it está fora da face $f ($d)")
            }
        }
    }

    companion object {
        @JvmStatic
        fun blueprints() = listOf(Blueprints.CORIOLIS, Blueprints.COBRA_MK3)
    }
}
