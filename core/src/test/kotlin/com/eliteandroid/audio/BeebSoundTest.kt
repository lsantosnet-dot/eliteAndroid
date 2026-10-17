package com.eliteandroid.audio

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.audio.SoundEffects.Sfx
import com.eliteandroid.flight.FlightControls
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.ships.ShipInstance
import com.eliteandroid.ships.ShipType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class BeebSoundTest {

    private fun fx() = SoundEffects(BeebSound())

    /** Energia RMS de [seconds] de áudio. */
    private fun rms(s: BeebSound, seconds: Float): Float {
        val buf = FloatArray((s.sampleRate * seconds).toInt())
        s.render(buf)
        return sqrt(buf.map { it * it }.average()).toFloat()
    }

    @Test
    fun `MOS pitch maps to the chip frequencies`() {
        assertEquals(261.6, BeebSound.toneFrequency(53), 1.0) // dó central
        assertEquals(523.2, BeebSound.toneFrequency(101), 3.0) // uma oitava = 48 passos
    }

    @Test
    fun `our laser sounds, then the envelope releases to silence`() {
        val f = fx(); val s = f.synth!!
        f.noise(Sfx.LASER)
        assertTrue(rms(s, 0.3f) > 0.01f)
        // Duração 16/20 s + liberação de 44 passos de 1 cs.
        rms(s, 1.2f)
        assertFalse(s.busy)
    }

    @Test
    fun `ECM sound lasts until it is switched off`() {
        val f = fx(); val s = f.synth!!
        f.noise(Sfx.ECM_ON)
        rms(s, 2f)
        assertTrue(rms(s, 0.2f) > 0.01f)
        f.noise(Sfx.ECM_OFF)
        rms(s, 0.05f)
        assertTrue(rms(s, 0.2f) < 1e-4f)
    }

    @Test
    fun `explosions are quieter far away`() {
        val near = fx(); near.exno(0, 15)
        val far = fx(); far.exno(60, 15)
        assertTrue(rms(near.synth!!, 0.3f) > 2f * rms(far.synth!!, 0.3f))
    }

    @Test
    fun `the short beep is queued and brief`() {
        val f = fx(); val s = f.synth!!
        f.beep(); f.beep()
        assertTrue(rms(s, 0.05f) > 0.01f)
        rms(s, 0.2f)
        assertFalse(s.busy)
    }

    @Test
    fun `muted sound makes no noise`() {
        val f = fx(); f.muted = true
        f.noise(Sfx.LASER); f.exno3()
        assertFalse(f.synth!!.busy)
    }

    @Test
    fun `flight events trigger the original sounds`() {
        val m = FlightModel()
        val s = BeebSound(); m.sound.synth = s
        // Nosso laser.
        m.step(0.2f, FlightControls().apply { fire = true })
        assertTrue(s.busy)
        s.silence()
        // Um laser inimigo nos atinge.
        val pirate = ShipInstance(ShipType.MAMBA, Vector3(0f, 0f, 2000f)).also { it.aiFlag = 0xFE; m.addShip(it) }
        m.combat.tactics(pirate)
        assertTrue(s.busy)
        s.silence()
        // Colisão.
        m.addShip(ShipInstance(ShipType.ASTEROID, Vector3(0f, 0f, 30f)))
        m.combat.checkCollisions()
        assertTrue(s.busy)
    }
}
