package com.eliteandroid.audio

/**
 * Os sons do Elite (versão em disco): a tabela SFX — os quatro parâmetros do SOUND de cada
 * efeito — e os quatro envelopes que o loader define (variável E% de elite-loader3.asm).
 * Sem [synth] (testes, ou som desligado) os pedidos são ignorados.
 */
class SoundEffects(synth: BeebSound? = null) {

    /** Os efeitos, com o número usado pela rotina NOISE (múltiplos de 8). */
    enum class Sfx(val channel: Int, val amplitude: Int, val pitch: Int, val duration: Int) {
        LASER(0x12, 0x01, 0x00, 0x10),          // 0  - nosso laser
        HIT_BY_LASER(0x12, 0x02, 0x2C, 0x08),   // 8  - laser nos atingindo
        DEATH_1(0x11, 0x03, 0xF0, 0x18),        // 16 - morte 1 / acerto ou abate 2
        DEATH_2(0x10, 0xF1, 0x07, 0x1A),        // 24 - morte 2 / acerto ou abate 1
        BEEP(0x03, 0xF1, 0xBC, 0x01),           // 32 - bipe curto e agudo
        BOOP(0x13, 0xF4, 0x0C, 0x08),           // 40 - bipe longo e grave
        LAUNCH(0x10, 0xF1, 0x06, 0x0C),         // 48 - míssil lançado / decolagem
        HYPERSPACE(0x10, 0x02, 0x60, 0x10),     // 56 - motor do hiperespaço
        ECM_ON(0x13, 0x04, 0xC2, 0xFF),         // 64 - E.C.M. ligado
        ECM_OFF(0x13, 0x00, 0x00, 0x00),        // 72 - E.C.M. desligado
    }

    @Volatile var synth: BeebSound? = synth
        set(value) { field = value; value?.let(::defineEnvelopes) }

    /** DNOIZ: som desligado. */
    @Volatile var muted = false

    init { synth?.let(::defineEnvelopes) }

    /** NOISE: toca o efeito [sfx]. */
    fun noise(sfx: Sfx) {
        val s = synth ?: return
        if (muted) return
        s.sound(sfx.channel, sfx.amplitude, sfx.pitch, sfx.duration)
    }

    /**
     * EXNO: o som de acertar ([x] = 15) ou abater ([x] = 7) uma nave a [zHi] (z/256) de
     * distância — o ruído DEATH_2 com amplitude −15 + ((z_hi/4 AND x) com o bit 0
     * ligado), mais baixo quanto mais longe.
     */
    fun exno(zHi: Int, x: Int) {
        val s = synth ?: return
        if (muted) return
        val a = (((zHi.coerceIn(0, 255) ushr 2) and x) or 0b11110001).toByte().toInt()
        s.sound(Sfx.DEATH_2.channel, a, Sfx.DEATH_2.pitch, Sfx.DEATH_2.duration)
    }

    /** EXNO3: explosão (morte, colisão, contêiner destruído, dano no casco). */
    fun exno3() {
        noise(Sfx.DEATH_1)
        noise(Sfx.DEATH_2)
    }

    fun beep() = noise(Sfx.BEEP)

    fun boop() = noise(Sfx.BOOP)

    private fun defineEnvelopes(s: BeebSound) {
        ENVELOPES.forEachIndexed { i, e -> s.envelope(i + 1, e) }
    }

    companion object {
        /**
         * E%: ENVELOPE 1 (nosso laser), 2 (laser nos atingindo, hiperespaço), 3 (morte 1)
         * e 4 (E.C.M.), na ordem T, PI1-3, PN1-3, AA, AD, AS, AR, ALA, ALD.
         */
        val ENVELOPES = listOf(
            BeebSound.Envelope(1, 0, 111, -8, 4, 1, 8, 8, -2, 0, -1, 126, 44),
            BeebSound.Envelope(1, 14, -18, -1, 44, 32, 50, 6, 1, 0, -2, 120, 126),
            BeebSound.Envelope(1, 1, -1, -3, 17, 32, 128, 1, 0, 0, -1, 1, 1),
            BeebSound.Envelope(1, 4, -8, 44, 4, 6, 8, 22, 0, 0, -127, 126, 0),
        )
    }
}
