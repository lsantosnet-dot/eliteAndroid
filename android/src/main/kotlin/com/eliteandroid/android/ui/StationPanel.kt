package com.eliteandroid.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.eliteandroid.GameSession
import com.eliteandroid.economy.Commander
import com.eliteandroid.economy.Commodities
import com.eliteandroid.galaxy.GalaxyGenerator
import java.util.Locale

/**
 * Telas da estação (atracado), como as teclas de função do original:
 * MARKET (preços e compra/venda: "Buy Cargo"/"Sell Cargo"), STATUS (Commander),
 * EQUIP (combustível), CHART (mapas) e DATA (Data on System). LAUNCH decola.
 */
@SuppressLint("ViewConstructor")
class StationPanel(context: Context, private val session: () -> GameSession?) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private enum class Tab(val label: String) { MARKET("MARKET"), STATUS("STATUS"), EQUIP("EQUIP"), CHART("CHART"), DATA("DATA") }

    private var tab = Tab.MARKET
    private val tabButtons = HashMap<Tab, TextView>()
    private val content = FrameLayout(context)
    private val header = text(13f, NeonStyle.CYAN)
    private val feedback = text(12f, NeonStyle.YELLOW)

    private val marketRows = LinearLayout(context).apply { orientation = VERTICAL }
    private val marketScroll = ScrollView(context).apply { addView(marketRows) }
    private val statusText = text(13f, NeonStyle.GREEN)
    private val dataText = text(13f, NeonStyle.GREEN)
    private val equipBox = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val equipText = text(14f, NeonStyle.GREEN)
    val chart = ChartView(context, session)
    private val chartBox = LinearLayout(context).apply { orientation = VERTICAL }

    private val rowViews = ArrayList<Array<TextView>>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(235, 0, 4, 10))
        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))

        // Abas + LAUNCH.
        val tabs = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        Tab.entries.forEach { t ->
            val b = button(t.label, NeonStyle.GREEN) { select(t) }
            tabButtons[t] = b
            tabs.addView(b, LayoutParams(0, dp(40f), 1f).apply { marginEnd = dp(6f) })
        }
        tabs.addView(button("LAUNCH", NeonStyle.MAGENTA) { session()?.input?.touch?.launchRequest = true },
            LayoutParams(0, dp(40f), 1.2f))
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(feedback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        buildMarketRows()
        equipBox.addView(equipText)
        equipBox.addView(button("BUY FUEL", NeonStyle.YELLOW) { refuel() }, LayoutParams(dp(200f), dp(48f)).apply { topMargin = dp(16f) })
        val chartToggle = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(button("SHORT RANGE", NeonStyle.CYAN) { chart.longRange = false }, LayoutParams(0, dp(36f), 1f).apply { marginEnd = dp(6f) })
            addView(button("GALACTIC", NeonStyle.CYAN) { chart.longRange = true }, LayoutParams(0, dp(36f), 1f))
        }
        chartBox.addView(chartToggle)
        chartBox.addView(chart, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        select(Tab.MARKET)
    }

    private fun text(sp: Float, color: Int) = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = text(13f, color).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        background = GradientDrawable().apply {
            setStroke(dp(1.5f), color); cornerRadius = 6f * density; setColor(Color.argb(40, 0, 40, 30))
        }
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
    }

    private fun select(t: Tab) {
        tab = t
        tabButtons.forEach { (k, v) -> v.alpha = if (k == t) 1f else 0.55f }
        content.removeAllViews()
        val view: View = when (t) {
            Tab.MARKET -> marketScroll
            Tab.STATUS -> ScrollView(context).apply { addView(statusText) }
            Tab.EQUIP -> equipBox
            Tab.CHART -> chartBox
            Tab.DATA -> ScrollView(context).apply { addView(dataText) }
        }
        (view.parent as? FrameLayout)?.removeView(view)
        content.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        feedback.text = ""
        refresh()
    }

    /** Uma linha por mercadoria: nome, preço, unidade, oferta, no porão, [−] vender, [+] comprar. */
    private fun buildMarketRows() {
        val head = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf("PRODUCT" to 3.2f, "PRICE" to 1.6f, "UNIT" to 1f, "FOR SALE" to 1.5f, "HOLD" to 1.2f, "" to 2.4f).forEach { (s, w) ->
            head.addView(text(11f, NeonStyle.CYAN).apply { text = s }, LayoutParams(0, LayoutParams.WRAP_CONTENT, w))
        }
        marketRows.addView(head)
        Commodities.ALL.forEach { c ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val cells = arrayOf(text(13f, NeonStyle.GREEN), text(13f, NeonStyle.YELLOW), text(13f, NeonStyle.GREEN),
                text(13f, NeonStyle.GREEN), text(13f, NeonStyle.YELLOW))
            cells[0].text = c.name
            cells[2].text = c.unit.label
            listOf(3.2f, 1.6f, 1f, 1.5f, 1.2f).forEachIndexed { i, w -> row.addView(cells[i], LayoutParams(0, LayoutParams.WRAP_CONTENT, w)) }
            val buttons = LinearLayout(context).apply { orientation = HORIZONTAL }
            buttons.addView(button("SELL", NeonStyle.RED) { trade(c.index, buy = false) }, LayoutParams(0, dp(34f), 1f).apply { marginEnd = dp(4f) })
            buttons.addView(button("BUY", NeonStyle.GREEN) { trade(c.index, buy = true) }, LayoutParams(0, dp(34f), 1f))
            row.addView(buttons, LayoutParams(0, LayoutParams.WRAP_CONTENT, 2.4f))
            marketRows.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) })
            rowViews += cells
        }
    }

    private fun trade(index: Int, buy: Boolean) {
        val sess = session() ?: return
        val r = if (buy) sess.buy(index) else sess.sell(index)
        feedback.text = when (r) {
            Commander.TradeResult.OK -> ""
            Commander.TradeResult.NO_STOCK -> "NONE FOR SALE"
            Commander.TradeResult.NO_CASH -> "NOT ENOUGH CASH"
            Commander.TradeResult.NO_ROOM -> "CARGO BAY FULL"
            Commander.TradeResult.NONE_IN_HOLD -> "NONE IN THE HOLD"
        }
        refresh()
    }

    private fun refuel() {
        val sess = session() ?: return
        val cost = sess.refuelCost
        feedback.text = when {
            cost == 0L -> "TANK ALREADY FULL"
            sess.refuel() -> "REFUELLED: " + Commander.formatCredits(cost)
            else -> "NOT ENOUGH CASH"
        }
        refresh()
    }

    /** Atualiza os textos (chamado ao abrir e a cada ação). */
    fun refresh() {
        val sess = session() ?: return
        val cmdr = sess.commander
        val here = sess.currentSystem
        val market = sess.market
        val fuel = sess.model.player.fuel
        header.text = String.format(Locale.US, "%s  ·  CASH %s  ·  HOLD %d/%d t  ·  FUEL %.1f LY",
            here.name, Commander.formatCredits(cmdr.cash), cmdr.tonnesUsed, cmdr.cargoCapacity, fuel / 10f)

        Commodities.ALL.forEach { c ->
            val cells = rowViews[c.index]
            cells[1].text = String.format(Locale.US, "%d.%d", market.prices[c.index] / 10, market.prices[c.index] % 10)
            val avail = market.availability[c.index]
            cells[3].text = if (avail == 0) "-" else "$avail${c.unit.label}"
            cells[4].text = if (cmdr.cargo[c.index] == 0) "" else "${cmdr.cargo[c.index]}${c.unit.label}"
        }

        val target = sess.hyperspaceTarget
        statusText.text = buildString {
            appendLine("COMMANDER JAMESON")
            appendLine()
            appendLine("Present System    : ${here.displayName}")
            appendLine("Hyperspace System : ${target?.displayName ?: "-"}")
            appendLine("Condition         : Docked")
            appendLine(String.format(Locale.US, "Fuel              : %.1f Light Years", fuel / 10f))
            appendLine("Cash              : ${Commander.formatCredits(cmdr.cash)}")
            appendLine("Legal Status      : ${Commander.legal(sess.model.player.legalStatus)}")
            appendLine("Rating            : ${Commander.rating(sess.model.player.kills)}")
            appendLine()
            appendLine("EQUIPMENT: Front Pulse Laser, ${sess.model.player.missiles} Missiles")
            val hold = Commodities.ALL.filter { cmdr.cargo[it.index] > 0 }
            appendLine()
            appendLine("CARGO: " + if (hold.isEmpty()) "empty" else hold.joinToString { "${cmdr.cargo[it.index]}${it.unit.label} ${it.name}" })
        }
        equipText.text = String.format(Locale.US, "FUEL  %.1f / 7.0 LIGHT YEARS\n\nFILL TANK: %s\n\nCASH: %s",
            fuel / 10f, Commander.formatCredits(sess.refuelCost), Commander.formatCredits(cmdr.cash))
        val shown = target ?: here
        dataText.text = buildString {
            if (shown !== here) appendLine(String.format(Locale.US, "Distance: %.1f Light Years", GalaxyGenerator.distance(here, shown) / 10f))
            append(shown.dataScreen())
        }
        chart.invalidate()
    }
}
