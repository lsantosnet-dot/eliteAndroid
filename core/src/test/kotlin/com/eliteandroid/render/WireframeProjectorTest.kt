package com.eliteandroid.render

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.ships.Blueprints
import com.eliteandroid.ships.ShipInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WireframeProjectorTest {

    private val projector = WireframeProjector().apply { viewportWidth = 1000f; viewportHeight = 600f }

    @Test
    fun `coriolis seen from the front shows front faces and the docking slot`() {
        // Estação à frente, com a fenda (face 0, +z do modelo) apontando para o jogador.
        val station = ShipInstance(Blueprints.CORIOLIS, Vector3(0f, 0f, 1000f), 0f)
            .orient(Vector3(0f, 0f, -1f))
        // Visíveis: face 0 + os 4 triângulos frontais → 12 arestas do casco + 4 da fenda.
        assertEquals(16, projector.project(station, SegmentSink { _, _, _, _, _ -> }))
    }

    @Test
    fun `coriolis seen from behind hides the slot`() {
        val station = ShipInstance(Blueprints.CORIOLIS, Vector3(0f, 0f, 1000f), 0f) // fenda apontando para longe
        var slot = 0
        val sink = SegmentSink { x0, y0, x1, y1, _ ->
            // Arestas da fenda ficam a <= 10% do raio projetado em torno do centro.
            if (maxOf(kotlin.math.abs(x0 - 500), kotlin.math.abs(x1 - 500), kotlin.math.abs(y0 - 300), kotlin.math.abs(y1 - 300)) < 40) slot++
        }
        assertEquals(12, projector.project(station, sink)) // face 13 + 4 triângulos traseiros
        assertEquals(0, slot)
    }

    @Test
    fun `projection matches x over z times focal`() {
        val out = Vector3()
        assertTrue(projector.projectPoint(100f, 50f, 1000f, out))
        assertEquals(500f + 100f, out.x, 1e-3f)
        assertEquals(300f + 50f, out.y, 1e-3f)
    }

    @Test
    fun `ship behind the player is culled`() {
        val ship = ShipInstance(Blueprints.COBRA_MK3, Vector3(0f, 0f, -500f), 0f)
        assertEquals(0, projector.project(ship, SegmentSink { _, _, _, _, _ -> }))
    }

    @Test
    fun `far ships drop low-visibility detail edges`() {
        val near = ShipInstance(Blueprints.COBRA_MK3, Vector3(0f, 0f, 300f), 0f) // traseira visível
        val far = ShipInstance(Blueprints.COBRA_MK3, Vector3(0f, 0f, 3000f), 0f)
        val nNear = projector.project(near, SegmentSink { _, _, _, _, _ -> })
        val nFar = projector.project(far, SegmentSink { _, _, _, _, _ -> })
        assertTrue(nFar < nNear, "near=$nNear far=$nFar")
    }
}
