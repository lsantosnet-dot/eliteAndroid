package com.eliteandroid

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.eliteandroid.galaxy.Galaxy
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem

/**
 * Ponto de entrada LibGDX (multiplataforma). Na Etapa 1 só mantém o estado da galáxia
 * e limpa a tela; o renderizador wireframe entra na Etapa 2.
 */
class EliteGame : ApplicationAdapter() {

    var galaxy: Galaxy = GalaxyGenerator.buildGalaxy(1)
        private set

    /** O comandante começa em Lave (galáxia 1, sistema 7), como no original. */
    var currentSystem: PlanetSystem = galaxy[7]
        private set

    override fun render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
    }
}
