package com.eliteandroid

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.eliteandroid.galaxy.Galaxy
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.render.SpaceView

/**
 * Ponto de entrada LibGDX (multiplataforma). Mantém o estado da galáxia e a sessão de voo.
 */
class EliteGame : ApplicationAdapter() {

    var galaxy: Galaxy = GalaxyGenerator.buildGalaxy(1)
        private set

    /** O comandante começa em Lave (galáxia 1, sistema 7), como no original. */
    var currentSystem: PlanetSystem = galaxy[7]
        private set

    lateinit var session: GameSession
        private set
    private lateinit var view: SpaceView

    override fun create() {
        session = GameSession(currentSystem.name)
        view = SpaceView()
        resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.input.inputProcessor = session.input
    }

    override fun resize(width: Int, height: Int) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        view.resize(w, h)
        session.input.layout(w.toFloat(), h.toFloat())
    }

    override fun render() {
        session.update(Gdx.graphics.deltaTime)
        session.render(view)
    }

    override fun dispose() {
        view.dispose()
    }
}
