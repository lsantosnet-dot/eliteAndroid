package com.eliteandroid.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.eliteandroid.EliteGame

/**
 * Executa o jogo no desktop (desenvolvimento).
 *
 *   ./gradlew :desktop:run
 *   ./gradlew :desktop:run --args="--screenshot shot.png --time 4.5"   (salva um PNG e sai)
 */
fun main(args: Array<String>) {
    val shotPath = args.valueAfter("--screenshot")
    val shotTime = args.valueAfter("--time")?.toFloat() ?: 0f
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Elite")
        setWindowedMode(args.valueAfter("--width")?.toInt() ?: 1600, args.valueAfter("--height")?.toInt() ?: 740)
        useVsync(true)
        setForegroundFPS(60)
    }
    val game = EliteGame()
    Lwjgl3Application(if (shotPath != null) ScreenshotWrapper(game, shotPath, shotTime) else game, config)
}

private fun Array<String>.valueAfter(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

/** Congela a cena no instante [time], renderiza alguns quadros e grava o framebuffer em PNG. */
private class ScreenshotWrapper(
    private val game: EliteGame,
    private val path: String,
    private val time: Float,
) : ApplicationListener by game {
    private var frames = 0

    override fun render() {
        if (frames == 0) game.scene.setTime(time)
        game.render()
        if (++frames == 3) {
            val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
            // O framebuffer vem de baixo para cima; inverte para o PNG.
            val flipped = Pixmap(w, h, Pixmap.Format.RGBA8888)
            for (y in 0 until h) flipped.drawPixmap(pixmap, 0, y, w, 1, 0, h - 1 - y, w, 1)
            PixmapIO.writePNG(Gdx.files.absolute(path), flipped, 6, true)
            pixmap.dispose(); flipped.dispose()
            Gdx.app.exit()
        }
    }
}
