package com.eliteandroid.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.sqrt

/**
 * Desenha segmentos 2D como quads grossos com perfil de brilho (ver [Shaders.LINE_FRAGMENT]),
 * em mistura aditiva — linhas sobrepostas ficam mais claras, como fósforo de CRT.
 *
 * Linhas GL_LINES nativas têm largura limitada e sem antialiasing em muitas GPUs Android;
 * os quads dão a mesma aparência em todos os aparelhos.
 */
class NeonLineBatch(private val maxSegments: Int = 4096) : SegmentSink, Disposable {

    private val floatsPerVertex = 4 // x, y, cor empacotada, edge
    private val vertices = FloatArray(maxSegments * 4 * floatsPerVertex)
    private var segments = 0

    private val mesh = Mesh(
        false, maxSegments * 4, maxSegments * 6,
        VertexAttribute(Usage.Position, 2, ShaderProgramAttrs.POSITION),
        VertexAttribute(Usage.ColorPacked, 4, ShaderProgramAttrs.COLOR),
        VertexAttribute(Usage.Generic, 1, "a_edge"),
    ).apply {
        val idx = ShortArray(maxSegments * 6)
        for (i in 0 until maxSegments) {
            val v = (i * 4).toShort(); val o = i * 6
            idx[o] = v; idx[o + 1] = (v + 1).toShort(); idx[o + 2] = (v + 2).toShort()
            idx[o + 3] = (v + 2).toShort(); idx[o + 4] = (v + 3).toShort(); idx[o + 5] = v
        }
        setIndices(idx)
    }

    private val shader = Shaders.compile("line", Shaders.LINE_VERTEX, Shaders.LINE_FRAGMENT)
    private val projection = Matrix4()

    /** Meia largura da linha em pixels (inclui a borda suave). */
    var halfWidth = 2.5f

    fun begin(width: Float, height: Float) {
        projection.setToOrtho2D(0f, 0f, width, height)
        segments = 0
    }

    override fun segment(x0: Float, y0: Float, x1: Float, y1: Float, color: Float) =
        segment(x0, y0, x1, y1, color, halfWidth)

    /** Segmento com meia largura [hw] própria (barras do painel, hastes do radar). */
    fun segment(x0: Float, y0: Float, x1: Float, y1: Float, color: Float, hw: Float) {
        if (segments == maxSegments) flush()
        var dx = x1 - x0; var dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
        // Estende as pontas meia largura para fechar as junções entre arestas.
        val ex = dx * hw; val ey = dy * hw
        val nx = -dy * hw; val ny = dx * hw

        var i = segments * 4 * floatsPerVertex
        i = put(i, x0 - ex + nx, y0 - ey + ny, color, 1f)
        i = put(i, x0 - ex - nx, y0 - ey - ny, color, -1f)
        i = put(i, x1 + ex - nx, y1 + ey - ny, color, -1f)
        put(i, x1 + ex + nx, y1 + ey + ny, color, 1f)
        segments++
    }

    /** Ponto luminoso (poeira estelar): um segmento curto de comprimento [size] px. */
    fun dot(x: Float, y: Float, color: Float, size: Float = halfWidth) = segment(x - size * 0.5f, y, x + size * 0.5f, y, color)

    private fun put(i: Int, x: Float, y: Float, color: Float, edge: Float): Int {
        vertices[i] = x; vertices[i + 1] = y; vertices[i + 2] = color; vertices[i + 3] = edge
        return i + 4
    }

    fun end() = flush()

    private fun flush() {
        if (segments == 0) return
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE)
        mesh.setVertices(vertices, 0, segments * 4 * floatsPerVertex)
        shader.bind()
        shader.setUniformMatrix("u_projTrans", projection)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, segments * 6)
        segments = 0
    }

    override fun dispose() {
        mesh.dispose()
        shader.dispose()
    }
}

internal object ShaderProgramAttrs {
    const val POSITION = "a_position"
    const val COLOR = "a_color"
    const val TEXCOORD = "a_texCoord0"
}
