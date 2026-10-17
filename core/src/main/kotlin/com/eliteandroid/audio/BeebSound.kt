package com.eliteandroid.audio

import kotlin.math.pow

/**
 * Emulação do som do BBC Micro: o chip SN76489 (três canais de onda quadrada e um de
 * ruído) comandado como pelo sistema operacional (MOS) — os comandos SOUND (OSWORD 7) e
 * ENVELOPE (OSWORD 8) que o Elite usa.
 *
 *  - SOUND canal, amplitude, altura, duração: canal 0 = ruído, 1-3 = tons; bit 4 do canal
 *    ("flush") interrompe o que estiver tocando, senão a nota entra na fila. Amplitude de
 *    −15 (máxima) a 0, ou 1-4 para usar um envelope. Altura em quartos de semitom (53 =
 *    dó central); no canal de ruído, os 3 bits baixos escolhem o ruído. Duração em
 *    vinteavos de segundo (255 = até ser interrompida).
 *  - ENVELOPE: a cada T centésimos, três seções de altura (PI1..PI3 por passo, PN1..PN3
 *    passos, repetindo se o bit 7 de T estiver livre) e as fases de amplitude ataque
 *    (AA até ALA), decaimento (AD até ALD), sustentação (AS) e, acabada a duração,
 *    liberação (AR até 0). Amplitude 0..126, oito unidades por nível do chip.
 *
 * O MOS trabalha a 100 Hz; aqui cada centésimo de segundo corresponde a
 * [sampleRate]/100 amostras geradas, então o tempo do som segue o áudio.
 */
class BeebSound(val sampleRate: Int = 22050) {

    /** Os 14 parâmetros de um ENVELOPE (sem o número). */
    class Envelope(
        val t: Int,
        val pi1: Int, val pi2: Int, val pi3: Int,
        val pn1: Int, val pn2: Int, val pn3: Int,
        val aa: Int, val ad: Int, val `as`: Int, val ar: Int,
        val ala: Int, val ald: Int,
    )

    private class Note(val amplitude: Int, val pitch: Int, val duration: Int)

    private enum class Phase { ATTACK, DECAY, SUSTAIN, RELEASE, DONE }

    private inner class Channel(val index: Int) {
        val queue = ArrayDeque<Note>()
        var note: Note? = null
        var envelope: Envelope? = null
        var pitch = 0
        var volume = 0          // nível do chip, 0..15
        var amp = 0             // amplitude do envelope, 0..126
        var phase = Phase.DONE
        var durationLeft = 0    // em centésimos (−1 = infinito)
        var stepTimer = 0
        var section = 0
        var sectionSteps = 0

        // Estado do gerador (fase da onda quadrada / LFSR do ruído).
        var accumulator = 0.0
        var level = 1f
        var lfsr = 0x4000

        val active: Boolean get() = note != null

        fun start(n: Note) {
            note = n
            pitch = n.pitch and 0xFF
            durationLeft = if (n.duration == 255) -1 else n.duration * 5
            if (n.amplitude in 1..4) {
                envelope = envelopes[n.amplitude - 1]
                amp = 0
                phase = Phase.ATTACK
                stepTimer = 0
                section = 0
                sectionSteps = 0
                volume = 0
            } else {
                envelope = null
                volume = (-n.amplitude).coerceIn(0, 15)
                phase = Phase.SUSTAIN
            }
            if (index == 1) channel1Pitch = pitch
            if (index == 0) lfsr = 0x4000
        }

        fun stop() {
            note = null
            envelope = null
            volume = 0
            phase = Phase.DONE
        }

        /** Um centésimo de segundo do MOS. */
        fun tick() {
            if (note == null) {
                queue.removeFirstOrNull()?.let { start(it) } ?: return
            }
            // Duração (em vinteavos, contada aqui em centésimos).
            if (durationLeft > 0) durationLeft--
            if (durationLeft == 0) {
                if (queue.isNotEmpty() || envelope == null || phase == Phase.DONE) {
                    stop()
                    queue.removeFirstOrNull()?.let { start(it) }
                    return
                }
                if (phase != Phase.RELEASE) phase = Phase.RELEASE
            }
            val env = envelope ?: return
            if (++stepTimer < (env.t and 0x7F).coerceAtLeast(1)) return
            stepTimer = 0
            stepPitch(env)
            stepAmplitude(env)
            volume = amp / 8
            if (phase == Phase.DONE) {
                stop()
                queue.removeFirstOrNull()?.let { start(it) }
            }
        }

        private fun stepPitch(env: Envelope) {
            val counts = intArrayOf(env.pn1, env.pn2, env.pn3)
            val deltas = intArrayOf(env.pi1, env.pi2, env.pi3)
            var guard = 0
            while (section < 3 && sectionSteps >= counts[section] && guard++ < 4) {
                section++; sectionSteps = 0
            }
            if (section >= 3) {
                if (env.t and 0x80 != 0) return // sem repetição
                section = 0; sectionSteps = 0
                guard = 0
                while (section < 3 && counts[section] == 0 && guard++ < 4) section++
                if (section >= 3) return
            }
            pitch = (pitch + deltas[section]) and 0xFF
            sectionSteps++
            if (index == 1) channel1Pitch = pitch
        }

        private fun stepAmplitude(env: Envelope) {
            when (phase) {
                Phase.ATTACK -> {
                    amp = (amp + env.aa).coerceIn(0, 126)
                    if (reached(amp, env.aa, env.ala)) { amp = env.ala; phase = Phase.DECAY }
                }
                Phase.DECAY -> {
                    amp = (amp + env.ad).coerceIn(0, 126)
                    if (reached(amp, env.ad, env.ald)) { amp = env.ald; phase = Phase.SUSTAIN }
                }
                Phase.SUSTAIN -> amp = (amp + env.`as`).coerceIn(0, 126)
                Phase.RELEASE -> {
                    amp = (amp + env.ar).coerceIn(0, 126)
                    if (amp == 0 || env.ar == 0) phase = Phase.DONE
                }
                Phase.DONE -> Unit
            }
        }

        /** Fim de fase ao atingir o alvo; com variação 0, a fase só termina se já estiver nele. */
        private fun reached(value: Int, delta: Int, target: Int) =
            value == target || (delta > 0 && value >= target) || (delta < 0 && value <= target)
    }

    private val envelopes = arrayOfNulls<Envelope>(4)
    private val channels = Array(4) { Channel(it) }

    /** A altura do canal 1 fica no registrador do chip: o ruído 3/7 usa essa frequência. */
    private var channel1Pitch = 0xF0

    private val samplesPerTick = sampleRate / 100.0
    private var tickAccumulator = 0.0

    /** Volume geral (0..1). */
    @Volatile var masterVolume = 0.6f

    /** OSWORD 8: define o envelope [n] (1..4). */
    @Synchronized fun envelope(n: Int, e: Envelope) {
        envelopes[n - 1] = e
    }

    /** OSWORD 7 / SOUND. */
    @Synchronized fun sound(channelFlush: Int, amplitude: Int, pitch: Int, duration: Int) {
        val ch = channels[channelFlush and 3]
        val note = Note(amplitude.toByte().toInt(), pitch and 0xFF, duration and 0xFF)
        if (channelFlush and 0x10 != 0) {
            ch.queue.clear()
            ch.stop()
            ch.start(note)
        } else if (ch.queue.size < 4) {
            ch.queue.addLast(note)
        }
    }

    /** Silencia tudo (sem afetar os envelopes definidos). */
    @Synchronized fun silence() {
        channels.forEach { it.queue.clear(); it.stop() }
    }

    val busy: Boolean @Synchronized get() = channels.any { it.active || it.queue.isNotEmpty() }

    /** Gera [count] amostras em [out] (−1..1). */
    @Synchronized fun render(out: FloatArray, count: Int = out.size) {
        for (i in 0 until count) {
            tickAccumulator += 1.0
            if (tickAccumulator >= samplesPerTick) {
                tickAccumulator -= samplesPerTick
                channels.forEach { it.tick() }
            }
            var mix = 0f
            for (ch in channels) {
                if (!ch.active || ch.volume == 0) continue
                mix += sample(ch) * VOLUME_TABLE[ch.volume]
            }
            out[i] = mix * 0.25f * masterVolume
        }
    }

    private fun sample(ch: Channel): Float {
        if (ch.index != 0) {
            // Onda quadrada: o contador inverte a saída a cada meio período.
            ch.accumulator += 2.0 * toneFrequency(ch.pitch) / sampleRate
            while (ch.accumulator >= 1.0) { ch.accumulator -= 1.0; ch.level = -ch.level }
            return ch.level
        }
        // Ruído: bits 0-1 = taxa (alta, média, baixa ou a do canal 1), bit 2 = branco.
        val control = ch.pitch and 7
        val rate = when (control and 3) {
            0 -> CLOCK / 512.0
            1 -> CLOCK / 1024.0
            2 -> CLOCK / 2048.0
            else -> 2.0 * toneFrequency(channel1Pitch)
        }
        ch.accumulator += rate / sampleRate
        while (ch.accumulator >= 1.0) {
            ch.accumulator -= 1.0
            val feedback = if (control and 4 != 0) (ch.lfsr xor (ch.lfsr shr 1)) and 1 else ch.lfsr and 1
            ch.lfsr = (ch.lfsr shr 1) or (feedback shl 14)
        }
        return if (ch.lfsr and 1 != 0) 1f else -1f
    }

    companion object {
        /** Clock do SN76489 no BBC Micro. */
        const val CLOCK = 4_000_000.0

        /** Atenuação de 2 dB por nível; 0 = mudo. */
        private val VOLUME_TABLE = FloatArray(16) { v -> if (v == 0) 0f else 10.0.pow(-(15 - v) * 0.1).toFloat() }

        /**
         * Altura do MOS → frequência real do chip: 53 = dó central, 4 passos por semitom;
         * o período de 10 bits do SN76489 arredonda a frequência.
         */
        fun toneFrequency(pitch: Int): Double {
            val ideal = 261.63 * 2.0.pow((pitch - 53) / 48.0)
            val period = (CLOCK / 32.0 / ideal).toInt().coerceIn(1, 1023)
            return CLOCK / 32.0 / period
        }
    }
}
