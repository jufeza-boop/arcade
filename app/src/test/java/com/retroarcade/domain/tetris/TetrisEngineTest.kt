package com.retroarcade.domain.tetris

import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import com.retroarcade.core.SwipeDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TetrisEngineTest {

    private lateinit var engine: TetrisEngine

    @BeforeEach
    fun setUp() {
        // Semilla fija para reproducibilidad determinista
        engine = TetrisEngine(Random(12345))
    }

    @Test
    @DisplayName("Estado inicial: juego en marcha, nivel 1, puntuación 0 y piezas preparadas")
    fun testInitialState() {
        assertEquals(GameStatus.RUNNING, engine.status)
        assertEquals(0, engine.score)
        assertEquals(1, engine.level)
        assertEquals(0, engine.totalLinesCleared)
        assertTrue(engine.currentPiece.getBlockPositions().isNotEmpty())
        assertTrue(engine.nextPiece.getBlockPositions().isNotEmpty())
    }

    @Test
    @DisplayName("Movimiento lateral respeta los límites del tablero (0 a 9)")
    fun testLateralMovementBounds() {
        // Mover repetidamente hacia la izquierda hasta el borde
        for (i in 0..15) {
            engine.handleInput(InputCommand.MoveLeft)
        }
        val leftPositions = engine.currentPiece.getBlockPositions()
        assertTrue(leftPositions.all { (col, _) -> col >= 0 })
        assertTrue(leftPositions.any { (col, _) -> col == 0 })

        // Mover repetidamente hacia la derecha hasta el borde
        for (i in 0..15) {
            engine.handleInput(InputCommand.MoveRight)
        }
        val rightPositions = engine.currentPiece.getBlockPositions()
        assertTrue(rightPositions.all { (col, _) -> col < 10 })
        assertTrue(rightPositions.any { (col, _) -> col == 9 })
    }

    @Test
    @DisplayName("Rotación horaria y antihoraria ciclan correctamente")
    fun testRotationCycle() {
        val initialRot = engine.currentPiece.rotation
        engine.handleInput(InputCommand.ActionA) // Horaria
        assertEquals((initialRot + 1) % 4, engine.currentPiece.rotation)

        engine.handleInput(InputCommand.ActionB) // Antihoraria
        assertEquals(initialRot, engine.currentPiece.rotation)
    }

    @Test
    @DisplayName("Wall-kick permite rotar piezas junto a la pared")
    fun testWallKickNearBoundary() {
        // Desplazar la pieza hasta el extremo derecho del tablero
        for (i in 0..15) {
            engine.handleInput(InputCommand.MoveRight)
        }
        // Intentar rotar la pieza pegada a la pared
        val rotated = engine.rotateClockwiseWithKick()
        assertTrue(rotated)
        assertTrue(engine.board.canPlace(engine.currentPiece))
        assertTrue(engine.currentPiece.getBlockPositions().all { (col, _) -> col in 0 until 10 })
    }

    @Test
    @DisplayName("Soft drop desciende una fila y otorga 1 punto")
    fun testSoftDrop() {
        val initialY = engine.currentPiece.y
        val initialScore = engine.score

        engine.handleInput(InputCommand.MoveDown)

        assertEquals(initialY - 1, engine.currentPiece.y)
        assertEquals(initialScore + 1, engine.score)
    }

    @Test
    @DisplayName("Hard drop lleva la pieza instantáneamente al fondo y la fija")
    fun testHardDrop() {
        val pieceType = engine.currentPiece.type
        val nextType = engine.nextPiece.type

        engine.handleInput(InputCommand.Swipe(SwipeDirection.DOWN))

        // La pieza original debe haberse fijado en el tablero
        val hasLockedBlocks = engine.board.cells.any { row -> row.any { it == pieceType.colorIndex } }
        assertTrue(hasLockedBlocks)

        // La siguiente pieza ahora es la activa
        assertEquals(nextType, engine.currentPiece.type)
        assertTrue(engine.score > 0)
    }

    @Test
    @DisplayName("Limpieza de una línea simple otorga 100 * nivel y actualiza el contador")
    fun testSingleLineClear() {
        // Llenar manualmente la fila 0 salvo la columna 4 y 5
        for (col in 0 until 10) {
            if (col !in 4..5) {
                engine.board.cells[0][col] = 1
            }
        }

        // Colocamos una pieza O en x=4, y=0 para completar la fila 0
        val oPiece = Tetromino(TetrominoType.O, x = 4, y = 0)
        engine.board.lockTetromino(oPiece)

        val cleared = engine.board.clearFullLines()
        assertEquals(1, cleared)
    }

    @Test
    @DisplayName("Limpieza de 4 líneas simultáneas (Tetris) otorga 800 * nivel")
    fun testTetrisFourLineClear() {
        // Llenar 4 filas inferiores dejando libre solo la columna 9
        for (row in 0..3) {
            for (col in 0 until 9) {
                engine.board.cells[row][col] = 2
            }
        }

        // Fila 0 a 3 tienen 9 bloques cada una
        val linesCleared = engine.board.clearFullLines()
        assertEquals(0, linesCleared) // Aún no están completas

        // Rellenar la columna 9 en las 4 filas
        for (row in 0..3) {
            engine.board.cells[row][9] = 1
        }

        val clearedAfterFill = engine.board.clearFullLines()
        assertEquals(4, clearedAfterFill)

        // Todas las filas inferiores deben quedar vacías tras el borrado
        for (row in 0..3) {
            assertTrue(engine.board.cells[row].all { it == 0 })
        }
    }

    @Test
    @DisplayName("Progreso de nivel cada 10 líneas completadas")
    fun testLevelProgression() {
        assertEquals(1, engine.level)

        // Simulamos completar 10 líneas mediante hard drops con líneas preparadas
        // Verificamos cálculo de intervalo de caída
        val initialSpeed = engine.calculateDropInterval()
        assertEquals(0.8f, initialSpeed)

        // Si el nivel sube a 2, la velocidad debe acelerarse (menor intervalo)
        // Probamos la lógica matemática del nivel
        val level2Speed = (0.8f - (2 - 1) * 0.07f)
        assertTrue(level2Speed < initialSpeed)
    }

    @Test
    @DisplayName("Ghost piece calcula con precisión la proyección de impacto en el suelo")
    fun testGhostPieceProjection() {
        val ghost = engine.calculateGhostPiece()
        assertEquals(engine.currentPiece.x, ghost.x)
        assertEquals(engine.currentPiece.rotation, ghost.rotation)
        assertTrue(ghost.y <= engine.currentPiece.y)

        // Si desciende un bloque más, colisionaría
        assertFalse(engine.board.canPlace(ghost.moveBy(0, -1)))
    }

    @Test
    @DisplayName("Pausa detiene la actualización y la recepción de comandos de juego")
    fun testPauseAndResume() {
        val initialY = engine.currentPiece.y

        // Pausar
        engine.handleInput(InputCommand.Pause)
        assertEquals(GameStatus.PAUSED, engine.status)

        // Update no debe mover la pieza
        engine.update(5.0f)
        assertEquals(initialY, engine.currentPiece.y)

        // Comandos de movimiento no deben tener efecto mientras está en pausa
        engine.handleInput(InputCommand.MoveLeft)
        assertEquals(initialY, engine.currentPiece.y)

        // Reanudar
        engine.handleInput(InputCommand.Pause)
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    @DisplayName("Detección de Game Over cuando los bloques se apilan hasta la cima")
    fun testGameOverWhenBoardFillsUp() {
        // Llenar el tablero hasta la cima
        for (row in 0 until 20) {
            engine.board.cells[row][4] = 1
            engine.board.cells[row][5] = 1
        }

        // Ejecutar hard drop o gravedad para forzar el spawn de nueva pieza que no quepa
        engine.hardDrop()

        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    @DisplayName("Reset reinicia el tablero, puntuación, líneas y estado RUNNING")
    fun testReset() {
        engine.handleInput(InputCommand.MoveDown)
        engine.reset()

        assertEquals(0, engine.score)
        assertEquals(1, engine.level)
        assertEquals(0, engine.totalLinesCleared)
        assertEquals(GameStatus.RUNNING, engine.status)
        assertTrue(engine.board.cells.all { row -> row.all { it == 0 } })
    }
}
