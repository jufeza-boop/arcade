package com.retroarcade.domain.tetris

class BoardGrid(
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT
) {
    companion object {
        const val DEFAULT_WIDTH = 10
        const val DEFAULT_HEIGHT = 20
    }

    // Matriz de celdas: 0 = vacía, 1..7 = color del TetrominoType
    val cells: Array<IntArray> = Array(height) { IntArray(width) { 0 } }

    fun isInsideBounds(col: Int, row: Int): Boolean {
        return col in 0 until width && row >= 0
    }

    fun isCellEmpty(col: Int, row: Int): Boolean {
        if (!isInsideBounds(col, row)) return false
        if (row >= height) return true // Por encima del tablero se considera vacío
        return cells[row][col] == 0
    }

    fun canPlace(tetromino: Tetromino): Boolean {
        for ((col, row) in tetromino.getBlockPositions()) {
            if (col !in 0 until width || row < 0) return false
            if (row < height && cells[row][col] != 0) return false
        }
        return true
    }

    fun lockTetromino(tetromino: Tetromino) {
        val color = tetromino.type.colorIndex
        for ((col, row) in tetromino.getBlockPositions()) {
            if (row in 0 until height && col in 0 until width) {
                cells[row][col] = color
            }
        }
    }

    /**
     * Revisa todas las filas de abajo hacia arriba.
     * Si una fila está completa, la elimina y desplaza hacia abajo las filas superiores.
     * Retorna el número de líneas eliminadas (0..4).
     */
    fun clearFullLines(): Int {
        var linesCleared = 0
        var row = 0

        while (row < height) {
            val isFull = cells[row].all { it != 0 }
            if (isFull) {
                linesCleared++
                // Desplazar todas las filas superiores una casilla hacia abajo
                for (r in row until height - 1) {
                    cells[r] = cells[r + 1].clone()
                }
                // Limpiar la fila superior
                cells[height - 1] = IntArray(width) { 0 }
            } else {
                row++
            }
        }

        return linesCleared
    }

    fun isGameOver(): Boolean {
        // Si hay bloques fijados en la fila superior (fuera del pozo jugable normal)
        return cells[height - 1].any { it != 0 }
    }

    fun clear() {
        for (r in 0 until height) {
            cells[r].fill(0)
        }
    }
}
