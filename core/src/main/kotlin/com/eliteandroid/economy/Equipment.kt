package com.eliteandroid.economy

import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.flight.View

/**
 * Itens da loja de equipamentos (EQSHP) da versão em disco, na ordem e com os preços da
 * tabela PRXS (em décimos de crédito) — os 12 da cassete mais os lasers militar e de
 * mineração. O combustível custa 2 décimos por 0.1 LY.
 */
enum class Equipment(val label: String, val price: Long) {
    FUEL("Fuel", 2),
    MISSILE("Missile", 300),
    LARGE_CARGO_BAY("Large Cargo Bay", 4000),
    ECM("E.C.M. System", 6000),
    PULSE_LASER("Pulse Laser", 4000),
    BEAM_LASER("Beam Laser", 10000),
    FUEL_SCOOPS("Fuel Scoops", 5250),
    ESCAPE_POD("Escape Pod", 10000),
    ENERGY_BOMB("Energy Bomb", 9000),
    ENERGY_UNIT("Energy Unit", 15000),
    DOCKING_COMPUTER("Docking Computers", 10), // 1000 Cr no original; 1 Cr aqui, a pedido
    GALACTIC_HYPERDRIVE("Galactic Hyperspace", 50000),
    MILITARY_LASER("Military Lasers", 60000),
    MINING_LASER("Mining Lasers", 8000);

    /** Byte LASER do laser deste item (0 se não for um laser). */
    val laser: Int get() = when (this) {
        PULSE_LASER -> PlayerShip.PULSE_LASER
        BEAM_LASER -> PlayerShip.BEAM_LASER
        MILITARY_LASER -> PlayerShip.MILITARY_LASER
        MINING_LASER -> PlayerShip.MINING_LASER
        else -> 0
    }

    /** Lasers são instalados numa vista (qv: "VIEW ?"). */
    val needsView: Boolean get() = laser != 0

    enum class Result { OK, NO_CASH, PRESENT, ALL_PRESENT, NOT_AVAILABLE }

    companion object {
        const val MAX_MISSILES = 4
        const val LARGE_CARGO_CAPACITY = 35

        /**
         * QQ25: itens à venda num sistema de tech level interno [techLevel] (0..14): os
         * primeiros tek + 3; a partir de 12, todos os 14.
         */
        fun available(techLevel: Int): List<Equipment> =
            entries.take((techLevel + 3).let { if (it >= 12) entries.size else it })

        /** O item da loja de um byte LASER instalado (para o reembolso), ou null. */
        fun ofLaser(laser: Int): Equipment? = entries.firstOrNull { it.laser != 0 && it.laser == laser }

        /** Preço atual de [item] (o combustível depende do quanto falta para encher o tanque). */
        fun price(item: Equipment, player: PlayerShip): Long =
            if (item == FUEL) (70 - player.fuel.toInt()).coerceAtLeast(0) * FUEL.price else item.price

        /**
         * EQSHP: cobra o item e o instala, com as mesmas regras do original — mísseis até 4
         * ("ALL PRESENT"), um item só de cada (senão "PRESENT", sem cobrar) e, para os
         * lasers (refund), qualquer um substitui o da vista, devolvendo o preço do antigo;
         * só o mesmo tipo é recusado ("LASER PRESENT").
         */
        fun buy(
            item: Equipment,
            techLevel: Int,
            player: PlayerShip,
            commander: Commander,
            view: View = View.FRONT,
        ): Result {
            if (item !in available(techLevel)) return Result.NOT_AVAILABLE
            val laser = player.lasers[view.ordinal]
            val present = when (item) {
                FUEL -> player.fuel >= 70f
                MISSILE -> player.missiles >= MAX_MISSILES
                LARGE_CARGO_BAY -> commander.cargoCapacity >= LARGE_CARGO_CAPACITY
                ECM -> player.hasEcm
                PULSE_LASER, BEAM_LASER, MILITARY_LASER, MINING_LASER -> laser == item.laser
                FUEL_SCOOPS -> player.fuelScoops
                ESCAPE_POD -> player.escapePod
                ENERGY_BOMB -> player.energyBomb
                ENERGY_UNIT -> player.energyUnit || player.navyEnergyUnit
                DOCKING_COMPUTER -> player.dockingComputer
                GALACTIC_HYPERDRIVE -> player.galacticHyperdrive
            }
            if (present) return if (item == MISSILE) Result.ALL_PRESENT else Result.PRESENT
            // eq: o preço cheio precisa caber no bolso; depois, trocar um laser devolve o
            // preço do antigo (refund).
            if (!commander.spend(price(item, player))) return Result.NO_CASH
            if (item.needsView) ofLaser(laser)?.let { commander.earn(it.price) }
            when (item) {
                FUEL -> player.fuel = 70f
                MISSILE -> player.missiles++
                LARGE_CARGO_BAY -> commander.cargoCapacity = LARGE_CARGO_CAPACITY
                ECM -> player.hasEcm = true
                PULSE_LASER, BEAM_LASER, MILITARY_LASER, MINING_LASER -> player.lasers[view.ordinal] = item.laser
                FUEL_SCOOPS -> player.fuelScoops = true
                ESCAPE_POD -> player.escapePod = true
                ENERGY_BOMB -> player.energyBomb = true
                ENERGY_UNIT -> player.energyUnit = true
                DOCKING_COMPUTER -> player.dockingComputer = true
                GALACTIC_HYPERDRIVE -> player.galacticHyperdrive = true
            }
            return Result.OK
        }
    }
}
