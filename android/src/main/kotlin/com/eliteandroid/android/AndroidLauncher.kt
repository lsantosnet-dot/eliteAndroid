package com.eliteandroid.android

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.eliteandroid.EliteGame
import com.eliteandroid.galaxy.PlanetSystem

/**
 * Activity principal: a superfície LibGDX ocupa a tela inteira e as Views Android
 * (HUD/menus/controles touch) ficam sobrepostas em um FrameLayout.
 *
 * Etapa 1: o overlay mostra a tela "Data on <planeta>" gerada pelo algoritmo Text Elite.
 * Toque na tela para percorrer os sistemas ao alcance (7.0 LY) de Lave.
 */
class AndroidLauncher : AndroidApplication() {

    private val game = EliteGame()
    private lateinit var dataView: TextView
    private var browseIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            numSamples = 4 // MSAA para linhas wireframe mais suaves
        }
        val gdxView = initializeForView(game, config)

        dataView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.neon_green))
            setBackgroundColor(ContextCompat.getColor(context, R.color.hud_dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(32, 24, 32, 24)
            setOnClickListener { showNextInRange() }
        }

        val root = FrameLayout(this).apply {
            addView(gdxView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(
                dataView,
                FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.TOP),
            )
        }
        setContentView(root)
        show(game.currentSystem)
    }

    private fun showNextInRange() {
        val here = game.currentSystem
        val candidates = listOf(here) + game.galaxy.inRange(here)
        browseIndex = (browseIndex + 1) % candidates.size
        show(candidates[browseIndex])
    }

    private fun show(system: PlanetSystem) {
        val dist = com.eliteandroid.galaxy.GalaxyGenerator.distance(game.currentSystem, system)
        dataView.text = buildString {
            appendLine("GALAXY ${system.galaxyNumber}  ·  DATA ON ${system.name}")
            appendLine("Distance: %.1f Light Years".format(java.util.Locale.US, dist / 10.0))
            append(system.dataScreen())
        }
    }
}
