package com.eliteandroid

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.eliteandroid.economy.Commander
import com.eliteandroid.economy.Market
import com.eliteandroid.flight.FlightEvent
import com.eliteandroid.flight.FlightModel
import com.eliteandroid.galaxy.Galaxy
import com.eliteandroid.galaxy.GalaxyGenerator
import com.eliteandroid.galaxy.PlanetSystem
import com.eliteandroid.hud.Dashboard
import com.eliteandroid.input.FlightInput
import com.eliteandroid.render.HudLayer
import com.eliteandroid.render.LaunchTunnel
import com.eliteandroid.render.NeonLineBatch
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.SpaceView

/**
 * Ciclo de jogo:
 *
 *   atracado (mercado) → túnel → voo → contagem → hiperespaço → chegada (planeta à frente,
 *   sol atrás) → aproximação do planeta → a estação surge → atracação → atracado (novo
 *   mercado) → …
 *
 * Destruição: GAME OVER e o comandante padrão volta a Lave (como recarregar JAMESON).
 */
class GameSession(val galaxy: Galaxy, private val homeSystem: PlanetSystem) {

    enum class State { DOCKED, LAUNCHING, FLYING, DOCKING, DESTROYED, HYPERSPACE }

    val model = FlightModel()
    val input = FlightInput()
    val dashboard = Dashboard()

    val commander = Commander()

    /** Lidos também pela interface Android (thread de UI). */
    @Volatile var state = State.DOCKED
        private set
    @Volatile var currentSystem: PlanetSystem = homeSystem
        private set
    /** Mercado da visita atual (refeito a cada chegada, com um novo QQ26). */
    @Volatile var market = Market(homeSystem, fluct = 0, initialAvailability = Market.JAMESON_AVAILABILITY)
        private set
    /** Sistema escolhido para o hiperespaço (QQ9/QQ10 do original). */
    @Volatile var hyperspaceTarget: PlanetSystem? = null

    private var stateTime = 0f
    private var message: String? = null
    private var messageTime = 0f
    private val layout = GlyphLayout()

    fun update(delta: Float) {
        input.poll()
        stateTime += delta
        messageTime -= delta

        input.requestedView?.let { if (state == State.FLYING) model.view = it }
        input.requestedView = null

        when (state) {
            State.DOCKED -> if (input.launchRequested) enter(State.LAUNCHING)
            State.LAUNCHING -> if (stateTime >= LaunchTunnel.DURATION) {
                model.launch(currentSystem)
                enter(State.FLYING)
            }
            State.FLYING -> {
                if (input.hyperspaceRequested) requestHyperspace()
                if (input.jumpRequested && !model.inSystemJump()) flash("JUMP?")
                when (model.update(delta, input.controls)) {
                    FlightEvent.DOCKED -> enter(State.DOCKING)
                    FlightEvent.CRASHED -> enter(State.DESTROYED)
                    FlightEvent.BUMPED -> flash("BUMP")
                    FlightEvent.HYPERSPACE -> enter(State.HYPERSPACE)
                    FlightEvent.STATION_APPEARED -> flash("SPACE STATION")
                    FlightEvent.NONE -> Unit
                }
            }
            State.DOCKING -> if (stateTime >= LaunchTunnel.DURATION) enter(State.DOCKED)
            State.DESTROYED -> if (stateTime >= 3f) restartCommander()
            State.HYPERSPACE -> if (stateTime >= HYPERSPACE_TUNNEL) arrive()
        }
        input.launchRequested = false
        input.hyperspaceRequested = false
        input.jumpRequested = false
    }

    /**
     * TT18: gasta o combustível da distância, troca de sistema, sorteia o QQ26 do novo
     * mercado (GVL) e monta planeta e sol (SOLAR). (Os mis-jumps para o witchspace, 0.78%
     * dos saltos, ficam para depois — eles levam a combates com Thargoids.)
     */
    @Synchronized private fun arrive() {
        val target = hyperspaceTarget
        if (target != null) {
            val distance = GalaxyGenerator.distance(currentSystem, target)
            model.player.fuel = (model.player.fuel - distance).coerceAtLeast(0f)
            currentSystem = target
            market = Market(target, fluct = kotlin.random.Random.nextInt(256))
            hyperspaceTarget = null
            model.arrive(target)
            flash(target.name)
        }
        enter(State.FLYING)
    }

    /** Morte: volta o comandante padrão (JAMESON em Lave, 100 Cr, porão vazio, tanque cheio). */
    @Synchronized private fun restartCommander() {
        commander.reset()
        currentSystem = homeSystem
        market = Market(homeSystem, fluct = 0, initialAvailability = Market.JAMESON_AVAILABILITY)
        hyperspaceTarget = null
        model.player.fuel = 70f
        model.player.missiles = 3
        enter(State.DOCKED)
    }

    // ------------------------------------------------------------------ comércio (UI)

    fun buy(index: Int, units: Int = 1): Commander.TradeResult =
        if (state != State.DOCKED) Commander.TradeResult.NO_STOCK else commander.buy(market, index, units)

    fun sell(index: Int, units: Int = 1): Commander.TradeResult =
        if (state != State.DOCKED) Commander.TradeResult.NONE_IN_HOLD else commander.sell(market, index, units)

    /** Custo para encher o tanque: 2 décimos de crédito por 0.1 LY (EQSHP: 14.0 Cr cheio). */
    val refuelCost: Long get() = ((70f - model.player.fuel).toLong().coerceAtLeast(0)) * 2

    @Synchronized fun refuel(): Boolean {
        if (state != State.DOCKED || refuelCost == 0L) return false
        if (!commander.spend(refuelCost)) return false
        model.player.fuel = 70f
        return true
    }

    /**
     * hyp: só em voo, sem contagem em andamento, com um destino diferente do sistema atual
     * e combustível suficiente; senão o original mostra "Range?".
     */
    private fun requestHyperspace() {
        if (model.hyperspace.active) return
        val target = hyperspaceTarget
        if (target == null || target.index == currentSystem.index) { flash("NO TARGET"); return }
        val distance = GalaxyGenerator.distance(currentSystem, target)
        if (distance > model.player.fuel) { flash("RANGE?"); return }
        model.hyperspace.start()
        flash("HYPERSPACE - ${target.name}")
    }

    /** Pula direto para o voo (capturas de tela e testes). */
    fun launchNow() {
        model.launch(currentSystem)
        enter(State.FLYING)
    }

    /** Pula direto para a chegada em [target] (capturas de tela e testes). */
    fun arriveNow(target: PlanetSystem) {
        hyperspaceTarget = target
        arrive()
    }

    private fun enter(s: State) {
        state = s
        stateTime = 0f
    }

    private fun flash(text: String) {
        message = text
        messageTime = 2f
    }

    fun layout(width: Float, height: Float) {
        dashboard.layout(width, height)
    }

    fun render(view: SpaceView) {
        val flying = state == State.FLYING
        view.render(
            model = if (flying) model else null,
            view = model.view,
            hud = object : HudLayer {
                override fun worldLines(lines: NeonLineBatch) = drawLines(view, lines)
                override fun panels(out: (Rectangle) -> Unit) { if (flying) out(dashboard.bounds) }
                override fun panelLines(lines: NeonLineBatch) { if (flying) dashboard.drawLines(lines, model) }
                override fun text(batch: SpriteBatch, font: BitmapFont) {
                    drawText(view, batch, font)
                    if (flying) dashboard.drawText(batch, font)
                }
            },
        )
    }

    private fun drawLines(view: SpaceView, lines: NeonLineBatch) {
        val w = view.width; val h = view.height
        when (state) {
            State.LAUNCHING, State.DOCKING ->
                LaunchTunnel.draw(stateTime / LaunchTunnel.DURATION, w, h, lines, NeonPalette.STATION)
            State.HYPERSPACE ->
                LaunchTunnel.draw((stateTime / HYPERSPACE_TUNNEL) % 1f, w, h, lines, NeonPalette.HYPERSPACE)
            State.FLYING -> {
                drawSight(w, h, lines)
                if (model.player.laserBeamVisible) drawLaser(view, w, h, lines)
            }
            else -> Unit
        }
    }

    /** SIGHT: mira do laser no centro da vista. */
    private fun drawSight(w: Float, h: Float, lines: NeonLineBatch) {
        val cx = w / 2f; val cy = h / 2f
        val a = h * 0.025f; val b = h * 0.075f
        val c = NeonPalette.PLAYER_SHIP
        lines.segment(cx - b, cy, cx - a, cy, c); lines.segment(cx + a, cy, cx + b, cy, c)
        lines.segment(cx, cy - b, cx, cy - a, c); lines.segment(cx, cy + a, cx, cy + b, c)
    }

    /**
     * LASLI: dois pares de linhas da borda inferior da vista (x = 32/224 e 48/208 numa vista
     * de 256 × 192) até o centro aleatório (LASX, LASY).
     */
    private fun drawLaser(view: SpaceView, w: Float, h: Float, lines: NeonLineBatch) {
        val k = view.projector.originalScale
        fun sx(x: Int) = w / 2f + (x - 128) * k
        fun sy(y: Int) = h / 2f - (y - 96) * k
        val p = model.player
        val tx = sx(p.laserX); val ty = sy(p.laserY)
        for (x in intArrayOf(32, 224, 48, 208)) lines.segment(sx(x), sy(191), tx, ty, NeonPalette.LASER)
    }

    private fun drawText(view: SpaceView, batch: SpriteBatch, font: BitmapFont) {
        val w = view.width; val h = view.height
        font.color = HUD_GREEN
        when (state) {
            State.DOCKED -> {
                centered(batch, font, "DOCKED  -  ${currentSystem.name}", w / 2f, h * 0.62f)
                if (Gdx.app?.type == Application.ApplicationType.Desktop)
                    centered(batch, font, "L / ENTER  TO LAUNCH", w / 2f, h * 0.45f)
            }
            State.FLYING -> {
                centered(batch, font, model.view.label, w / 2f, h - font.lineHeight * 0.6f)
                // Número da contagem do hiperespaço (QQ22+1).
                if (model.hyperspace.active) centered(batch, font, model.hyperspace.shown.toString(), w / 2f, h - font.lineHeight * 1.8f)
                if (messageTime > 0f) message?.let { centered(batch, font, it, w / 2f, h * 0.68f) }
            }
            State.DESTROYED -> centered(batch, font, "GAME OVER", w / 2f, h * 0.55f)
            else -> Unit
        }
    }

    private fun centered(batch: SpriteBatch, font: BitmapFont, text: String, x: Float, y: Float) {
        layout.setText(font, text)
        font.draw(batch, text, x - layout.width / 2f, y)
    }

    private companion object {
        val HUD_GREEN = Color(0.35f, 1f, 0.45f, 1f)
        const val HYPERSPACE_TUNNEL = 2.4f
    }
}
