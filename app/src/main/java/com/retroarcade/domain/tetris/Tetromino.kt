package com.retroarcade.domain.tetris

data class Tetromino(
    val type: TetrominoType,
    val x: Int = 4, // Columna central en tablero 10x20
    val y: Int = 18, // Fila superior de spawn
    val rotation: Int = 0
) {
    /**
     * Devuelve las posiciones absolutas (columna, fila) de los 4 bloques.
     */
    fun getBlockPositions(): List<Pair<Int, Int>> {
        val offsets = type.getOffsets(rotation)
        return offsets.map { (dx, dy) ->
            (x + dx) to (y + dy)
        }
    }

    fun moveBy(dx: Int, dy: Int): Tetromino {
        return copy(x = x + dx, y = y + dy)
    }

    fun rotateClockwise(): Tetromino {
        return copy(rotation = (rotation + 1) % 4)
    }

    fun rotateCounterClockwise(): Tetromino {
        return copy(rotation = if (rotation - 1 < 0) 3 else rotation - 1)
    }
}
