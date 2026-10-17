package com.eliteandroid.economy

import com.eliteandroid.flight.PlayerShip
import com.eliteandroid.flight.View

/**
 * Itens da loja de equipamentos (EQSHP), na ordem e com os preços da tabela PRXS do
 * original (em décimos de crédito). O combustível custa 2 décimos por 0.1 LY.
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
    DOCKING_COMPUTER("Docking Computers", 10000),
    GALACTIC_HYPERDRIVE("Galactic Hyperspace", 50000);

    /** Lasers são instalados numa vista (qv: "VIEW ?"). */
    val needsView: Boolean get() = this == PULSE_LASER || this == BEAM_LASER

    enum class Result { OK, NO_CASH, PRESENT, ALL_PRESENT, NOT_AVAILABLE }

    companion object {
        const val MAX_MISSILES = 4
        const val LARGE_CARGO_CAPACITY = 35

        /** QQ25: itens à venda num sistema de tech level interno [techLevel] (0..14): até tek + 3. */
        fun available(techLevel: Int): List<Equipment> = entries.take(minOf(techLevel + 3, entries.size))

        /** Preço atual de [item] (o combustível depende do quanto falta para encher o tanque). */
        fun price(item: Equipment, player: PlayerShip): Long =
            if (item == FUEL) (70 - player.fuel.toInt()).coerceAtLeast(0) * FUEL.price else item.price

        /**
         * EQSHP: cobra o item e o instala, com as mesmas regras do original — mísseis até 4
         * ("ALL PRESENT"), um item só de cada (senão "PRESENT", sem cobrar), laser de pulso
         * só numa vista sem laser e laser de feixe substituindo o de pulso (que é
         * reembolsado).
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
                PULSE_LASER -> laser != 0
                BEAM_LASER -> laser and 0x80 != 0
                FUEL_SCOOPS -> player.fuelScoops
                ESCAPE_POD -> player.escapePod
                ENERGY_BOMB -> player.energyBomb
                ENERGY_UNIT -> player.energyUnit
                DOCKING_COMPUTER -> player.dockingComputer
                GALACTIC_HYPERDRIVE -> player.galacticHyperdrive
            }
            if (present) return if (item == MISSILE) Result.ALL_PRESENT else Result.PRESENT
            // eq: o preço cheio precisa caber no bolso; trocar um laser de pulso pelo de
            // feixe devolve depois o preço do de pulso (cpl).
            if (!commander.spend(price(item, player))) return Result.NO_CASH
            if (item == BEAM_LASER && laser != 0) commander.earn(PULSE_LASER.price)
            when (item) {
                FUEL -> player.fuel = 70f
                MISSILE -> player.missiles++
                LARGE_CARGO_BAY -> commander.cargoCapacity = LARGE_CARGO_CAPACITY
                ECM -> player.hasEcm = true
                PULSE_LASER -> player.lasers[view.ordinal] = PlayerShip.PULSE_LASER
                BEAM_LASER -> player.lasers[view.ordinal] = PlayerShip.BEAM_LASER
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
