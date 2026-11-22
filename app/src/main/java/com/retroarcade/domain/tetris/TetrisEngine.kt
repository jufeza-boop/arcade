package com.retroarcade.domain.tetris

import com.retroarcade.core.EngineState
import com.retroarcade.core.GameEngine
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import com.retroarcade.core.SwipeDirection
import kotlin.random.Random

class TetrisEngine(
    private val random: Random = Random.Default
) : GameEngine {

    override val gameId: GameId = GameId.TETRIS

    val board: BoardGrid = BoardGrid()

    var currentPiece: Tetromino = spawnPiece(randomPieceType())
        private set

    var nextPiece: Tetromino = spawnPiece(randomPieceType())
        private set

    override var state: EngineState = EngineState(
        score = 0,
        level = 1,
        lives = 1,
        status = GameStatus.RUNNING
    )
        private set

    var totalLinesCleared: Int = 0
        private set

    var lastLinesClearedInTurn: Int = 0
        private set

    private var dropTimer: Float = 0f
    private val pieceBag = mutableListOf<TetrominoType>()

    init {
        // Inicializar con dos piezas de la bolsa
        currentPiece = spawnPiece(nextFromBag())
        nextPiece = spawnPiece(nextFromBag())
    }

    private fun nextFromBag(): TetrominoType {
        if (pieceBag.isEmpty()) {
            val allTypes = TetrominoType.entries.toMutableList()
            allTypes.shuffle(random)
            pieceBag.addAll(allTypes)
        }
        return pieceBag.removeAt(0)
    }

    private fun randomPieceType(): TetrominoType {
        return TetrominoType.entries.random(random)
    }

    fun spawnPiece(type: TetrominoType): Tetromino {
        // Spawn centrado arriba
        val startX = if (type == TetrominoType.O) 4 else 4
        val startY = if (type == TetrominoType.I) 18 else 18
        return Tetromino(type = type, x = startX, y = startY, rotation = 0)
    }

    override fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING) return

        dropTimer += deltaSeconds
        val dropInterval = calculateDropInterval()

        if (dropTimer >= dropInterval) {
            dropTimer = 0f
            stepGravity()
        }
    }

    private fun stepGravity() {
        val moved = currentPiece.moveBy(0, -1)
        if (board.canPlace(moved)) {
            currentPiece = moved
        } else {
            lockAndAdvance()
        }
    }

    fun calculateDropInterval(): Float {
        // Velocidad progresiva según el nivel: desde 0.8s hasta 0.08s
        return (0.8f - (level - 1) * 0.07f).coerceAtLeast(0.08f)
    }

    override fun handleInput(command: InputCommand) {
        when (command) {
            is InputCommand.Pause -> togglePause()
            else -> {
                if (status != GameStatus.RUNNING) return
                when (command) {
                    is InputCommand.MoveLeft -> tryMove(-1, 0)
                    is InputCommand.MoveRight -> tryMove(1, 0)
                    is InputCommand.MoveDown -> softDrop()
                    is InputCommand.MoveUp, is InputCommand.Tap -> rotateClockwiseWithKick()
                    is InputCommand.ActionA -> rotateClockwiseWithKick()
                    is InputCommand.ActionB -> rotateCounterClockwiseWithKick()
                    is InputCommand.Swipe -> {
                        when (command.direction) {
                            SwipeDirection.LEFT -> tryMove(-1, 0)
                            SwipeDirection.RIGHT -> tryMove(1, 0)
                            SwipeDirection.DOWN -> hardDrop()
                            SwipeDirection.UP -> rotateClockwiseWithKick()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun tryMove(dx: Int, dy: Int): Boolean {
        val candidate = currentPiece.moveBy(dx, dy)
        if (board.canPlace(candidate)) {
            currentPiece = candidate
            return true
        }
        return false
    }

    fun softDrop() {
        if (tryMove(0, -1)) {
            // Puntos por caída manual controlada
            addScore(1)
        } else {
            lockAndAdvance()
        }
    }

    fun hardDrop() {
        var dropDistance = 0
        var candidate = currentPiece
        while (board.canPlace(candidate.moveBy(0, -1))) {
            candidate = candidate.moveBy(0, -1)
            dropDistance++
        }
        currentPiece = candidate
        addScore(dropDistance * 2)
        lockAndAdvance()
    }

    /**
     * Calcula la posición "fantasma" donde caerá la pieza actual.
     */
    fun calculateGhostPiece(): Tetromino {
        var ghost = currentPiece
        while (board.canPlace(ghost.moveBy(0, -1))) {
            ghost = ghost.moveBy(0, -1)
        }
        return ghost
    }

    fun rotateClockwiseWithKick(): Boolean {
        val rotated = currentPiece.rotateClockwise()
        return applyRotationWithKick(rotated)
    }

    fun rotateCounterClockwiseWithKick(): Boolean {
        val rotated = currentPiece.rotateCounterClockwise()
        return applyRotationWithKick(rotated)
    }

    private fun applyRotationWithKick(candidate: Tetromino): Boolean {
        // Intentar posición directa
        if (board.canPlace(candidate)) {
            currentPiece = candidate
            return true
        }

        // Wall-kick offsets básicos: derecha 1, izquierda 1, derecha 2, izquierda 2, arriba 1
        val kicks = listOf(1 to 0, -1 to 0, 2 to 0, -2 to 0, 0 to 1)
        for ((kx, ky) in kicks) {
            val kicked = candidate.moveBy(kx, ky)
            if (board.canPlace(kicked)) {
                currentPiece = kicked
                return true
            }
        }
        return false
    }

    private fun lockAndAdvance() {
        board.lockTetromino(currentPiece)

        // Limpiar líneas completas
        val cleared = board.clearFullLines()
        lastLinesClearedInTurn = cleared
        if (cleared > 0) {
            totalLinesCleared += cleared
            val points = when (cleared) {
                1 -> 100 * level
                2 -> 300 * level
                3 -> 500 * level
                4 -> 800 * level // Tetris!
                else -> 0
            }
            addScore(points)

            // Subir de nivel cada 10 líneas
            val newLevel = (totalLinesCleared / 10) + 1
            if (newLevel != level) {
                state = state.copy(level = newLevel)
            }
        }

        // Spawn de la siguiente pieza
        currentPiece = nextPiece
        nextPiece = spawnPiece(nextFromBag())
        dropTimer = 0f

        // Si la nueva pieza no puede colocarse en el tablero -> Fin de partida
        if (!board.canPlace(currentPiece)) {
            state = state.copy(status = GameStatus.GAME_OVER)
        }
    }

    private fun addScore(points: Int) {
        state = state.copy(score = state.score + points)
    }

    fun togglePause() {
        if (status == GameStatus.RUNNING) {
            state = state.copy(status = GameStatus.PAUSED)
        } else if (status == GameStatus.PAUSED) {
            state = state.copy(status = GameStatus.RUNNING)
        }
    }

    override fun reset() {
        board.clear()
        pieceBag.clear()
        totalLinesCleared = 0
        lastLinesClearedInTurn = 0
        dropTimer = 0f
        currentPiece = spawnPiece(nextFromBag())
        nextPiece = spawnPiece(nextFromBag())
        state = EngineState(
            score = 0,
            level = 1,
            lives = 1,
            status = GameStatus.RUNNING
        )
    }
}
