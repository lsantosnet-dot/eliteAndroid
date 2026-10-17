package com.eliteandroid

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.eliteandroid.audio.AudioOutput
import com.eliteandroid.audio.BeebSound
import com.eliteandroid.galaxy.Galaxy
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.render.SpaceView
import com.eliteandroid.save.PreferencesStorage

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

    /** Para a interface Android: null até o create() rodar na thread do GL. */
    @Volatile var sessionOrNull: GameSession? = null
        private set
    private lateinit var view: SpaceView

    /** O chip de som do BBC emulado e a saída de áudio. */
    private val synth = BeebSound()
    private var audio: AudioOutput? = null

    override fun create() {
        session = GameSession(galaxy, currentSystem)
        // Continua de onde parou: carrega o último comandante salvo, se houver.
        session.storage = PreferencesStorage()
        session.loadSaved()
        session.sound.synth = synth
        session.sound.muted = !Options.soundOn
        audio = AudioOutput(synth).also { it.start() }
        view = SpaceView()
        resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        sessionOrNull = session
    }

    override fun resize(width: Int, height: Int) {
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        view.resize(w, h)
        session.layout(w.toFloat(), h.toFloat())
    }

    override fun render() {
        session.update(Gdx.graphics.deltaTime)
        session.render(view)
    }

    /** O app foi para segundo plano: se estiver atracado, grava o comandante. */
    override fun pause() {
        if (::session.isInitialized) session.save()
        audio?.stop()
        synth.silence()
    }

    override fun resume() {
        audio?.start()
    }

    /** Liga ou desliga o som (DNOIZ), lembrando a escolha. */
    fun setSoundOn(on: Boolean) {
        Options.soundOn = on
        sessionOrNull?.sound?.muted = !on
        if (!on) synth.silence()
    }

    val soundOn: Boolean get() = Options.soundOn

    override fun dispose() {
        audio?.stop()
        view.dispose()
    }

    /** Opções do jogador, nas Preferences do LibGDX. */
    private object Options {
        private val prefs get() = Gdx.app.getPreferences("elite-options")
        var soundOn: Boolean
            get() = prefs.getBoolean("sound", true)
            set(v) { prefs.putBoolean("sound", v); prefs.flush() }
    }
}
