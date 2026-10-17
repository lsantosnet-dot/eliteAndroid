package com.eliteandroid.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/**
 * Pós-processamento "neon": a cena é desenhada num framebuffer e recebe dois níveis de
 * bloom (halo próximo em 1/2 resolução e halo largo em 1/8), mais scanlines e vinheta.
 *
 * Uso: [beginScene] → desenhar → [endScene] (compõe na tela).
 */
class BloomRenderer : Disposable {

    var bloomNearIntensity = 2.2f
    var bloomFarIntensity = 2.6f
    var scanlineStrength = 0.10f
    var blurPasses = 2

    /** Cor de fundo do espaço (quase preto azulado). */
    var clearR = 0.0f; var clearG = 0.004f; var clearB = 0.016f

    private val copyShader = Shaders.compile("copy", Shaders.QUAD_VERTEX, Shaders.COPY_FRAGMENT)
    private val blurShader = Shaders.compile("blur", Shaders.QUAD_VERTEX, Shaders.BLUR_FRAGMENT)
    private val compositeShader = Shaders.compile("composite", Shaders.QUAD_VERTEX, Shaders.COMPOSITE_FRAGMENT)

    private val quad = Mesh(
        true, 4, 6,
        VertexAttribute(Usage.Position, 2, ShaderProgramAttrs.POSITION),
        VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgramAttrs.TEXCOORD),
    ).apply {
        setVertices(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f))
        setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
    }

    private var scene: FrameBuffer? = null
    private var nearA: FrameBuffer? = null
    private var nearB: FrameBuffer? = null
    private var farA: FrameBuffer? = null
    private var farB: FrameBuffer? = null
    private var width = 0
    private var height = 0

    fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        disposeBuffers()
        width = w; height = h
        scene = fbo(w, h)
        nearA = fbo(w / 2, h / 2); nearB = fbo(w / 2, h / 2)
        farA = fbo(w / 8, h / 8); farB = fbo(w / 8, h / 8)
    }

    private fun fbo(w: Int, h: Int) = FrameBuffer(Pixmap.Format.RGBA8888, maxOf(w, 1), maxOf(h, 1), false).apply {
        colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    fun beginScene() {
        val s = scene ?: return
        s.begin()
        Gdx.gl.glClearColor(clearR, clearG, clearB, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    fun endScene() {
        val s = scene ?: return
        s.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // Halo próximo: cena → 1/2 → blur.
        draw(nearA!!, copyShader, s.colorBufferTexture)
        blur(nearA!!, nearB!!)
        // Halo largo: 1/2 → 1/8 → blur.
        draw(farA!!, copyShader, nearA!!.colorBufferTexture)
        blur(farA!!, farB!!)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        s.colorBufferTexture.bind(0)
        nearA!!.colorBufferTexture.bind(1)
        farA!!.colorBufferTexture.bind(2)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        compositeShader.bind()
        compositeShader.setUniformi("u_scene", 0)
        compositeShader.setUniformi("u_bloomNear", 1)
        compositeShader.setUniformi("u_bloomFar", 2)
        compositeShader.setUniformf("u_bloomNearIntensity", bloomNearIntensity)
        compositeShader.setUniformf("u_bloomFarIntensity", bloomFarIntensity)
        compositeShader.setUniformf("u_scanline", scanlineStrength)
        compositeShader.setUniformf("u_scanlinePeriod", maxOf(3f, Gdx.graphics.backBufferHeight / 270f))
        quad.render(compositeShader, GL20.GL_TRIANGLES)
    }

    private fun blur(a: FrameBuffer, b: FrameBuffer) {
        repeat(blurPasses) {
            blurInto(b, a.colorBufferTexture, 1f / a.width, 0f)
            blurInto(a, b.colorBufferTexture, 0f, 1f / b.height)
        }
    }

    private fun blurInto(target: FrameBuffer, src: Texture, dx: Float, dy: Float) {
        target.begin()
        src.bind(0)
        blurShader.bind()
        blurShader.setUniformi("u_texture", 0)
        blurShader.setUniformf("u_dir", dx, dy)
        quad.render(blurShader, GL20.GL_TRIANGLES)
        target.end()
    }

    private fun draw(target: FrameBuffer, shader: ShaderProgram, src: Texture) {
        target.begin()
        src.bind(0)
        shader.bind()
        shader.setUniformi("u_texture", 0)
        quad.render(shader, GL20.GL_TRIANGLES)
        target.end()
    }

    private fun disposeBuffers() {
        listOf(scene, nearA, nearB, farA, farB).forEach { it?.dispose() }
        scene = null; nearA = null; nearB = null; farA = null; farB = null
    }

    override fun dispose() {
        disposeBuffers()
        quad.dispose()
        copyShader.dispose(); blurShader.dispose(); compositeShader.dispose()
    }
}
