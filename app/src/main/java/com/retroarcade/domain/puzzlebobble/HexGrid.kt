package com.retroarcade.domain.puzzlebobble

import java.util.ArrayDeque

/**
 * Rejilla hexagonal de Puzzle Bobble.
 * Las filas pares tienen 8 columnas y las impares 7 columnas.
 * La fila 0 está siempre adosada al techo.
 */
class HexGrid(
    val maxRows: Int = DEFAULT_MAX_ROWS
) {
    companion object {
        const val DEFAULT_MAX_ROWS = 14
        const val COLS_EVEN = 8
        const val COLS_ODD = 7
    }

    // Matriz de burbujas: grid[row][col]
    val cells: Array<Array<BubbleColor>> = Array(maxRows) { row ->
        val cols = if (row % 2 == 0) COLS_EVEN else COLS_ODD
        Array(cols) { BubbleColor.NONE }
    }

    fun getColsInRow(row: Int): Int {
        return if (row % 2 == 0) COLS_EVEN else COLS_ODD
    }

    fun isInside(row: Int, col: Int): Boolean {
        return row in 0 until maxRows && col in 0 until getColsInRow(row)
    }

    fun getBubble(row: Int, col: Int): BubbleColor {
        if (!isInside(row, col)) return BubbleColor.NONE
        return cells[row][col]
    }

    fun setBubble(row: Int, col: Int, color: BubbleColor) {
        if (isInside(row, col)) {
            cells[row][col] = color
        }
    }

    fun getNeighbors(row: Int, col: Int): List<Pair<Int, Int>> {
        val neighbors = mutableListOf<Pair<Int, Int>>()
        val isEven = (row % 2 == 0)

        val potential = if (isEven) {
            listOf(
                row to col - 1, // Izquierda
                row to col + 1, // Derecha
                row - 1 to col - 1, // Arriba-Izquierda
                row - 1 to col,     // Arriba-Derecha
                row + 1 to col - 1, // Abajo-Izquierda
                row + 1 to col      // Abajo-Derecha
            )
        } else {
            listOf(
                row to col - 1, // Izquierda
                row to col + 1, // Derecha
                row - 1 to col,     // Arriba-Izquierda
                row - 1 to col + 1, // Arriba-Derecha
                row + 1 to col,     // Abajo-Izquierda
                row + 1 to col + 1  // Abajo-Derecha
            )
        }

        for ((r, c) in potential) {
            if (isInside(r, c)) {
                neighbors.add(r to c)
            }
        }
        return neighbors
    }

    fun findConnectedCluster(startRow: Int, startCol: Int): List<Pair<Int, Int>> {
        val targetColor = getBubble(startRow, startCol)
        if (targetColor == BubbleColor.NONE) return emptyList()

        val cluster = mutableListOf<Pair<Int, Int>>()
        val visited = Array(maxRows) { r -> BooleanArray(getColsInRow(r)) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startRow to startCol)
        visited[startRow][startCol] = true

        while (queue.isNotEmpty()) {
            val curr = queue.poll()!!
            cluster.add(curr)

            for (neighbor in getNeighbors(curr.first, curr.second)) {
                val (nr, nc) = neighbor
                if (!visited[nr][nc] && getBubble(nr, nc) == targetColor) {
                    visited[nr][nc] = true
                    queue.add(neighbor)
                }
            }
        }

        return cluster
    }

    /**
     * Identifica las burbujas que ya no están conectadas (directa o indirectamente) a la fila superior (techo).
     */
    fun findOrphanBubbles(): List<Pair<Int, Int>> {
        val connectedToCeiling = Array(maxRows) { r -> BooleanArray(getColsInRow(r)) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        // Todas las burbujas existentes en la fila 0 (ancladas al techo)
        for (c in 0 until getColsInRow(0)) {
            if (getBubble(0, c) != BubbleColor.NONE) {
                queue.add(0 to c)
                connectedToCeiling[0][c] = true
            }
        }

        while (queue.isNotEmpty()) {
            val curr = queue.poll()!!
            for (neighbor in getNeighbors(curr.first, curr.second)) {
                val (nr, nc) = neighbor
                if (!connectedToCeiling[nr][nc] && getBubble(nr, nc) != BubbleColor.NONE) {
                    connectedToCeiling[nr][nc] = true
                    queue.add(neighbor)
                }
            }
        }

        // Cualquier burbuja ocupada no conectada a la fila 0 es huérfana
        val orphans = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until maxRows) {
            for (c in 0 until getColsInRow(r)) {
                if (getBubble(r, c) != BubbleColor.NONE && !connectedToCeiling[r][c]) {
                    orphans.add(r to c)
                }
            }
        }

        return orphans
    }

    fun isEmpty(): Boolean {
        for (r in 0 until maxRows) {
            for (c in 0 until getColsInRow(r)) {
                if (cells[r][c] != BubbleColor.NONE) return false
            }
        }
        return true
    }

    fun clear() {
        for (r in 0 until maxRows) {
            for (c in 0 until getColsInRow(r)) {
                cells[r][c] = BubbleColor.NONE
            }
        }
    }
}
