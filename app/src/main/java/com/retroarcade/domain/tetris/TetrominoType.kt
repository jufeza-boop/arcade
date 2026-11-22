package com.retroarcade.domain.tetris

/**
 * Los 7 tipos estándar de tetrominós.
 * Cada tipo define su representación de bloques en 4 rotaciones (0, 1, 2, 3)
 * usando coordenadas locales relativas a su pivote central.
 */
enum class TetrominoType(
    val colorIndex: Int, // 1: Cian, 2: Amarillo, 3: Magenta, 4: Verde, 5: Rojo, 6: Azul, 7: Naranja
    private val rotationShapes: Array<Array<Pair<Int, Int>>>
) {
    // I: Pieza línea (Cian)
    I(
        colorIndex = 1,
        rotationShapes = arrayOf(
            arrayOf(-1 to 0, 0 to 0, 1 to 0, 2 to 0),
            arrayOf(1 to -1, 1 to 0, 1 to 1, 1 to 2),
            arrayOf(-1 to 1, 0 to 1, 1 to 1, 2 to 1),
            arrayOf(0 to -1, 0 to 0, 0 to 1, 0 to 2)
        )
    ),

    // O: Cuadrado 2x2 (Amarillo - rotación idéntica)
    O(
        colorIndex = 2,
        rotationShapes = arrayOf(
            arrayOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            arrayOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            arrayOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            arrayOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
        )
    ),

    // T: Forma de T (Magenta)
    T(
        colorIndex = 3,
        rotationShapes = arrayOf(
            arrayOf(-1 to 0, 0 to 0, 1 to 0, 0 to 1),
            arrayOf(0 to -1, 0 to 0, 0 to 1, 1 to 0),
            arrayOf(-1 to 0, 0 to 0, 1 to 0, 0 to -1),
            arrayOf(0 to -1, 0 to 0, 0 to 1, -1 to 0)
        )
    ),

    // S: Pieza serpiente S (Verde)
    S(
        colorIndex = 4,
        rotationShapes = arrayOf(
            arrayOf(-1 to 0, 0 to 0, 0 to 1, 1 to 1),
            arrayOf(1 to -1, 1 to 0, 0 to 0, 0 to 1),
            arrayOf(-1 to -1, 0 to -1, 0 to 0, 1 to 0),
            arrayOf(0 to -1, 0 to 0, -1 to 0, -1 to 1)
        )
    ),

    // Z: Pieza serpiente Z (Rojo)
    Z(
        colorIndex = 5,
        rotationShapes = arrayOf(
            arrayOf(-1 to 1, 0 to 1, 0 to 0, 1 to 0),
            arrayOf(0 to -1, 0 to 0, 1 to 0, 1 to 1),
            arrayOf(-1 to 0, 0 to 0, 0 to -1, 1 to -1),
            arrayOf(-1 to -1, -1 to 0, 0 to 0, 0 to 1)
        )
    ),

    // J: Forma de J (Azul)
    J(
        colorIndex = 6,
        rotationShapes = arrayOf(
            arrayOf(-1 to 1, -1 to 0, 0 to 0, 1 to 0),
            arrayOf(0 to 1, 0 to 0, 0 to -1, 1 to 1),
            arrayOf(-1 to 0, 0 to 0, 1 to 0, 1 to -1),
            arrayOf(-1 to -1, 0 to -1, 0 to 0, 0 to 1)
        )
    ),

    // L: Forma de L (Naranja)
    L(
        colorIndex = 7,
        rotationShapes = arrayOf(
            arrayOf(-1 to 0, 0 to 0, 1 to 0, 1 to 1),
            arrayOf(0 to -1, 0 to 0, 0 to 1, 1 to -1),
            arrayOf(-1 to -1, -1 to 0, 0 to 0, 1 to 0),
            arrayOf(-1 to 1, 0 to 1, 0 to 0, 0 to -1)
        )
    );

    fun getOffsets(rotation: Int): Array<Pair<Int, Int>> {
        val normRot = Math.floorMod(rotation, 4)
        return rotationShapes[normRot]
    }
}
