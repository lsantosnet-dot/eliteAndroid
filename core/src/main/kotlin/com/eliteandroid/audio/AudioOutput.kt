package com.eliteandroid.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice

/**
 * Toca o [BeebSound] num AudioDevice do LibGDX (Android e desktop): uma thread gera
 * blocos curtos de amostras e os escreve no dispositivo, que dita o ritmo.
 */
class AudioOutput(private val synth: BeebSound) {

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        val device: AudioDevice = try {
            Gdx.audio.newAudioDevice(synth.sampleRate, true)
        } catch (e: Throwable) {
            Gdx.app?.error("AudioOutput", "sem dispositivo de áudio", e)
            return
        }
        running = true
        thread = Thread({
            val buffer = FloatArray(BLOCK)
            try {
                while (running) {
                    synth.render(buffer)
                    device.writeSamples(buffer, 0, buffer.size)
                }
            } catch (e: Throwable) {
                Gdx.app?.error("AudioOutput", "falha no áudio", e)
            } finally {
                device.dispose()
            }
        }, "beeb-sound").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private companion object {
        /** ~23 ms a 22050 Hz. */
        const val BLOCK = 512
    }
}
