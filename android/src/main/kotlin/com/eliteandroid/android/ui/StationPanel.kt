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
import com.eliteandroid.economy.Equipment
import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.flight.View as SpaceDirection
import com.eliteandroid.galaxy.GalaxyGenerator
import java.util.Locale

/**
 * Telas da estação (atracado), como as teclas de função do original:
 * MARKET (preços e compra/venda: "Buy Cargo"/"Sell Cargo"), STATUS (Commander),
 * EQUIP ("Equip Ship": combustível, mísseis, lasers e os demais itens, conforme o tech
 * level do sistema), CHART (mapas) e DATA (Data on System). LAUNCH decola.
 */
@SuppressLint("ViewConstructor")
class StationPanel(
    context: Context,
    private val session: () -> GameSession?,
    /** Som ligado (DNOIZ) — lido e alterado pela aba STATUS. */
    private val soundOn: () -> Boolean = { true },
    private val setSoundOn: (Boolean) -> Unit = {},
) : LinearLayout(context) {

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
    private val statusBox = LinearLayout(context).apply { orientation = VERTICAL }
    private var newGameArmedAt = 0L
    private val dataText = text(13f, NeonStyle.GREEN)
    private val equipBox = LinearLayout(context).apply { orientation = VERTICAL }
    private val equipRows = LinearLayout(context).apply { orientation = VERTICAL }
    private val equipRowViews = HashMap<Equipment, Pair<View, Array<TextView>>>()
    /** Vista onde os lasers comprados serão instalados (qv: "VIEW ?"). */
    private var laserView = SpaceDirection.FRONT
    private val laserViewButtons = HashMap<SpaceDirection, TextView>()
    val chart = ChartView(context, session)
    private val chartBox = LinearLayout(context).apply { orientation = VERTICAL }

    private val rowViews = ArrayList<Array<TextView>>()

    /** Liga/desliga o som (declarado antes do init, que o põe na aba STATUS). */
    private val soundButton: TextView by lazy {
        button("SOUND", NeonStyle.YELLOW) {
            setSoundOn(!soundOn())
            updateSoundButton()
        }
    }

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
        statusBox.addView(ScrollView(context).apply { addView(statusText) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val saveRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(button("SAVE GAME", NeonStyle.GREEN) { saveGame() }, LayoutParams(0, dp(42f), 1f).apply { marginEnd = dp(6f) })
            addView(button("SAVE & EXIT", NeonStyle.CYAN) { saveAndExit() }, LayoutParams(0, dp(42f), 1f).apply { marginEnd = dp(6f) })
            addView(button("NEW GAME", NeonStyle.RED) { newGame() }, LayoutParams(0, dp(42f), 1f).apply { marginEnd = dp(6f) })
            addView(soundButton, LayoutParams(0, dp(42f), 1f))
        }
        statusBox.addView(saveRow)
        buildEquipRows()
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
            Tab.STATUS -> statusBox
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

    /**
     * "Equip Ship": uma linha por item (nome, preço, o que já está instalado, BUY) e, para
     * os lasers, a escolha da vista (FRONT/REAR/LEFT/RIGHT).
     */
    private fun buildEquipRows() {
        val views = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        views.addView(text(12f, NeonStyle.CYAN).apply { text = "LASER VIEW " }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        SpaceDirection.entries.forEach { v ->
            val b = button(v.name, NeonStyle.CYAN) { laserView = v; refresh() }
            laserViewButtons[v] = b
            views.addView(b, LayoutParams(0, dp(32f), 1f).apply { marginStart = dp(4f) })
        }
        equipBox.addView(views, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4f) })
        equipBox.addView(ScrollView(context).apply { addView(equipRows) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        Equipment.entries.forEach { item ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val cells = arrayOf(text(13f, NeonStyle.GREEN), text(13f, NeonStyle.YELLOW), text(12f, NeonStyle.CYAN))
            cells[0].text = item.label
            listOf(3.4f, 1.6f, 2.6f).forEachIndexed { i, w -> row.addView(cells[i], LayoutParams(0, LayoutParams.WRAP_CONTENT, w)) }
            row.addView(button("BUY", NeonStyle.GREEN) { buyEquipment(item) }, LayoutParams(0, dp(34f), 1.2f))
            equipRows.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) })
            equipRowViews[item] = row to cells
        }
    }

    private fun updateSoundButton() {
        soundButton.text = if (soundOn()) "SOUND ON" else "SOUND OFF"
        soundButton.alpha = if (soundOn()) 1f else 0.55f
    }

    /** Mensagem de erro com o bipe do original (dn2). */
    private fun error(text: String) {
        feedback.text = text
        session()?.errorBeep()
    }

    private fun buyEquipment(item: Equipment) {
        val sess = session() ?: return
        val cost = Equipment.price(item, sess.model.player)
        val result = sess.buyEquipment(item, laserView)
        if (result != Equipment.Result.OK) sess.errorBeep()
        feedback.text = when (result) {
            Equipment.Result.OK -> "${item.label.uppercase(Locale.US)} FITTED: " + Commander.formatCredits(cost)
            Equipment.Result.NO_CASH -> "NOT ENOUGH CASH"
            Equipment.Result.PRESENT -> if (item.needsView) "LASER PRESENT" else "${item.label.uppercase(Locale.US)} PRESENT"
            Equipment.Result.ALL_PRESENT -> "ALL PRESENT"
            Equipment.Result.NOT_AVAILABLE -> "NOT AVAILABLE HERE"
        }
        refresh()
    }

    /** O que já está instalado para [item]. */
    private fun fitted(item: Equipment, p: PlayerShip, cmdr: Commander): String = when (item) {
        Equipment.FUEL -> String.format(Locale.US, "%.1f LY", p.fuel / 10f)
        Equipment.MISSILE -> "${p.missiles}/${Equipment.MAX_MISSILES}"
        Equipment.LARGE_CARGO_BAY -> if (cmdr.cargoCapacity >= Equipment.LARGE_CARGO_CAPACITY) "FITTED" else ""
        Equipment.ECM -> if (p.hasEcm) "FITTED" else ""
        Equipment.PULSE_LASER, Equipment.BEAM_LASER, Equipment.MILITARY_LASER, Equipment.MINING_LASER ->
            laserViews(p) { it == item.laser }
        Equipment.FUEL_SCOOPS -> if (p.fuelScoops) "FITTED" else ""
        Equipment.ESCAPE_POD -> if (p.escapePod) "FITTED" else ""
        Equipment.ENERGY_BOMB -> if (p.energyBomb) "FITTED" else ""
        Equipment.ENERGY_UNIT -> if (p.navyEnergyUnit) "NAVY" else if (p.energyUnit) "FITTED" else ""
        Equipment.DOCKING_COMPUTER -> if (p.dockingComputer) "FITTED" else ""
        Equipment.GALACTIC_HYPERDRIVE -> if (p.galacticHyperdrive) "FITTED" else ""
    }

    private fun laserViews(p: PlayerShip, match: (Int) -> Boolean): String =
        SpaceDirection.entries.filter { match(p.lasers[it.ordinal]) }.joinToString(" ") { it.name.take(1) }

    /** Lasers instalados, como na tela "Commander": "Front Pulse Laser", "Rear Beam Laser"… */
    private fun laserList(p: PlayerShip): List<String> = SpaceDirection.entries.mapNotNull { v ->
        val l = p.lasers[v.ordinal]
        if (l == 0) null
        else v.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() } + " " +
            (Equipment.ofLaser(l)?.label?.removeSuffix("s") ?: "Laser")
    }

    private fun trade(index: Int, buy: Boolean) {
        val sess = session() ?: return
        val r = if (buy) sess.buy(index) else sess.sell(index)
        if (r != Commander.TradeResult.OK) sess.errorBeep()
        feedback.text = when (r) {
            Commander.TradeResult.OK -> ""
            Commander.TradeResult.NO_STOCK -> "NONE FOR SALE"
            Commander.TradeResult.NO_CASH -> "NOT ENOUGH CASH"
            Commander.TradeResult.NO_ROOM -> "CARGO BAY FULL"
            Commander.TradeResult.NONE_IN_HOLD -> "NONE IN THE HOLD"
        }
        refresh()
    }

    private fun saveGame() {
        if (session()?.save() == true) feedback.text = "GAME SAVED" else error("CAN ONLY SAVE WHILE DOCKED")
    }

    private fun saveAndExit() {
        if (session()?.save() == true) (context as? android.app.Activity)?.finish()
        else error("CAN ONLY SAVE WHILE DOCKED")
    }

    /** Recomeça como JAMESON em Lave; exige um segundo toque em até 3 s. */
    private fun newGame() {
        val now = System.currentTimeMillis()
        if (now - newGameArmedAt > 3000L) {
            newGameArmedAt = now
            feedback.text = "TAP NEW GAME AGAIN TO ERASE THE SAVED COMMANDER"
            return
        }
        newGameArmedAt = 0L
        session()?.newGame()
        feedback.text = "NEW COMMANDER: JAMESON AT LAVE"
        refresh()
    }

    /** Atualiza os textos (chamado ao abrir e a cada ação). */
    fun refresh() {
        val sess = session() ?: return
        updateSoundButton()
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
            val p = sess.model.player
            val equipment = laserList(p) + listOfNotNull(
                "${p.missiles} Missiles",
                if (cmdr.cargoCapacity >= Equipment.LARGE_CARGO_CAPACITY) "Large Cargo Bay" else null,
                if (p.hasEcm) "E.C.M. System" else null,
                if (p.fuelScoops) "Fuel Scoops" else null,
                if (p.escapePod) "Escape Pod" else null,
                if (p.energyBomb) "Energy Bomb" else null,
                if (p.navyEnergyUnit) "Navy Energy Unit" else if (p.energyUnit) "Extra Energy Unit" else null,
                if (p.dockingComputer) "Docking Computers" else null,
                if (p.galacticHyperdrive) "Galactic Hyperspace" else null,
            )
            appendLine("EQUIPMENT:")
            equipment.forEach { appendLine("  $it") }
            val hold = Commodities.ALL.filter { cmdr.cargo[it.index] > 0 }
            appendLine()
            appendLine("CARGO: " + if (hold.isEmpty()) "empty" else hold.joinToString { "${cmdr.cargo[it.index]}${it.unit.label} ${it.name}" })
        }
        val forSale = sess.equipmentForSale
        equipRowViews.forEach { (item, rv) ->
            val (row, cells) = rv
            row.visibility = if (item in forSale) View.VISIBLE else View.GONE
            cells[1].text = Commander.formatCredits(Equipment.price(item, sess.model.player)).removeSuffix(" Cr")
            cells[2].text = fitted(item, sess.model.player, cmdr)
        }
        laserViewButtons.forEach { (v, b) -> b.alpha = if (v == laserView) 1f else 0.45f }
        val shown = target ?: here
        dataText.text = buildString {
            if (shown !== here) appendLine(String.format(Locale.US, "Distance: %.1f Light Years", GalaxyGenerator.distance(here, shown) / 10f))
            append(sess.dataScreen(shown))
        }
        chart.invalidate()
    }
}
