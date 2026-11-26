package com.retroarcade.domain.pacman

import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PacmanEngineTest {

    private lateinit var engine: PacmanEngine

    @BeforeEach
    fun setUp() {
        engine = PacmanEngine(random = Random(42))
    }

    @Test
    fun pacmanMovesAlongCorridorAndStopsAtWall() {
        // Spawn en (9.5, 5.5); en fila 5, col 14 es el último paso antes del muro en col 15
        engine.handleInput(InputCommand.MoveRight)
        repeat(100) {
            engine.update(0.02f)
        }

        assertEquals(14.5f, engine.pacmanX, 0.01f)
        assertEquals(5.5f, engine.pacmanY, 0.01f)
    }

    @Test
    fun pacmanTurnsAtIntersectionWhenNextDirIsClear() {
        // Desde (9.5, 5.5) hacia la izquierda, al llegar a col 8 (fila 6 col 8 es transitable) gira UP
        engine.setNextDirection(PacDirection.LEFT)
        engine.update(0.05f)
        engine.setNextDirection(PacDirection.UP)

        repeat(25) {
            engine.update(0.02f)
        }

        assertEquals(PacDirection.UP, engine.pacmanDir)
        assertEquals(8.5f, engine.pacmanX, 0.01f)
        assertTrue(engine.pacmanY > 5.5f, "Pac-Man debería haber subido por la intersección en col 8")
    }

    @Test
    fun tunnelTeleportsPacmanFromLeftToRightAndViceVersa() {
        // Fila 11 es el túnel lateral (col 0 y col 18)
        engine.setPositionForTest(0.5f, 11.5f, PacDirection.LEFT)
        engine.update(0.25f)
        assertTrue(engine.pacmanX > 17f, "Al salir por la izquierda debe reaparecer en el extremo derecho, x=${engine.pacmanX}")

        engine.setPositionForTest(18.5f, 11.5f, PacDirection.RIGHT)
        engine.update(0.25f)
        assertTrue(engine.pacmanX < 2f, "Al salir por la derecha debe reaparecer en el extremo izquierdo, x=${engine.pacmanX}")
    }

    @Test
    fun eatingDotIncreasesScoreAndDecreasesDotsRemaining() {
        val initialDots = engine.dotsRemaining
        assertEquals(0, engine.score)

        // (10, 5) es DOT justo a la derecha del spawn (9.5, 5.5)
        engine.handleInput(InputCommand.MoveRight)
        repeat(12) {
            engine.update(0.02f)
        }

        assertEquals(10, engine.score)
        assertEquals(initialDots - 1, engine.dotsRemaining)
    }

    @Test
    fun eatingPowerPelletSwitchesGhostsToFrightenedMode() {
        // (1, 5) es POWER_PELLET; colocamos a Pac-Man en (2.5, 5.5) moviéndose a la izquierda
        engine.setPositionForTest(2.5f, 5.5f, PacDirection.LEFT)
        repeat(15) {
            engine.update(0.02f)
        }

        assertTrue(engine.score >= 50, "Comer píldora de poder debe sumar al menos 50 puntos")
        assertTrue(engine.frightenedTimer > 0f, "El temporizador FRIGHTENED debe estar activo")
        assertTrue(engine.ghosts.all { it.mode == GhostMode.FRIGHTENED })
    }

    @Test
    fun eatingFrightenedGhostsAwardsChainedPoints200_400_800_1600() {
        // Colocar a Pac-Man sobre la píldora de poder en (1, 5)
        engine.setTileForTest(1, 5, TileType.POWER_PELLET)
        engine.setPositionForTest(1.5f, 5.5f, PacDirection.NONE)
        engine.update(0.01f)
        assertEquals(50, engine.score)

        // Comer los 4 fantasmas en cadena mientras están en FRIGHTENED
        val expectedScores = listOf(250, 650, 1450, 3050)
        engine.ghosts.forEachIndexed { index, ghost ->
            ghost.x = engine.pacmanX
            ghost.y = engine.pacmanY
            engine.update(0.001f)
            assertEquals(GhostMode.EATEN, ghost.mode)
            assertEquals(expectedScores[index], engine.score)
        }
    }

    @Test
    fun collidingWithChaseGhostReducesLivesAndGameOverAtZero() {
        assertEquals(3, engine.lives)
        assertEquals(GameStatus.RUNNING, engine.status)

        // Colisión 1
        engine.ghosts[0].x = engine.pacmanX
        engine.ghosts[0].y = engine.pacmanY
        engine.update(0.001f)
        assertEquals(2, engine.lives)
        assertEquals(GameStatus.RUNNING, engine.status)

        // Colisión 2
        engine.ghosts[0].x = engine.pacmanX
        engine.ghosts[0].y = engine.pacmanY
        engine.update(0.001f)
        assertEquals(1, engine.lives)
        assertEquals(GameStatus.RUNNING, engine.status)

        // Colisión 3 -> GAME_OVER
        engine.ghosts[0].x = engine.pacmanX
        engine.ghosts[0].y = engine.pacmanY
        engine.update(0.001f)
        assertEquals(0, engine.lives)
        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    fun clearingAllDotsAdvancesToNextLevelAndResetsBoard() {
        val totalDotsFullMaze = engine.dotsRemaining
        engine.clearDotsExceptOneForTest(keepCol = 10, keepRow = 5)
        assertEquals(1, engine.dotsRemaining)

        engine.handleInput(InputCommand.MoveRight)
        repeat(15) {
            engine.update(0.02f)
        }

        assertEquals(2, engine.level)
        assertEquals(totalDotsFullMaze, engine.dotsRemaining)
    }

    @Test
    fun resetRestoresInitialState() {
        val initialDots = engine.dotsRemaining

        engine.handleInput(InputCommand.MoveRight)
        repeat(12) { engine.update(0.02f) }
        engine.ghosts[0].x = engine.pacmanX
        engine.ghosts[0].y = engine.pacmanY
        engine.update(0.001f)
        engine.togglePause()

        engine.reset()

        assertEquals(0, engine.score)
        assertEquals(1, engine.level)
        assertEquals(3, engine.lives)
        assertEquals(GameStatus.RUNNING, engine.status)
        assertEquals(0f, engine.frightenedTimer)
        assertEquals(initialDots, engine.dotsRemaining)
    }
}
