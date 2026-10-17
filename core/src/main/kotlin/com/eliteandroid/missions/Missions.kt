package com.eliteandroid.missions

import com.eliteandroid.galaxy.PlanetSystem
import kotlin.random.Random

/**
 * As duas missões da versão em disco do BBC Elite, com o byte TP do original:
 *
 *  - bit 0: missão 1 (o Constrictor roubado) em andamento;
 *  - bit 1: Constrictor destruído (missão 1 cumprida);
 *  - bit 2: missão 2 (os planos dos Thargoids) começou;
 *  - bit 3: planos recolhidos em Ceerdi.
 *
 * DOENTRY (ao atracar) decide os briefings:
 *  1. sem a missão 1, com ≥ 256 abates (TALLY+1 ≠ 0) nas galáxias 1 ou 2 → BRIEF;
 *  2. Constrictor destruído e missão ainda aberta → DEBRIEF (5000 Cr e 256 abates);
 *  3. missão 1 concluída, ≥ 1280 abates (TALLY+1 ≥ 5), na galáxia 3 → BRIEF2;
 *  4. a caminho de Ceerdi (215, 84) na galáxia 3 → BRIEF3 (recolhe os planos);
 *  5. com os planos em Birera (63, 72) na galáxia 3 → DEBRIEF2 (unidade de energia da
 *     marinha e 256 abates).
 */
object Missions {

    const val CONSTRICTOR_IN_PROGRESS = 1
    const val CONSTRICTOR_KILLED = 2
    const val PLANS_STARTED = 4
    const val PLANS_PICKED_UP = 8

    /** Onde o Constrictor está (THERE): galáxia 2, sistema em (144, 33). */
    fun isConstrictorSystem(s: PlanetSystem): Boolean = s.galaxyNumber == 2 && s.x == 144 && s.y == 33

    /** Bits 2-3 de TP = %10: carregando os planos (mais Thargoids, arquivos de naves C/D). */
    fun carryingPlans(tp: Int): Boolean = tp and 0b1100 == PLANS_PICKED_UP

    /** O que aconteceu ao atracar. */
    enum class Kind { BRIEF, DEBRIEF, BRIEF2, BRIEF3, DEBRIEF2 }

    /** Uma mensagem de missão (o "INCOMING MESSAGE" do original). */
    class Briefing(val kind: Kind, val pages: List<String>) {
        /** BRIEF mostra o Constrictor girando enquanto a mensagem é lida. */
        val showsConstrictor: Boolean get() = kind == Kind.BRIEF
    }

    /** Resultado de DOENTRY: o novo TP e a mensagem (ou null, se nada acontece). */
    class Entry(val tp: Int, val briefing: Briefing?, val rewardCash: Long = 0, val rewardKills: Int = 0, val navyEnergyUnit: Boolean = false)

    /**
     * DOENTRY: ao atracar em [here] (galáxia [galaxyNumber] 1..8) com [kills] abates e o
     * estado [tp], decide se uma missão começa, avança ou termina.
     */
    fun onDocked(tp: Int, kills: Int, galaxyNumber: Int, here: PlanetSystem, commander: String = "Jameson"): Entry {
        val gcnt = galaxyNumber - 1
        val m1 = tp and 0b11
        if (m1 == 0) {
            if (kills < 256 || gcnt > 1) return Entry(tp, null)
            return Entry(tp or CONSTRICTOR_IN_PROGRESS, Briefing(Kind.BRIEF, brief(commander, gcnt)))
        }
        if (m1 == 0b11) {
            return Entry(tp and CONSTRICTOR_IN_PROGRESS.inv(), Briefing(Kind.DEBRIEF, listOf(DEBRIEF_TEXT)),
                rewardCash = 50_000, rewardKills = 256)
        }
        when (tp and 0b1111) {
            0b0010 -> if (kills >= 5 * 256 && gcnt == 2) {
                return Entry(tp or PLANS_STARTED, Briefing(Kind.BRIEF2, listOf(brief2(commander, gcnt))))
            }
            0b0110 -> if (gcnt == 2 && here.x == 215 && here.y == 84) {
                return Entry((tp and 0xF0) or 0b1010, Briefing(Kind.BRIEF3, brief3(commander)))
            }
            0b1010 -> if (gcnt == 2 && here.x == 63 && here.y == 72) {
                return Entry(tp or PLANS_STARTED, Briefing(Kind.DEBRIEF2, listOf(DEBRIEF2_TEXT)),
                    rewardKills = 256, navyEnergyUnit = true)
            }
        }
        return Entry(tp, null)
    }

    // ------------------------------------------------------------ textos (tokens estendidos)

    /** MT27: o capitão da marinha muda com a galáxia (tokens 217-219). */
    private fun captain(gcnt: Int) = listOf("Curruthers", "Fosdyke Smythe", "Fortesque")[gcnt.coerceIn(0, 2)]

    /** MT28: a pista do Constrictor (tokens 220-221). */
    private fun locationHint(gcnt: Int) =
        if (gcnt == 0) "was last seen at Reesdice" else "is believed to have jumped to this galaxy"

    /** Token 10: o briefing da missão 1, em duas telas (a pausa "display ship"). */
    private fun brief(name: String, gcnt: Int) = listOf(
        "Greetings Commander $name, I am Captain ${captain(gcnt)} of Her Majesty's Space Navy " +
            "and I beg a moment of your valuable time.\n\n" +
            "We would like you to do a little job for us.\n\n" +
            "The ship you see here is a new model, the Constrictor, equiped with a top secret " +
            "new shield generator.\n\nUnfortunately it's been stolen.",
        "It went missing from our ship yard on Xeer five months ago and ${locationHint(gcnt)}.\n\n" +
            "Your mission, should you decide to accept it, is to seek and destroy this ship.\n\n" +
            "You are cautioned that only Military Lasers will penetrate the new shields and " +
            "that the Constrictor is fitted with an E.C.M. System.\n\n" +
            "      Good Luck, Commander.\n\n        MESSAGE ENDS",
    )

    /** Token 11: o chamado da missão 2. */
    private fun brief2(name: String, gcnt: Int) =
        "Attention Commander $name, I am Captain ${captain(gcnt)} of Her Majesty's Space Navy. " +
            "We have need of your services again.\n\n" +
            "If you would be so good as to go to Ceerdi you will be briefed.\n\n" +
            "If successful, you will be well rewarded.\n\n        MESSAGE ENDS"

    /** Token 222: o briefing em Ceerdi (com os planos). */
    private fun brief3(name: String) = listOf(
        "Good day Commander $name.\n\nI am Agent Blake of Naval Intellegence.\n\n" +
            "As you know, the Navy have been keeping the Thargoids off your ass out in deep " +
            "space for many years now. Well the situation has changed.\n\n" +
            "Our boys are ready for a push right to the home system of those mothers.",
        "I have obtained the defence plans for their Hive Worlds.\n\n" +
            "The beetles know we've got something but not what.\n\n" +
            "If I transmit the plans to our base on Birera they'll intercept the transmission. " +
            "I need a ship to make the run.\n\nYou're elected.\n\n" +
            "The plans are unipulse coded within this transmission.\n\n" +
            "      You will be paid.\n\n    Good luck Commander.\n\n        MESSAGE ENDS",
    )

    /** Token 15. */
    private const val DEBRIEF_TEXT =
        "Congratulations Commander!\n\nThere will always be a place for you in Her Majesty's " +
            "Space Navy.\n\nAnd maybe sooner than you think...\n\n        MESSAGE ENDS"

    /** Token 223. */
    private const val DEBRIEF2_TEXT =
        "Well done Commander.\n\nYou have served us well and we shall remember.\n\n" +
            "We did not expect the Thargoids to find out about you.\n\n" +
            "For the moment please accept this Navy Extra Energy Unit as payment.\n\n" +
            "        MESSAGE ENDS"

    // ------------------------------------------------------------ dicas no "Data on System"

    /**
     * RUPLA/RUGAL/RUTOK: em alguns sistemas, a descrição do planeta dá lugar a um recado. Os
     * marcados "sempre" aparecem em qualquer momento; os da missão 1, só com ela em andamento.
     * [rnd] sorteia as palavras variáveis (como os tokens [91-95] do original).
     */
    fun systemHint(s: PlanetSystem, tp: Int, rnd: Random = Random.Default): String? {
        val gcnt = s.galaxyNumber - 1
        val h = HINTS.firstOrNull { it.system == s.index && it.galaxy == gcnt } ?: return null
        if (!h.always && tp and CONSTRICTOR_IN_PROGRESS == 0) return null
        return expand(h.text, s, rnd)
    }

    private class Hint(val system: Int, val galaxy: Int, val always: Boolean, val text: String)

    private val ERRIUS = "{ERRIUS}"

    private val HINTS = listOf(
        Hint(211, 0, true, "The colonists here have violated Intergalactic Cloning Protocol and should be avoided."),
        Hint(150, 0, false, "The Constrictor was last seen at Reesdice, Commander."),
        Hint(36, 0, false, "A {odd} looking ship left here a while back. Looked bound for Arexe."),
        Hint(28, 0, false, "Yep, a {odd} new ship had a galactic hyperdrive fitted here. Used it too."),
        Hint(253, 1, false, "This {odd} ship dehyped here from nowhere, sun skimmed and jumped. I hear it went to Inbibe."),
        Hint(79, 1, false, "{rogue} ship went for me at Ausar. My lasers didn't even scratch the {rogue}."),
        Hint(53, 1, false, "Oh dear me yes. A frightful rogue with what I believe you people call a lead " +
            "posterior shot up lots of those beastly pirates and went to Usleri."),
        Hint(118, 1, false, "You can tackle the {vicious} {rogue} if you like. He's at Orarra."),
        Hint(100, 2, true, "Coming soon: ELITE II"),
        Hint(32, 1, false, ERRIUS), Hint(68, 1, false, ERRIUS), Hint(164, 1, false, ERRIUS),
        Hint(220, 1, false, ERRIUS), Hint(106, 1, false, ERRIUS), Hint(16, 1, false, ERRIUS),
        Hint(162, 1, false, ERRIUS), Hint(3, 1, false, ERRIUS), Hint(107, 1, false, ERRIUS),
        Hint(26, 1, false, ERRIUS), Hint(192, 1, false, ERRIUS), Hint(184, 1, false, ERRIUS),
        Hint(5, 1, false, ERRIUS),
        Hint(101, 2, false, "Boy are you in the wrong galaxy!"),
        Hint(193, 1, false, "There's a real {rogue} pirate out there."),
        Hint(41, 2, true, "The inhabitants of {planet} are so amazingly primitive that they still think A*****R is a pretty neat game."),
    )

    /** Tokens 106-110: as pistas que mandam para Errius. */
    private val ERRIUS_TEXTS = listOf(
        "I hear a {odd} looking ship appeared at Errius.",
        "Yeah, I hear a {odd} ship left Errius a  while back.",
        "Get your iron ass over to Errius.",
        "Some {rogue} new ship was seen at Errius.",
        "Try Errius.",
    )
    private val ODD = listOf("funny", "wierd", "unusual", "strange", "peculiar")                 // 130-134
    private val ROGUE = listOf("son of a bitch", "scoundrel", "blackguard", "rogue",
        "whoreson beetle headflap ear'd knave")                                                     // 91-95
    private val VICIOUS = listOf("killer", "deadly", "evil", "lethal", "vicious")                 // 170-174

    private fun expand(text: String, s: PlanetSystem, rnd: Random): String {
        var t = if (text == ERRIUS) ERRIUS_TEXTS[rnd.nextInt(5)] else text
        t = t.replace("{odd}") { ODD[rnd.nextInt(5)] }
        t = t.replace("{rogue}") { ROGUE[rnd.nextInt(5)] }
        t = t.replace("{vicious}") { VICIOUS[rnd.nextInt(5)] }
        val planet = listOf(s.displayName, "the planet ${s.displayName}", "the world ${s.displayName}",
            "this planet", "this world")[rnd.nextInt(5)]                                            // 86-90
        t = t.replace("{planet}", planet)
        return t.replaceFirstChar { it.uppercase() }
    }

    private fun String.replace(token: String, pick: () -> String): String {
        var out = this
        while (token in out) out = out.replaceFirst(token, pick())
        return out
    }
}
