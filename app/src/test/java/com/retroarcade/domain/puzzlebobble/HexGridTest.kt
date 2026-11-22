package com.retroarcade.domain.puzzlebobble

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HexGridTest {

    private lateinit var grid: HexGrid

    @BeforeEach
    fun setUp() {
        grid = HexGrid(maxRows = 10)
    }

    @Test
    @DisplayName("Filas pares tienen 8 columnas y filas impares tienen 7 columnas")
    fun testGridDimensions() {
        assertEquals(8, grid.getColsInRow(0))
        assertEquals(7, grid.getColsInRow(1))
        assertEquals(8, grid.getColsInRow(2))
        assertEquals(7, grid.getColsInRow(3))
    }

    @Test
    @DisplayName("Vecindad hexagonal identifica hasta 6 vecinos correctamente")
    fun testHexNeighbors() {
        val neighborsEven = grid.getNeighbors(2, 3)
        assertEquals(6, neighborsEven.size)
        assertTrue(neighborsEven.contains(2 to 2))
        assertTrue(neighborsEven.contains(2 to 4))
        assertTrue(neighborsEven.contains(1 to 2))
        assertTrue(neighborsEven.contains(1 to 3))
        assertTrue(neighborsEven.contains(3 to 2))
        assertTrue(neighborsEven.contains(3 to 3))

        val cornerNeighbors = grid.getNeighbors(0, 0)
        assertTrue(cornerNeighbors.size < 6)
        assertTrue(cornerNeighbors.contains(0 to 1))
        assertTrue(cornerNeighbors.contains(1 to 0))
    }

    @Test
    @DisplayName("BFS detecta grupos de burbujas conectadas del mismo color")
    fun testClusterDetection() {
        grid.setBubble(0, 2, BubbleColor.YELLOW)
        grid.setBubble(0, 3, BubbleColor.YELLOW)
        grid.setBubble(1, 2, BubbleColor.YELLOW)
        grid.setBubble(0, 4, BubbleColor.BLUE)

        val cluster = grid.findConnectedCluster(0, 2)
        assertEquals(3, cluster.size)
        assertTrue(cluster.contains(0 to 2))
        assertTrue(cluster.contains(0 to 3))
        assertTrue(cluster.contains(1 to 2))
        assertFalse(cluster.contains(0 to 4))
    }

    @Test
    @DisplayName("Identificación de burbujas huérfanas desprendidas del techo")
    fun testFindOrphanBubbles() {
        grid.setBubble(0, 0, BubbleColor.BLUE)
        grid.setBubble(1, 0, BubbleColor.BLUE)

        grid.setBubble(4, 2, BubbleColor.RED)
        grid.setBubble(4, 3, BubbleColor.RED)

        val orphans = grid.findOrphanBubbles()
        assertEquals(2, orphans.size)
        assertTrue(orphans.contains(4 to 2))
        assertTrue(orphans.contains(4 to 3))
        assertFalse(orphans.contains(0 to 0))
        assertFalse(orphans.contains(1 to 0))
    }

    @Test
    @DisplayName("isEmpty identifica correctamente si no queda ninguna burbuja en el pozo")
    fun testIsEmpty() {
        assertTrue(grid.isEmpty())
        grid.setBubble(0, 3, BubbleColor.GREEN)
        assertFalse(grid.isEmpty())
        grid.setBubble(0, 3, BubbleColor.NONE)
        assertTrue(grid.isEmpty())
    }
}
