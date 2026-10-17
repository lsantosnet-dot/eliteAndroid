package com.eliteandroid

import com.badlogic.gdx.math.Vector3
import com.eliteandroid.render.NeonPalette
import com.eliteandroid.render.Stardust
import com.eliteandroid.ships.Blueprints
import com.eliteandroid.ships.ShipInstance
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cena de demonstração da Etapa 2: a Coriolis girando com a fenda voltada para o jogador
 * e uma Cobra Mk III orbitando a estação. Substituída pelo voo real na Etapa 3.
 */
class DemoScene {

    val station = ShipInstance(Blueprints.CORIOLIS, Vector3(0f, 40f, 1500f), NeonPalette.STATION)
        .orient(Vector3(0f, 0f, -1f))

    val cobra = ShipInstance(Blueprints.COBRA_MK3, Vector3(), NeonPalette.PLAYER_SHIP)

    val dust = Stardust()

    val ships = listOf(station, cobra)

    private var time = 0f
    private val tangent = Vector3()
    private val up = Vector3()

    init { update(0f) }

    fun update(delta: Float) {
        time += delta
        // Estações giram continuamente em torno do eixo do nariz (a fenda).
        station.roll(STATION_ROLL_RATE * delta)

        // Órbita inclinada ao redor da estação.
        val a = time * ORBIT_RATE
        val c = cos(a); val s = sin(a)
        cobra.position.set(station.position).add(ORBIT_RADIUS * c, ORBIT_RADIUS * 0.25f * s, -ORBIT_RADIUS * s)
        tangent.set(-s, 0.25f * c, -c).nor()
        up.set(cobra.position).sub(station.position).scl(-1f).nor().scl(0.35f).add(0f, 1f, 0f)
        cobra.orient(tangent, up)

        dust.update(delta)
    }

    /** Pose estática usada pelas capturas de tela do desktop. */
    fun setTime(t: Float) { time = 0f; station.orient(Vector3(0f, 0f, -1f)); update(t) }

    private companion object {
        const val STATION_ROLL_RATE = 0.30f // rad/s
        const val ORBIT_RATE = 0.22f        // rad/s
        const val ORBIT_RADIUS = 480f
    }
}
