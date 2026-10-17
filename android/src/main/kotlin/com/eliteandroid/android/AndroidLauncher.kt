package com.eliteandroid.android

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.eliteandroid.EliteGame
import com.eliteandroid.android.ui.FlightControlsOverlay
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem
import java.util.Locale

/**
 * Activity principal: a superfície LibGDX ocupa a tela inteira e as Views Android ficam
 * sobrepostas num FrameLayout — controles de voo ([FlightControlsOverlay]) e a ficha
 * "Data on" (toque em [ DATA ]; toque na ficha para percorrer os sistemas a até 7 LY e
 * escolhê-los como destino do hiperespaço).
 */
class AndroidLauncher : AndroidApplication() {

    private val game = EliteGame()
    private lateinit var titleView: TextView
    private lateinit var dataView: TextView
    private var browseIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            // Sem MSAA: as linhas já têm antialiasing no shader e a cena passa por framebuffers.
            numSamples = 0
        }
        val gdxView = initializeForView(game, config)

        titleView = hudText(15f).apply {
            text = "[ DATA ]"
            setOnClickListener { toggleData() }
        }
        dataView = hudText(13f).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.hud_dim))
            visibility = View.GONE
            setOnClickListener { showNextInRange() }
        }
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(dataView)
        }

        val root = FrameLayout(this).apply {
            addView(gdxView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(FlightControlsOverlay(this@AndroidLauncher, game), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(hud, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.TOP))
        }
        setContentView(root)
        show(game.currentSystem)
    }

    private fun hudText(sizeSp: Float) = TextView(this).apply {
        typeface = Typeface.MONOSPACE
        setTextColor(ContextCompat.getColor(context, R.color.neon_green))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setPadding(32, 16, 32, 16)
    }

    private fun toggleData() {
        dataView.visibility = if (dataView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun showNextInRange() {
        val here = game.sessionOrNull?.currentSystem ?: game.currentSystem
        val candidates = listOf(here) + game.galaxy.inRange(here)
        browseIndex = (browseIndex + 1) % candidates.size
        val system = candidates[browseIndex]
        // Escolher um sistema na ficha também o define como destino do hiperespaço.
        game.sessionOrNull?.hyperspaceTarget = if (system.index == here.index) null else system
        titleView.text = if (system.index == here.index) "[ DATA ]" else "[ DATA ]  TARGET: ${system.name}"
        show(system)
    }

    private fun show(system: PlanetSystem) {
        val here = game.sessionOrNull?.currentSystem ?: game.currentSystem
        val dist = GalaxyGenerator.distance(here, system)
        dataView.text = buildString {
            appendLine("GALAXY ${system.galaxyNumber}  ·  DATA ON ${system.name}")
            appendLine("Distance: %.1f Light Years".format(Locale.US, dist / 10.0))
            append(system.dataScreen())
        }
    }
}
