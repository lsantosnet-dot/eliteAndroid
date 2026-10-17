package com.eliteandroid.ships

import com.badlogic.gdx.math.Vector3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs
import kotlin.math.sqrt

class BlueprintsTest {

    @Test
    fun `coriolis has original counts and equal hull edges`() {
        val c = DiscShips.CORIOLIS
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
        val c = DiscShips.COBRA_MK_3
        assertEquals(28, c.vertexCount)
        assertEquals(38, c.edgeCount)
        assertEquals(13, c.faceCount)
    }

    @Test
    fun `thirty models from the sixteen disc ship files`() {
        val all = DiscShips.SHIP_FILES.flatMap { it.values }.toSet()
        assertEquals(30, all.size)
        assertEquals(16, DiscShips.SHIP_FILES.size)
        // Todo arquivo tem uma estação (tipo 2) e a polícia (tipo 16).
        DiscShips.SHIP_FILES.forEach { f ->
            assertTrue(f[2] === DiscShips.CORIOLIS || f[2] === DiscShips.DODO)
            assertTrue(f[16] === DiscShips.VIPER)
        }
        // Arquivos com o bit 0 ligado (tech level ≥ 10) têm a Dodecaedro.
        DiscShips.SHIP_FILES.forEachIndexed { i, f -> assertEquals(i and 1 == 1, f[2] === DiscShips.DODO, "arquivo $i") }
    }

    @Test
    fun `scooped items from blueprint byte 0`() {
        assertEquals(9, DiscShips.PLATE.scoopItem)       // Alloys
        assertEquals(3, DiscShips.ESCAPE_POD.scoopItem)  // Slaves
        assertEquals(16, DiscShips.THARGON.scoopItem)    // Alien Items
        assertEquals(-1, DiscShips.ASTEROID.scoopItem)
    }

    /**
     * Confere as normais do bloco FACE: cada uma aponta para fora do casco e é quase
     * perpendicular às arestas de contorno da sua face. As normais do original são inteiros
     * e algumas arestas são decalques (como no Cobra) ou emprestadas de outro modelo (o
     * Splinter usa as da cápsula, que não combinam com as suas faces — fica de fora), então
     * exigimos 85% das arestas.
     */
    @ParameterizedTest
    @MethodSource("blueprints")
    fun `source normals are perpendicular to their faces`(bp: ShipBlueprint) {
        val n = Vector3(); val a = Vector3(); val b = Vector3()
        var good = 0; var total = 0
        for (f in 0 until bp.faceCount) {
            if (bp.faceAlwaysVisible[f]) continue
            n.set(bp.faceNormals[f * 3], bp.faceNormals[f * 3 + 1], bp.faceNormals[f * 3 + 2])
            for (e in 0 until bp.edgeCount) {
                val f1 = bp.edges[e * 4 + 2]; val f2 = bp.edges[e * 4 + 3]
                if (f1 == f2 || (f1 != f && f2 != f)) continue
                bp.vertex(bp.edges[e * 4], a); bp.vertex(bp.edges[e * 4 + 1], b)
                val d = b.sub(a)
                if (d.len() < 4f) continue // arestas minúsculas: a direção é imprecisa
                d.nor()
                total++
                if (abs(d.dot(n)) < 0.35f) good++
            }
            // Para fora: a normal aponta para o mesmo lado dos vértices da face.
            val vs = bp.faceVertices(f)
            if (vs.isNotEmpty()) {
                val c = Vector3()
                vs.forEach { c.add(bp.vertex(it, a)) }
                c.scl(1f / vs.size)
                assertTrue(c.dot(n) > -1f, "${bp.name}: face $f aponta para dentro")
            }
        }
        assertTrue(good >= total * 0.85f, "${bp.name}: só $good de $total arestas perpendiculares")
    }

    companion object {
        @JvmStatic
        fun blueprints() = DiscShips.SHIP_FILES.flatMap { it.values }.toSet().filter { it !== DiscShips.SPLINTER }
    }
}
