package com.eliteandroid.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.eliteandroid.flight.View
import com.eliteandroid.ships.ShipInstance

/**
 * Vista espacial: projeta naves + poeira com o [WireframeProjector], desenha com o
 * [NeonLineBatch], acrescenta texto e sobreposições e aplica o [BloomRenderer].
 */
class SpaceView : Disposable {

    val projector = WireframeProjector()
    val lines = NeonLineBatch()
    private val bloom = BloomRenderer()
    val sprites = SpriteBatch()
    val font = BitmapFont().apply {
        region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    val width: Float get() = projector.viewportWidth
    val height: Float get() = projector.viewportHeight

    fun resize(width: Int, height: Int) {
        projector.viewportWidth = width.toFloat()
        projector.viewportHeight = height.toFloat()
        // Espessura proporcional à resolução: ~3.4 px de meia largura em 1080p.
        lines.halfWidth = maxOf(2f, height / 320f)
        font.data.setScale(maxOf(1f, height / 400f))
        sprites.projectionMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        bloom.resize(width, height)
    }

    /**
     * Desenha um quadro. [overlayLines] e [overlayText] acrescentam linhas e texto por cima
     * (também recebem o brilho do bloom).
     */
    fun render(
        ships: List<ShipInstance>,
        dust: Stardust?,
        view: View,
        overlayLines: (NeonLineBatch) -> Unit = {},
        overlayText: (SpriteBatch, BitmapFont) -> Unit = { _, _ -> },
    ) {
        projector.view = view
        dust?.aspect = width / height
        bloom.beginScene()
        Gdx.gl.glViewport(0, 0, width.toInt(), height.toInt())
        lines.begin(width, height)
        dust?.draw(projector, lines)
        for (ship in ships) projector.project(ship, lines)
        overlayLines(lines)
        lines.end()

        sprites.enableBlending()
        sprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        sprites.begin()
        overlayText(sprites, font)
        sprites.end()
        bloom.endScene()
    }

    override fun dispose() {
        lines.dispose()
        bloom.dispose()
        sprites.dispose()
        font.dispose()
    }
}
