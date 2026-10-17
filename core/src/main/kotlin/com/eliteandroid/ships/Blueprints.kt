package com.eliteandroid.ships

/**
 * Modelos originais do BBC Elite (disassembly de Mark Moxon, versão cassete:
 * SHIP_CORIOLIS e SHIP_COBRA_MK_3). Vértices e arestas na mesma ordem do original,
 * conferidos com elite-source.asm; os comentários indicam os índices como no fonte.
 */
object Blueprints {

    /** Estação espacial Coriolis: cuboctaedro de 14 faces com a fenda de atracação na face 0 (frente). */
    val CORIOLIS: ShipBlueprint = ShipBlueprint.define("Coriolis Station") {
        visibilityDistance = 120
        targetableArea = 160 * 160
        vertex(160, 0, 160)      // 0
        vertex(0, 160, 160)      // 1
        vertex(-160, 0, 160)     // 2
        vertex(0, -160, 160)     // 3
        vertex(160, -160, 0)     // 4
        vertex(160, 160, 0)      // 5
        vertex(-160, 160, 0)     // 6
        vertex(-160, -160, 0)    // 7
        vertex(160, 0, -160)     // 8
        vertex(0, 160, -160)     // 9
        vertex(-160, 0, -160)    // 10
        vertex(0, -160, -160)    // 11
        vertex(10, -30, 160)     // 12  fenda de atracação
        vertex(10, 30, 160)      // 13
        vertex(-10, 30, 160)     // 14
        vertex(-10, -30, 160)    // 15

        edge(0, 3, 0, 1, 31)     // 0
        edge(0, 1, 0, 2, 31)     // 1
        edge(1, 2, 0, 3, 31)     // 2
        edge(2, 3, 0, 4, 31)     // 3
        edge(3, 4, 1, 5, 31)     // 4
        edge(0, 4, 1, 6, 31)     // 5
        edge(0, 5, 2, 6, 31)     // 6
        edge(5, 1, 2, 8, 31)     // 7
        edge(1, 6, 3, 8, 31)     // 8
        edge(2, 6, 3, 7, 31)     // 9
        edge(2, 7, 4, 7, 31)     // 10
        edge(3, 7, 4, 5, 31)     // 11
        edge(8, 11, 10, 13, 31)  // 12
        edge(8, 9, 11, 13, 31)   // 13
        edge(9, 10, 12, 13, 31)  // 14
        edge(10, 11, 9, 13, 31)  // 15
        edge(4, 11, 5, 10, 31)   // 16
        edge(4, 8, 6, 10, 31)    // 17
        edge(5, 8, 6, 11, 31)    // 18
        edge(5, 9, 8, 11, 31)    // 19
        edge(6, 9, 8, 12, 31)    // 20
        edge(6, 10, 7, 12, 31)   // 21
        edge(7, 10, 7, 9, 31)    // 22
        edge(7, 11, 5, 9, 31)    // 23
        edge(12, 13, 0, 0, 30)   // 24  fenda
        edge(13, 14, 0, 0, 30)   // 25
        edge(14, 15, 0, 0, 30)   // 26
        edge(15, 12, 0, 0, 30)   // 27
    }

    /** Cobra Mk III: a nave do jogador. */
    val COBRA_MK3: ShipBlueprint = ShipBlueprint.define("Cobra Mk III") {
        visibilityDistance = 50
        targetableArea = 95 * 95
        vertex(32, 0, 76)        // 0
        vertex(-32, 0, 76)       // 1
        vertex(0, 26, 24)        // 2
        vertex(-120, -3, -8)     // 3
        vertex(120, -3, -8)      // 4
        vertex(-88, 16, -40)     // 5
        vertex(88, 16, -40)      // 6
        vertex(128, -8, -40)     // 7
        vertex(-128, -8, -40)    // 8
        vertex(0, 26, -40)       // 9
        vertex(-32, -24, -40)    // 10
        vertex(32, -24, -40)     // 11
        vertex(-36, 8, -40)      // 12  detalhes da traseira (motores)
        vertex(-8, 12, -40)      // 13
        vertex(8, 12, -40)       // 14
        vertex(36, 8, -40)       // 15
        vertex(36, -12, -40)     // 16
        vertex(8, -16, -40)      // 17
        vertex(-8, -16, -40)     // 18
        vertex(-36, -12, -40)    // 19
        vertex(0, 0, 76)         // 20  canhão
        vertex(0, 0, 90)         // 21
        vertex(-80, -6, -40)     // 22  retro-foguetes
        vertex(-80, 6, -40)      // 23
        vertex(-88, 0, -40)      // 24
        vertex(80, 6, -40)       // 25
        vertex(88, 0, -40)       // 26
        vertex(80, -6, -40)      // 27

        edge(0, 1, 0, 11, 31)    // 0
        edge(0, 4, 4, 12, 31)    // 1
        edge(1, 3, 3, 10, 31)    // 2
        edge(3, 8, 7, 10, 31)    // 3
        edge(4, 7, 8, 12, 31)    // 4
        edge(6, 7, 8, 9, 31)     // 5
        edge(6, 9, 6, 9, 31)     // 6
        edge(5, 9, 5, 9, 31)     // 7
        edge(5, 8, 7, 9, 31)     // 8
        edge(2, 5, 1, 5, 31)     // 9
        edge(2, 6, 2, 6, 31)     // 10
        edge(3, 5, 3, 7, 31)     // 11
        edge(4, 6, 4, 8, 31)     // 12
        edge(1, 2, 0, 1, 31)     // 13
        edge(0, 2, 0, 2, 31)     // 14
        edge(8, 10, 9, 10, 31)   // 15
        edge(10, 11, 9, 11, 31)  // 16
        edge(7, 11, 9, 12, 31)   // 17
        edge(1, 10, 10, 11, 31)  // 18
        edge(0, 11, 11, 12, 31)  // 19
        edge(1, 5, 1, 3, 29)     // 20
        edge(0, 6, 2, 4, 29)     // 21
        edge(20, 21, 0, 11, 6, detail = true) // 22  canhão
        edge(12, 13, 9, 9, 20)   // 23
        edge(18, 19, 9, 9, 20)   // 24
        edge(14, 15, 9, 9, 20)   // 25
        edge(16, 17, 9, 9, 20)   // 26
        edge(15, 16, 9, 9, 19)   // 27
        edge(14, 17, 9, 9, 17)   // 28
        edge(13, 18, 9, 9, 19)   // 29
        edge(12, 19, 9, 9, 19)   // 30
        edge(2, 9, 5, 6, 30)     // 31
        edge(22, 24, 9, 9, 6)    // 32
        edge(23, 24, 9, 9, 6)    // 33
        edge(22, 23, 9, 9, 8)    // 34
        edge(25, 26, 9, 9, 6)    // 35
        edge(26, 27, 9, 9, 6)    // 36
        edge(25, 27, 9, 9, 8)    // 37
    }
}
