package com.eliteandroid.ships

/**
 * Modelos gerados a partir de elite-source.asm (disassembly de Mark Moxon, versão cassete)
 * pelo script de extração: cabeçalho (bytes do bloco do modelo), VERTEX e EDGE na ordem original.
 * Não edite à mão.
 */
object OriginalShips {
    val SIDEWINDER: ShipBlueprint = ShipBlueprint.define("Sidewinder") {
        stats(energy = 70, speed = 37, laserByte = 16, bounty = 50, canisters = 0, gunVertex = 0, explosionVertices = 6)
        visibilityDistance = 20
        targetableArea = 4225
        vertex(-32, 0, 36) // 0
        vertex(32, 0, 36) // 1
        vertex(64, 0, -28) // 2
        vertex(-64, 0, -28) // 3
        vertex(0, 16, -28) // 4
        vertex(0, -16, -28) // 5
        vertex(-12, 6, -28) // 6
        vertex(12, 6, -28) // 7
        vertex(12, -6, -28) // 8
        vertex(-12, -6, -28) // 9
        edge(0, 1, 0, 5, 31) // 0
        edge(1, 2, 2, 6, 31) // 1
        edge(1, 4, 0, 2, 31) // 2
        edge(0, 4, 0, 1, 31) // 3
        edge(0, 3, 1, 4, 31) // 4
        edge(3, 4, 1, 3, 31) // 5
        edge(2, 4, 2, 3, 31) // 6
        edge(3, 5, 3, 4, 31) // 7
        edge(2, 5, 3, 6, 31) // 8
        edge(1, 5, 5, 6, 31) // 9
        edge(0, 5, 4, 5, 31) // 10
        edge(6, 7, 3, 3, 15) // 11
        edge(7, 8, 3, 3, 12) // 12
        edge(6, 9, 3, 3, 12) // 13
        edge(8, 9, 3, 3, 12) // 14
    }

    val VIPER: ShipBlueprint = ShipBlueprint.define("Viper") {
        stats(energy = 120, speed = 32, laserByte = 17, bounty = 0, canisters = 0, gunVertex = 0, explosionVertices = 9)
        visibilityDistance = 23
        targetableArea = 5625
        vertex(0, 0, 72) // 0
        vertex(0, 16, 24) // 1
        vertex(0, -16, 24) // 2
        vertex(48, 0, -24) // 3
        vertex(-48, 0, -24) // 4
        vertex(24, -16, -24) // 5
        vertex(-24, -16, -24) // 6
        vertex(24, 16, -24) // 7
        vertex(-24, 16, -24) // 8
        vertex(-32, 0, -24) // 9
        vertex(32, 0, -24) // 10
        vertex(8, 8, -24) // 11
        vertex(-8, 8, -24) // 12
        vertex(-8, -8, -24) // 13
        vertex(8, -8, -24) // 14
        edge(0, 3, 2, 4, 31) // 0
        edge(0, 1, 1, 2, 30) // 1
        edge(0, 2, 3, 4, 30) // 2
        edge(0, 4, 1, 3, 31) // 3
        edge(1, 7, 0, 2, 30) // 4
        edge(1, 8, 0, 1, 30) // 5
        edge(2, 5, 4, 5, 30) // 6
        edge(2, 6, 3, 5, 30) // 7
        edge(7, 8, 0, 6, 31) // 8
        edge(5, 6, 5, 6, 30) // 9
        edge(4, 8, 1, 6, 31) // 10
        edge(4, 6, 3, 6, 30) // 11
        edge(3, 7, 2, 6, 31) // 12
        edge(3, 5, 6, 4, 30) // 13
        edge(9, 12, 6, 6, 19) // 14
        edge(9, 13, 6, 6, 18) // 15
        edge(10, 11, 6, 6, 19) // 16
        edge(10, 14, 6, 6, 18) // 17
        edge(11, 14, 6, 6, 16) // 18
        edge(12, 13, 6, 6, 16) // 19
    }

    val MAMBA: ShipBlueprint = ShipBlueprint.define("Mamba") {
        stats(energy = 90, speed = 30, laserByte = 18, bounty = 150, canisters = 1, gunVertex = 0, explosionVertices = 7)
        visibilityDistance = 25
        targetableArea = 4900
        vertex(0, 0, 64) // 0
        vertex(-64, -8, -32) // 1
        vertex(-32, 8, -32) // 2
        vertex(32, 8, -32) // 3
        vertex(64, -8, -32) // 4
        vertex(-4, 4, 16) // 5
        vertex(4, 4, 16) // 6
        vertex(8, 3, 28) // 7
        vertex(-8, 3, 28) // 8
        vertex(-20, -4, 16) // 9
        vertex(20, -4, 16) // 10
        vertex(-24, -7, -20) // 11
        vertex(-16, -7, -20) // 12
        vertex(16, -7, -20) // 13
        vertex(24, -7, -20) // 14
        vertex(-8, 4, -32) // 15
        vertex(8, 4, -32) // 16
        vertex(8, -4, -32) // 17
        vertex(-8, -4, -32) // 18
        vertex(-32, 4, -32) // 19
        vertex(32, 4, -32) // 20
        vertex(36, -4, -32) // 21
        vertex(-36, -4, -32) // 22
        vertex(-38, 0, -32) // 23
        vertex(38, 0, -32) // 24
        edge(0, 1, 0, 2, 31) // 0
        edge(0, 4, 0, 3, 31) // 1
        edge(1, 4, 0, 4, 31) // 2
        edge(1, 2, 2, 4, 30) // 3
        edge(2, 3, 1, 4, 30) // 4
        edge(3, 4, 3, 4, 30) // 5
        edge(5, 6, 1, 1, 14) // 6
        edge(6, 7, 1, 1, 12) // 7
        edge(7, 8, 1, 1, 13) // 8
        edge(5, 8, 1, 1, 12) // 9
        edge(9, 11, 0, 0, 20) // 10
        edge(9, 12, 0, 0, 16) // 11
        edge(10, 13, 0, 0, 16) // 12
        edge(10, 14, 0, 0, 20) // 13
        edge(13, 14, 0, 0, 14) // 14
        edge(11, 12, 0, 0, 14) // 15
        edge(15, 16, 4, 4, 13) // 16
        edge(17, 18, 4, 4, 14) // 17
        edge(15, 18, 4, 4, 12) // 18
        edge(16, 17, 4, 4, 12) // 19
        edge(20, 21, 4, 4, 7) // 20
        edge(20, 24, 4, 4, 5) // 21
        edge(21, 24, 4, 4, 5) // 22
        edge(19, 22, 4, 4, 7) // 23
        edge(19, 23, 4, 4, 5) // 24
        edge(22, 23, 4, 4, 5) // 25
        edge(0, 2, 1, 2, 30) // 26
        edge(0, 3, 1, 3, 30) // 27
    }

    val PYTHON: ShipBlueprint = ShipBlueprint.define("Python") {
        stats(energy = 250, speed = 20, laserByte = 27, bounty = 200, canisters = 3, gunVertex = 0, explosionVertices = 10)
        visibilityDistance = 40
        targetableArea = 14400
        vertex(0, 0, 224) // 0
        vertex(0, 48, 48) // 1
        vertex(96, 0, -16) // 2
        vertex(-96, 0, -16) // 3
        vertex(0, 48, -32) // 4
        vertex(0, 24, -112) // 5
        vertex(-48, 0, -112) // 6
        vertex(48, 0, -112) // 7
        vertex(0, -48, 48) // 8
        vertex(0, -48, -32) // 9
        vertex(0, -24, -112) // 10
        edge(0, 8, 2, 3, 30) // 0
        edge(0, 3, 0, 2, 31) // 1
        edge(0, 2, 1, 3, 31) // 2
        edge(0, 1, 0, 1, 30) // 3
        edge(2, 4, 9, 5, 29) // 4
        edge(1, 2, 1, 5, 29) // 5
        edge(2, 8, 7, 3, 29) // 6
        edge(1, 3, 0, 4, 29) // 7
        edge(3, 8, 2, 6, 29) // 8
        edge(2, 9, 7, 10, 29) // 9
        edge(3, 4, 4, 8, 29) // 10
        edge(3, 9, 6, 11, 29) // 11
        edge(3, 5, 8, 8, 5) // 12
        edge(3, 10, 11, 11, 5) // 13
        edge(2, 5, 9, 9, 5) // 14
        edge(2, 10, 10, 10, 5) // 15
        edge(2, 7, 9, 10, 31) // 16
        edge(3, 6, 8, 11, 31) // 17
        edge(5, 6, 8, 12, 31) // 18
        edge(5, 7, 9, 12, 31) // 19
        edge(7, 10, 12, 10, 29) // 20
        edge(6, 10, 11, 12, 29) // 21
        edge(4, 5, 8, 9, 29) // 22
        edge(9, 10, 10, 11, 29) // 23
        edge(1, 4, 4, 5, 29) // 24
        edge(8, 9, 6, 7, 29) // 25
    }

    val MISSILE: ShipBlueprint = ShipBlueprint.define("Missile") {
        stats(energy = 2, speed = 44, laserByte = 0, bounty = 0, canisters = 0, gunVertex = 0, explosionVertices = 1)
        visibilityDistance = 14
        targetableArea = 1600
        vertex(0, 0, 68) // 0
        vertex(8, -8, 36) // 1
        vertex(8, 8, 36) // 2
        vertex(-8, 8, 36) // 3
        vertex(-8, -8, 36) // 4
        vertex(8, 8, -44) // 5
        vertex(8, -8, -44) // 6
        vertex(-8, -8, -44) // 7
        vertex(-8, 8, -44) // 8
        vertex(12, 12, -44) // 9
        vertex(12, -12, -44) // 10
        vertex(-12, -12, -44) // 11
        vertex(-12, 12, -44) // 12
        vertex(-8, 8, -12) // 13
        vertex(-8, -8, -12) // 14
        vertex(8, 8, -12) // 15
        vertex(8, -8, -12) // 16
        edge(0, 1, 1, 2, 31) // 0
        edge(0, 2, 2, 3, 31) // 1
        edge(0, 3, 0, 3, 31) // 2
        edge(0, 4, 0, 1, 31) // 3
        edge(1, 2, 4, 2, 31) // 4
        edge(1, 4, 1, 5, 31) // 5
        edge(3, 4, 0, 6, 31) // 6
        edge(2, 3, 3, 7, 31) // 7
        edge(2, 5, 4, 7, 31) // 8
        edge(1, 6, 4, 5, 31) // 9
        edge(4, 7, 5, 6, 31) // 10
        edge(3, 8, 6, 7, 31) // 11
        edge(7, 8, 6, 8, 31) // 12
        edge(5, 8, 7, 8, 31) // 13
        edge(5, 6, 4, 8, 31) // 14
        edge(6, 7, 5, 8, 31) // 15
        edge(6, 10, 5, 8, 8) // 16
        edge(5, 9, 7, 8, 8) // 17
        edge(8, 12, 7, 8, 8) // 18
        edge(7, 11, 5, 8, 8) // 19
        edge(9, 15, 4, 7, 8) // 20
        edge(10, 16, 4, 5, 8) // 21
        edge(12, 13, 6, 7, 8) // 22
        edge(11, 14, 5, 6, 8) // 23
    }

    val ASTEROID: ShipBlueprint = ShipBlueprint.define("Asteroid") {
        stats(energy = 60, speed = 30, laserByte = 0, bounty = 5, canisters = 0, gunVertex = 0, explosionVertices = 7)
        visibilityDistance = 50
        targetableArea = 6400
        vertex(0, 80, 0) // 0
        vertex(-80, -10, 0) // 1
        vertex(0, -80, 0) // 2
        vertex(70, -40, 0) // 3
        vertex(60, 50, 0) // 4
        vertex(50, 0, 60) // 5
        vertex(-40, 0, 70) // 6
        vertex(0, 30, -75) // 7
        vertex(0, -50, -60) // 8
        edge(0, 1, 2, 7, 31) // 0
        edge(0, 4, 6, 13, 31) // 1
        edge(3, 4, 5, 12, 31) // 2
        edge(2, 3, 4, 11, 31) // 3
        edge(1, 2, 3, 10, 31) // 4
        edge(1, 6, 2, 3, 31) // 5
        edge(2, 6, 1, 3, 31) // 6
        edge(2, 5, 1, 4, 31) // 7
        edge(5, 6, 0, 1, 31) // 8
        edge(0, 5, 0, 6, 31) // 9
        edge(3, 5, 4, 5, 31) // 10
        edge(0, 6, 0, 2, 31) // 11
        edge(4, 5, 5, 6, 31) // 12
        edge(1, 8, 8, 10, 31) // 13
        edge(1, 7, 7, 8, 31) // 14
        edge(0, 7, 7, 13, 31) // 15
        edge(4, 7, 12, 13, 31) // 16
        edge(3, 7, 9, 12, 31) // 17
        edge(3, 8, 9, 11, 31) // 18
        edge(2, 8, 10, 11, 31) // 19
        edge(7, 8, 8, 9, 31) // 20
    }

    val CANISTER: ShipBlueprint = ShipBlueprint.define("Canister") {
        stats(energy = 17, speed = 15, laserByte = 0, bounty = 0, canisters = 0, gunVertex = 0, explosionVertices = 3)
        visibilityDistance = 12
        targetableArea = 400
        vertex(24, 16, 0) // 0
        vertex(24, 5, 15) // 1
        vertex(24, -13, 9) // 2
        vertex(24, -13, -9) // 3
        vertex(24, 5, -15) // 4
        vertex(-24, 16, 0) // 5
        vertex(-24, 5, 15) // 6
        vertex(-24, -13, 9) // 7
        vertex(-24, -13, -9) // 8
        vertex(-24, 5, -15) // 9
        edge(0, 1, 0, 1, 31) // 0
        edge(1, 2, 0, 2, 31) // 1
        edge(2, 3, 0, 3, 31) // 2
        edge(3, 4, 0, 4, 31) // 3
        edge(0, 4, 0, 5, 31) // 4
        edge(0, 5, 1, 5, 31) // 5
        edge(1, 6, 1, 2, 31) // 6
        edge(2, 7, 2, 3, 31) // 7
        edge(3, 8, 3, 4, 31) // 8
        edge(4, 9, 4, 5, 31) // 9
        edge(5, 6, 1, 6, 31) // 10
        edge(6, 7, 2, 6, 31) // 11
        edge(7, 8, 3, 6, 31) // 12
        edge(8, 9, 4, 6, 31) // 13
        edge(9, 5, 5, 6, 31) // 14
    }

    val ESCAPE_POD: ShipBlueprint = ShipBlueprint.define("Escape Pod") {
        stats(energy = 17, speed = 8, laserByte = 0, bounty = 0, canisters = 0, gunVertex = 0, explosionVertices = 4)
        visibilityDistance = 8
        targetableArea = 256
        vertex(-7, 0, 36) // 0
        vertex(-7, -14, -12) // 1
        vertex(-7, 14, -12) // 2
        vertex(21, 0, 0) // 3
        edge(0, 1, 3, 2, 31) // 0
        edge(1, 2, 3, 0, 31) // 1
        edge(2, 3, 1, 0, 31) // 2
        edge(3, 0, 2, 1, 31) // 3
        edge(0, 2, 3, 1, 31) // 4
        edge(3, 1, 2, 0, 31) // 5
    }

}
