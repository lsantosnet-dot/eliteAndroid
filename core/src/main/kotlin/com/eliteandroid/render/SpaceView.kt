package com.eliteandroid.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eliteandroid.flight.View
import com.eliteandroid.ships.ShipInstance

/** Camadas desenhadas por cima da vista espacial (HUD, painel, efeitos). */
interface HudLayer {
    /** Linhas sobre o espaço (mira, túnel, controles). */
    fun worldLines(lines: NeonLineBatch) {}
    /** Retângulos escurecidos atrás do painel. */
    fun panels(out: (Rectangle) -> Unit) {}
    /** Linhas do painel, desenhadas depois do escurecimento. */
    fun panelLines(lines: NeonLineBatch) {}
    fun text(batch: SpriteBatch, font: BitmapFont) {}
}

/**
 * Vista espacial: projeta naves + poeira com o [WireframeProjector], desenha com o
 * [NeonLineBatch], acrescenta as camadas do [HudLayer] e aplica o [BloomRenderer].
 */
class SpaceView : Disposable {

    val projector = WireframeProjector()
    val lines = NeonLineBatch()
    private val bloom = BloomRenderer()
    val sprites = SpriteBatch()
    val font = BitmapFont().apply {
        region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val pixel = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() })
    private val panelColor = Color(0f, 0.01f, 0.03f, 0.82f)

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

    fun render(ships: List<ShipInstance>, dust: Stardust?, view: View, hud: HudLayer) {
        projector.view = view
        dust?.aspect = width / height
        bloom.beginScene()
        Gdx.gl.glViewport(0, 0, width.toInt(), height.toInt())

        lines.begin(width, height)
        dust?.draw(projector, lines)
        for (ship in ships) projector.project(ship, lines)
        hud.worldLines(lines)
        lines.end()

        sprites.enableBlending()
        sprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sprites.begin()
        sprites.color = panelColor
        hud.panels { r -> sprites.draw(pixel, r.x, r.y, r.width, r.height) }
        sprites.color = Color.WHITE
        sprites.end()

        lines.begin(width, height)
        hud.panelLines(lines)
        lines.end()

        sprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        sprites.begin()
        hud.text(sprites, font)
        sprites.end()
        bloom.endScene()
    }

    override fun dispose() {
        lines.dispose()
        bloom.dispose()
        sprites.dispose()
        font.dispose()
        pixel.dispose()
    }
}
