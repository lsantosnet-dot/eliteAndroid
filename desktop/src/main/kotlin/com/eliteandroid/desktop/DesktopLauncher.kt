package com.eliteandroid.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.eliteandroid.EliteGame
import com.eliteandroid.flight.FlightControls
import com.eliteandroid.flight.View

/**
 * Executa o jogo no desktop (desenvolvimento).
 *
 *   ./gradlew :desktop:run
 *
 * Captura de tela (salva um PNG e sai): decola, voa [--time] segundos com o manche em
 * [--roll]/[--pitch] (−1..1), velocidade-alvo [--speed] (1..40) e fotografa na vista
 * [--view] (front, rear, left, right). [--fire] captura com o laser disparando,
 * [--arrive NOME] chega pelo hiperespaço nesse sistema, [--jumps N] dá N saltos "J" e
 * [--docked] fotografa a tela de atracado; [--beam] instala um laser de feixe na frente e
 * [--bomb] detona a bomba de energia (com [--combat], no meio da batalha):
 *   ./gradlew :desktop:run --args="--screenshot shot.png --time 2 --view rear"
 */
fun main(args: Array<String>) {
    val shotPath = args.valueAfter("--screenshot")
    val shot = ShotSpec(
        time = args.valueAfter("--time")?.toFloat() ?: 0f,
        view = args.valueAfter("--view")?.let { View.valueOf(it.uppercase()) } ?: View.FRONT,
        roll = args.valueAfter("--roll")?.toFloat() ?: 0f,
        pitch = args.valueAfter("--pitch")?.toFloat() ?: 0f,
        docked = "--docked" in args,
        speed = args.valueAfter("--speed")?.toFloat(),
        fire = "--fire" in args,
        arrive = args.valueAfter("--arrive"),
        jumps = args.valueAfter("--jumps")?.toInt() ?: 0,
        combat = "--combat" in args,
        beam = "--beam" in args,
        bomb = "--bomb" in args,
    )
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Elite")
        setWindowedMode(args.valueAfter("--width")?.toInt() ?: 1600, args.valueAfter("--height")?.toInt() ?: 740)
        useVsync(true)
        setForegroundFPS(60)
    }
    val game = EliteGame()
    Lwjgl3Application(if (shotPath != null) ScreenshotWrapper(game, shotPath, shot) else game, config)
}

/** Cena de combate para capturas: piratas à frente, um explodindo e um míssil nosso a caminho. */
private fun stageCombat(m: com.eliteandroid.flight.FlightModel) {
    fun ship(t: com.eliteandroid.ships.ShipType, x: Float, y: Float, z: Float, ai: Int) =
        com.eliteandroid.ships.ShipInstance(t, com.badlogic.gdx.math.Vector3(x, y, z)).also { it.aiFlag = ai; m.addShip(it) }
    ship(com.eliteandroid.ships.ShipType.MAMBA, -260f, 90f, 1300f, 0xFE).also { it.laserFlash = 1000f } // laser aceso na foto
    ship(com.eliteandroid.ships.ShipType.SIDEWINDER, 420f, -40f, 1700f, 0xC0).setOrientation(com.badlogic.gdx.math.Vector3(-1f, 0f, -0.3f))
    val python = ship(com.eliteandroid.ships.ShipType.PYTHON, 80f, 200f, 3200f, 0xC0)
    python.setOrientation(com.badlogic.gdx.math.Vector3(0.3f, -0.2f, 1f))
    m.combat.kill(ship(com.eliteandroid.ships.ShipType.SIDEWINDER, -700f, -150f, 2200f, 0xC0), byUs = true)
    m.ships.last().cloudCounter = 70f
    m.combat.armMissile()
}

private fun Array<String>.valueAfter(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

private class ShotSpec(
    val time: Float, val view: View, val roll: Float, val pitch: Float, val docked: Boolean, val speed: Float?,
    val fire: Boolean,
    val arrive: String?,
    val jumps: Int,
    val combat: Boolean,
    val beam: Boolean,
    val bomb: Boolean,
)

/** Simula o voo pedido, renderiza alguns quadros e grava o framebuffer em PNG. */
private class ScreenshotWrapper(
    private val game: EliteGame,
    private val path: String,
    private val shot: ShotSpec,
) : ApplicationListener by game {
    private var frames = 0

    override fun render() {
        if (frames == 0 && !shot.docked) {
            val session = game.session
            val target = shot.arrive?.let { session.galaxy.findByName(it) }
            if (target != null) session.arriveNow(target) else session.launchNow()
            repeat(shot.jumps) { session.model.inSystemJump() }
            if (shot.combat) stageCombat(session.model)
            if (shot.beam) session.model.player.lasers[0] = com.eliteandroid.flight.PlayerShip.BEAM_LASER
            val controls = FlightControls().apply { stickX = shot.roll; stickY = shot.pitch; targetSpeed = shot.speed }
            var t = 0f
            while (t < shot.time) { session.model.update(1f / 60f, controls); t += 1f / 60f }
            // Segura o gatilho (o quadro só é gravado com o feixe aceso).
            if (shot.fire) session.input.touch.fire = true
            session.model.view = shot.view
            if (shot.bomb) { session.model.player.energyBomb = true; session.model.combat.detonateBomb() }
        }
        game.render()
        frames++
        val waitingBeam = shot.fire && !game.session.model.player.laserBeamVisible && frames < 300
        if (frames >= 3 && !waitingBeam) {
            val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
            // createFromFrameBuffer já devolve as linhas de cima para baixo.
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
            PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, 6, true)
            pixmap.dispose()
            Gdx.app.exit()
        }
    }
}
