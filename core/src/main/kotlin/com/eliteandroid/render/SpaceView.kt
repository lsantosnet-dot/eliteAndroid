package com.eliteandroid.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Disposable
import com.eliteandroid.ships.ShipInstance

/**
 * Vista espacial: projeta naves + poeira com o [WireframeProjector], desenha com o
 * [NeonLineBatch] e aplica o [BloomRenderer].
 */
class SpaceView : Disposable {

    val projector = WireframeProjector()
    private val lines = NeonLineBatch()
    private val bloom = BloomRenderer()

    fun resize(width: Int, height: Int) {
        projector.viewportWidth = width.toFloat()
        projector.viewportHeight = height.toFloat()
        // Espessura proporcional à resolução: ~3.4 px de meia largura em 1080p.
        lines.halfWidth = maxOf(2f, height / 320f)
        bloom.resize(width, height)
    }

    fun render(ships: List<ShipInstance>, dust: Stardust?) {
        bloom.beginScene()
        Gdx.gl.glViewport(0, 0, projector.viewportWidth.toInt(), projector.viewportHeight.toInt())
        lines.begin(projector.viewportWidth, projector.viewportHeight)
        dust?.draw(projector, lines)
        for (ship in ships) projector.project(ship, lines)
        lines.end()
        bloom.endScene()
    }

    override fun dispose() {
        lines.dispose()
        bloom.dispose()
    }
}
