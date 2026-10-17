package com.eliteandroid

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.eliteandroid.galaxy.Galaxy
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.render.SpaceView

/**
 * Ponto de entrada LibGDX (multiplataforma). Mantém o estado da galáxia e desenha a
 * vista espacial em wireframe neon.
 */
class EliteGame : ApplicationAdapter() {

    var galaxy: Galaxy = GalaxyGenerator.buildGalaxy(1)
        private set

    /** O comandante começa em Lave (galáxia 1, sistema 7), como no original. */
    var currentSystem: PlanetSystem = galaxy[7]
        private set

    lateinit var scene: DemoScene
        private set
    private lateinit var view: SpaceView

    override fun create() {
        scene = DemoScene()
        view = SpaceView()
        view.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
    }

    override fun resize(width: Int, height: Int) {
        view.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
    }

    override fun render() {
        scene.update(minOf(Gdx.graphics.deltaTime, 1f / 15f))
        view.render(scene.ships, scene.dust)
    }

    override fun dispose() {
        view.dispose()
    }
}
