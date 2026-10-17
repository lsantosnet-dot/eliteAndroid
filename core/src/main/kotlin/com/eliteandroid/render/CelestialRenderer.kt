package com.eliteandroid.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eliteandroid.flight.Planet
import com.eliteandroid.ships.SpaceObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Planeta e sol. Como no original, o raio na tela é R·256/z (escalado pela largura da vista)
 * e eles aparecem em qualquer vista.
 *
 *  - Planeta tipo 128: contorno + equador e meridiano (só a metade voltada para nós).
 *  - Planeta tipo 130: contorno + uma cratera em torno do eixo nosev.
 *  - Sol: preenchido por linhas horizontais com a borda tremulando (SUN desenha linha a
 *    linha com um "franjado" aleatório).
 */
class CelestialRenderer {

    private val c = Vector3()
    private val n = Vector3(); private val r = Vector3(); private val s = Vector3()
    private val p0 = Vector3(); private val p1 = Vector3()
    private val tmp = Vector3(); private val out = Vector3()

    fun drawPlanet(planet: Planet, projector: WireframeProjector, lines: NeonLineBatch, color: Float) {
        val view = projector.view
        view.toView(planet.position.x, planet.position.y, planet.position.z, c)
        if (c.z <= projector.nearZ) return
        val radius = Planet.RADIUS / c.z * projector.focal
        val cx = projector.viewportWidth / 2f + c.x / c.z * projector.focal
        val cy = projector.viewportHeight / 2f + c.y / c.z * projector.focal
        if (radius < 1.5f) { lines.dot(cx, cy, color); return }
        circle(cx, cy, radius, lines, color)

        view.toView(planet.nosev.x, planet.nosev.y, planet.nosev.z, n)
        view.toView(planet.roofv.x, planet.roofv.y, planet.roofv.z, r)
        view.toView(planet.sidev.x, planet.sidev.y, planet.sidev.z, s)
        if (planet.hasCrater) {
            // Cratera: círculo de raio angular 0.45 rad em torno do polo nosev.
            surfaceCircle(projector, lines, color, n, s, r, 0.45f)
        } else {
            greatCircle(projector, lines, color, n, s) // equador (perpendicular ao roofv)
            greatCircle(projector, lines, color, n, r) // meridiano
        }
    }

    fun drawSun(sun: SpaceObject, projector: WireframeProjector, lines: NeonLineBatch, color: Float) {
        projector.view.toView(sun.position.x, sun.position.y, sun.position.z, c)
        if (c.z <= projector.nearZ) return
        val radius = Planet.RADIUS / c.z * projector.focal
        val cx = projector.viewportWidth / 2f + c.x / c.z * projector.focal
        val cy = projector.viewportHeight / 2f + c.y / c.z * projector.focal
        if (radius < 1.5f) { lines.dot(cx, cy, color); return }
        val step = maxOf(lines.halfWidth * 2.2f, radius / 48f)
        var y = -radius + step / 2f
        while (y < radius) {
            if (cy + y in -step..projector.viewportHeight + step) {
                val half = sqrt(radius * radius - y * y)
                val fringe = MathUtils.random(0f, radius * 0.08f)
                lines.segment(cx - half - fringe, cy + y, cx + half + MathUtils.random(0f, radius * 0.08f), cy + y, color)
            }
            y += step
        }
    }

    private fun circle(cx: Float, cy: Float, radius: Float, lines: NeonLineBatch, color: Float) {
        val segs = (radius / 6f).toInt().coerceIn(32, 256)
        var px = cx + radius; var py = cy
        for (i in 1..segs) {
            val a = i * MathUtils.PI2 / segs
            val x = cx + radius * cos(a); val y = cy + radius * sin(a)
            lines.segment(px, py, x, y, color)
            px = x; py = y
        }
    }

    /** Círculo máximo no plano (a, b), desenhando só os trechos voltados para o observador. */
    private fun greatCircle(projector: WireframeProjector, lines: NeonLineBatch, color: Float, a: Vector3, b: Vector3) {
        val segs = 64
        var prevVisible = point(a, b, 0f, Planet.RADIUS, p0)
        for (i in 1..segs) {
            val t = i * MathUtils.PI2 / segs
            val visible = point(a, b, t, Planet.RADIUS, p1)
            if (prevVisible && visible) emit(projector, lines, color)
            p0.set(p1); prevVisible = visible
        }
    }

    /** Círculo menor a [angle] rad do polo [pole] (cratera). */
    private fun surfaceCircle(
        projector: WireframeProjector, lines: NeonLineBatch, color: Float,
        pole: Vector3, u: Vector3, v: Vector3, angle: Float,
    ) {
        val segs = 32
        val ca = cos(angle); val sa = sin(angle)
        fun at(t: Float, outV: Vector3): Boolean {
            tmp.set(pole).scl(ca).mulAdd(u, sa * cos(t)).mulAdd(v, sa * sin(t)).scl(Planet.RADIUS)
            outV.set(c).add(tmp)
            return tmp.dot(outV) < 0f
        }
        var prevVisible = at(0f, p0)
        for (i in 1..segs) {
            val visible = at(i * MathUtils.PI2 / segs, p1)
            if (prevVisible && visible) emit(projector, lines, color)
            p0.set(p1); prevVisible = visible
        }
    }

    /** Ponto do círculo máximo; visível se a normal (p − c) aponta para o observador. */
    private fun point(a: Vector3, b: Vector3, t: Float, radius: Float, outV: Vector3): Boolean {
        tmp.set(a).scl(cos(t)).mulAdd(b, sin(t)).scl(radius)
        outV.set(c).add(tmp)
        return tmp.dot(outV) < 0f
    }

    private fun emit(projector: WireframeProjector, lines: NeonLineBatch, color: Float) {
        if (p0.z <= projector.nearZ || p1.z <= projector.nearZ) return
        projector.projectPoint(p0.x, p0.y, p0.z, out)
        val x0 = out.x; val y0 = out.y
        projector.projectPoint(p1.x, p1.y, p1.z, out)
        lines.segment(x0, y0, out.x, out.y, color)
    }
}
