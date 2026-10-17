package com.eliteandroid.android

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.eliteandroid.EliteGame
import com.eliteandroid.GameSession
import com.eliteandroid.android.ui.ChartView
import com.eliteandroid.android.ui.FlightControlsOverlay
import com.eliteandroid.android.ui.StationPanel
import java.util.Locale

/**
 * Activity principal: a superfície LibGDX ocupa a tela inteira e as Views Android ficam
 * sobrepostas num FrameLayout:
 *
 *  - em voo: [FlightControlsOverlay] e os botões MAP (mapa estelar para escolher o destino)
 *    e DATA (dados do destino), no canto superior esquerdo;
 *  - atracado: o [StationPanel] (mercado, status, combustível, mapas, dados, LAUNCH).
 */
class AndroidLauncher : AndroidApplication() {

    private val game = EliteGame()
    private lateinit var stationPanel: StationPanel
    private lateinit var flightButtons: LinearLayout
    private lateinit var mapPanel: LinearLayout
    private lateinit var mapChart: ChartView
    private lateinit var dataView: TextView
    private lateinit var briefingPanel: LinearLayout
    private lateinit var briefingText: TextView
    private var shownBriefing: Pair<Any, Int>? = null
    private var lastState: GameSession.State? = null

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            window.decorView.postDelayed(this, 150)
        }
    }

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
        val session = { game.sessionOrNull }

        stationPanel = StationPanel(this, session, { game.soundOn }, { game.setSoundOn(it) }).apply { visibility = View.GONE }

        // Mapa em voo (o original permite consultar os mapas a qualquer momento).
        mapChart = ChartView(this, session)
        mapPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(225, 0, 4, 10))
            visibility = View.GONE
            val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            bar.addView(smallButton("SHORT RANGE") { mapChart.longRange = false }, LinearLayout.LayoutParams(0, dp(38f), 1f))
            bar.addView(smallButton("GALACTIC") { mapChart.longRange = true }, LinearLayout.LayoutParams(0, dp(38f), 1f))
            bar.addView(smallButton("CLOSE") { visibility = View.GONE }, LinearLayout.LayoutParams(0, dp(38f), 1f))
            addView(bar)
            addView(mapChart, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }

        dataView = hudText(13f).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.hud_dim))
            visibility = View.GONE
            setOnClickListener { visibility = View.GONE }
        }
        flightButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(smallButton("MAP") { mapPanel.visibility = View.VISIBLE; mapChart.invalidate() }, LinearLayout.LayoutParams(dp(72f), dp(40f)))
            addView(smallButton("DATA") { toggleData() }, LinearLayout.LayoutParams(dp(72f), dp(40f)).apply { marginStart = dp(6f) })
        }
        val topLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), 0, 0)
            addView(flightButtons)
            addView(dataView)
        }

        // "INCOMING MESSAGE": as mensagens das missões (o Constrictor gira acima, no GL).
        briefingText = hudText(14f).apply { setTextColor(Color.WHITE); setLineSpacing(0f, 1.15f) }
        briefingPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(215, 0, 4, 10))
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            visibility = View.GONE
            addView(hudText(15f).apply { text = "INCOMING MESSAGE"; gravity = Gravity.CENTER })
            addView(ScrollView(context).apply { addView(briefingText) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(smallButton("CONTINUE") { game.sessionOrNull?.nextBriefingPage(); refreshBriefing() },
                LinearLayout.LayoutParams(dp(180f), dp(44f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(6f) })
        }

        val root = FrameLayout(this).apply {
            addView(gdxView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(FlightControlsOverlay(this@AndroidLauncher, game), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(topLeft, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.TOP))
            addView(mapPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(stationPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(briefingPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.BOTTOM))
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post(poll)
    }

    override fun onPause() {
        window.decorView.removeCallbacks(poll)
        super.onPause()
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun hudText(sizeSp: Float) = TextView(this).apply {
        typeface = Typeface.MONOSPACE
        setTextColor(ContextCompat.getColor(context, R.color.neon_green))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
    }

    private fun smallButton(label: String, onClick: () -> Unit) = hudText(13f).apply {
        text = label
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setStroke(dp(1.5f), ContextCompat.getColor(context, R.color.neon_green))
            cornerRadius = 6f * resources.displayMetrics.density
            setColor(Color.argb(60, 0, 20, 10))
        }
        setOnClickListener { onClick() }
    }

    /** DATA: Data on System do destino (ou do sistema atual, sem destino). */
    private fun toggleData() {
        if (dataView.visibility == View.VISIBLE) { dataView.visibility = View.GONE; return }
        val sess = game.sessionOrNull ?: return
        val target = sess.hyperspaceTarget
        val shown = target ?: sess.currentSystem.takeUnless { sess.inWitchspace }
        dataView.text = buildString {
            if (shown == null) { append("WITCHSPACE\n\nNo system data in witchspace."); return@buildString }
            if (!sess.isHere(shown)) appendLine("Distance: %.1f Light Years".format(Locale.US, sess.distanceTo(shown) / 10.0))
            append(sess.dataScreen(shown))
        }
        dataView.visibility = View.VISIBLE
    }

    /**
     * Mensagem de missão: cobre a tela, ou só a parte de baixo quando o Constrictor
     * aparece girando (o briefing da missão 1); o painel da estação volta ao fim.
     */
    private fun refreshBriefing() {
        val sess = game.sessionOrNull ?: return
        val b = sess.briefing.takeIf { sess.state == GameSession.State.DOCKED }
        val key = b?.let { it to sess.briefingPage }
        if (key == shownBriefing) return
        shownBriefing = key
        if (b == null) {
            briefingPanel.visibility = View.GONE
            if (sess.state == GameSession.State.DOCKED) { stationPanel.visibility = View.VISIBLE; stationPanel.refresh() }
            return
        }
        briefingText.text = b.pages[sess.briefingPage]
        (briefingPanel.layoutParams as FrameLayout.LayoutParams).height =
            if (b.showsConstrictor) (window.decorView.height * 0.6f).toInt() else MATCH_PARENT
        briefingPanel.requestLayout()
        briefingPanel.visibility = View.VISIBLE
        stationPanel.visibility = View.GONE
    }

    /** Mostra o painel da estação quando atracado e os botões de voo quando em voo. */
    private fun refresh() {
        val sess = game.sessionOrNull ?: return
        refreshBriefing()
        val state = sess.state
        if (state == lastState) return
        val docked = state == GameSession.State.DOCKED
        stationPanel.visibility = if (docked && sess.briefing == null) View.VISIBLE else View.GONE
        if (docked) stationPanel.refresh()
        val flying = state == GameSession.State.FLYING
        flightButtons.visibility = if (flying) View.VISIBLE else View.GONE
        if (!flying) { mapPanel.visibility = View.GONE; dataView.visibility = View.GONE }
        lastState = state
    }
}
