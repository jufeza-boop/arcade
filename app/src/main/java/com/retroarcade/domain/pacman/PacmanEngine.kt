package com.retroarcade.domain.pacman

import com.retroarcade.core.EngineState
import com.retroarcade.core.GameEngine
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import com.retroarcade.core.SwipeDirection
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

enum class TileType {
    WALL,
    EMPTY,
    DOT,
    POWER_PELLET,
    GHOST_HOUSE,
    TUNNEL
}

enum class PacDirection(val dx: Int, val dy: Int) {
    UP(0, 1),
    DOWN(0, -1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0);

    fun opposite(): PacDirection = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
        NONE -> NONE
    }
}

enum class GhostPersonality {
    BLINKY,
    PINKY,
    INKY,
    CLYDE
}

enum class GhostMode {
    CHASE,
    FRIGHTENED,
    EATEN
}

data class Ghost(
    val id: Int,
    val personality: GhostPersonality,
    var x: Float,
    var y: Float,
    var dir: PacDirection,
    var mode: GhostMode = GhostMode.CHASE
)

/**
 * Motor de dominio puro para Pac-Man (sin dependencias de LibGDX ni Android).
 * Coordenadas continuas en unidades de celda: x en [0, cols), y en [0, rows).
 * El centro de la celda (col, row) está en (col + 0.5f, row + 0.5f).
 * Fila 0 abajo, fila rows - 1 arriba.
 */
class PacmanEngine(
    private val random: Random = Random.Default
) : GameEngine {

    companion object {
        const val COLS = 19
        const val ROWS = 21

        private const val TURN_TOLERANCE = 0.12f
        private const val FRIGHTENED_DURATION = 7f
        private const val COLLISION_RADIUS = 0.55f

        private const val BASE_PACMAN_SPEED = 5.5f
        private const val BASE_GHOST_SPEED = 4.6f
        private const val SPEED_INCREMENT_PER_LEVEL = 0.35f

        private const val PACMAN_SPAWN_X = 9.5f
        private const val PACMAN_SPAWN_Y = 5.5f

        private const val GHOST_HOUSE_EXIT_X = 9.5f
        private const val GHOST_HOUSE_EXIT_Y = 13.5f
        private const val GHOST_HOUSE_CENTER_X = 9.5f
        private const val GHOST_HOUSE_CENTER_Y = 11.5f

        // Fila 20 arriba -> fila 0 abajo
        private val MAZE_LAYOUT = arrayOf(
            "###################", // 20
            "#........#........#", // 19
            "#o##.###.#.###.##o#", // 18
            "#.................#", // 17
            "#.##.#.#####.#.##.#", // 16
            "#....#...#...#....#", // 15
            "####.### # ###.####", // 14
            "####.#       #.####", // 13
            "####.# ## ## #.####", // 12
            "T   .  #GGG#  .   T", // 11 (túnel lateral)
            "####.# ##### #.####", // 10
            "####.#       #.####", // 9
            "####.# ##### #.####", // 8
            "#........#........#", // 7
            "#.##.###.#.###.##.#", // 6
            "#o.#..... .....#.o#", // 5 (centro x=9,y=5 spawn)
            "##.#.#.#####.#.#.##", // 4
            "#....#...#...#....#", // 3
            "#.######.#.######.#", // 2
            "#.................#", // 1
            "###################"  // 0
        )
    }

    override val gameId: GameId = GameId.PACMAN

    val cols: Int = COLS
    val rows: Int = ROWS

    override var state: EngineState = EngineState(
        score = 0,
        level = 1,
        lives = 3,
        status = GameStatus.RUNNING
    )
        private set

    private val grid: Array<Array<TileType>> = Array(ROWS) { Array(COLS) { TileType.WALL } }

    var pacmanX: Float = PACMAN_SPAWN_X
        private set
    var pacmanY: Float = PACMAN_SPAWN_Y
        private set
    var pacmanDir: PacDirection = PacDirection.NONE
        private set
    private var nextDir: PacDirection = PacDirection.NONE

    private val _ghosts = mutableListOf<Ghost>()
    val ghosts: List<Ghost> get() = _ghosts

    var frightenedTimer: Float = 0f
        private set
    private var frightenedComboCount: Int = 0

    var dotsRemaining: Int = 0
        private set

    init {
        reset()
    }

    fun getTile(col: Int, row: Int): TileType {
        if (col !in 0 until cols || row !in 0 until rows) return TileType.WALL
        return grid[row][col]
    }

    fun setNextDirection(dir: PacDirection) {
        if (state.status != GameStatus.RUNNING) return
        nextDir = dir
        if (pacmanDir == PacDirection.NONE && dir != PacDirection.NONE) {
            val curCol = kotlin.math.round(pacmanX).toInt().coerceIn(0, cols - 1)
            val curRow = kotlin.math.round(pacmanY).toInt().coerceIn(0, rows - 1)
            if (isWalkableForPacman(curCol + dir.dx, curRow + dir.dy, curRow)) {
                pacmanDir = dir
                nextDir = PacDirection.NONE
            }
        } else if (dir != PacDirection.NONE && dir == pacmanDir.opposite()) {
            pacmanDir = dir
            nextDir = PacDirection.NONE
        }
    }

    fun togglePause() {
        state = when (state.status) {
            GameStatus.RUNNING -> state.copy(status = GameStatus.PAUSED)
            GameStatus.PAUSED -> state.copy(status = GameStatus.RUNNING)
            GameStatus.GAME_OVER -> state
        }
    }

    override fun handleInput(command: InputCommand) {
        when (command) {
            InputCommand.Pause -> togglePause()
            InputCommand.MoveUp -> setNextDirection(PacDirection.UP)
            InputCommand.MoveDown -> setNextDirection(PacDirection.DOWN)
            InputCommand.MoveLeft -> setNextDirection(PacDirection.LEFT)
            InputCommand.MoveRight -> setNextDirection(PacDirection.RIGHT)
            is InputCommand.Swipe -> when (command.direction) {
                SwipeDirection.UP -> setNextDirection(PacDirection.UP)
                SwipeDirection.DOWN -> setNextDirection(PacDirection.DOWN)
                SwipeDirection.LEFT -> setNextDirection(PacDirection.LEFT)
                SwipeDirection.RIGHT -> setNextDirection(PacDirection.RIGHT)
            }
            else -> Unit
        }
    }

    override fun reset() {
        state = EngineState(
            score = 0,
            level = 1,
            lives = 3,
            status = GameStatus.RUNNING
        )
        loadMaze()
        resetPositions()
    }

    override fun update(deltaSeconds: Float) {
        if (state.status != GameStatus.RUNNING || deltaSeconds <= 0f) return

        // Sub-stepping para evitar atravesar centros o paredes con deltas grandes
        var remaining = deltaSeconds
        val maxStep = 0.02f
        while (remaining > 0f && state.status == GameStatus.RUNNING) {
            val dt = minOf(remaining, maxStep)
            stepSimulation(dt)
            remaining -= dt
        }
    }

    private fun stepSimulation(dt: Float) {
        if (frightenedTimer > 0f) {
            frightenedTimer = (frightenedTimer - dt).coerceAtLeast(0f)
            if (frightenedTimer == 0f) {
                frightenedComboCount = 0
                for (ghost in _ghosts) {
                    if (ghost.mode == GhostMode.FRIGHTENED) {
                        ghost.mode = GhostMode.CHASE
                    }
                }
            }
        }

        movePacman(dt)
        consumeCurrentTile()
        if (dotsRemaining == 0) {
            advanceLevel()
            return
        }

        moveGhosts(dt)
        checkGhostCollisions()
    }

    private fun pacmanSpeed(): Float =
        BASE_PACMAN_SPEED + (state.level - 1) * SPEED_INCREMENT_PER_LEVEL

    private fun ghostSpeed(mode: GhostMode): Float {
        val base = BASE_GHOST_SPEED + (state.level - 1) * SPEED_INCREMENT_PER_LEVEL
        return when (mode) {
            GhostMode.CHASE -> base
            GhostMode.FRIGHTENED -> base * 0.55f
            GhostMode.EATEN -> base * 1.8f
        }
    }

    private fun movePacman(dt: Float) {
        val curCol = floor(pacmanX).toInt().coerceIn(0, cols - 1)
        val curRow = floor(pacmanY).toInt().coerceIn(0, rows - 1)
        val centerX = curCol + 0.5f
        val centerY = curRow + 0.5f

        // Evaluar giro pendiente al estar cerca del centro de la celda
        if (nextDir != PacDirection.NONE) {
            if (nextDir == pacmanDir.opposite()) {
                pacmanDir = nextDir
                nextDir = PacDirection.NONE
            } else if (pacmanDir == PacDirection.NONE) {
                if (isWalkableForPacman(curCol + nextDir.dx, curRow + nextDir.dy, curRow)) {
                    pacmanDir = nextDir
                    nextDir = PacDirection.NONE
                }
            } else {
                val nearCenter = abs(pacmanX - centerX) <= TURN_TOLERANCE &&
                    abs(pacmanY - centerY) <= TURN_TOLERANCE
                if (nearCenter && isWalkableForPacman(curCol + nextDir.dx, curRow + nextDir.dy, curRow)) {
                    pacmanX = centerX
                    pacmanY = centerY
                    pacmanDir = nextDir
                    nextDir = PacDirection.NONE
                }
            }
        }

        if (pacmanDir == PacDirection.NONE) return

        val speed = pacmanSpeed()
        val prevX = pacmanX
        val prevY = pacmanY
        pacmanX += pacmanDir.dx * speed * dt
        pacmanY += pacmanDir.dy * speed * dt

        // Si cruzó el centro de la celda en este paso y hay giro pendiente válido, girar justo en el centro
        if (nextDir != PacDirection.NONE && nextDir != pacmanDir) {
            val crossedX = (prevX - centerX) * (pacmanX - centerX) <= 0f
            val crossedY = (prevY - centerY) * (pacmanY - centerY) <= 0f
            if (crossedX && crossedY && isWalkableForPacman(curCol + nextDir.dx, curRow + nextDir.dy, curRow)) {
                pacmanX = centerX
                pacmanY = centerY
                pacmanDir = nextDir
                nextDir = PacDirection.NONE
                return
            }
        }

        // Teletransporte por túneles laterales
        if (pacmanX < 0f) {
            pacmanX += cols.toFloat()
        } else if (pacmanX >= cols.toFloat()) {
            pacmanX -= cols.toFloat()
        }

        // Colisión contra muros
        val newCol = floor(pacmanX).toInt().coerceIn(0, cols - 1)
        val newRow = floor(pacmanY).toInt().coerceIn(0, rows - 1)
        val newCenterX = newCol + 0.5f
        val newCenterY = newRow + 0.5f

        when (pacmanDir) {
            PacDirection.RIGHT -> {
                if (pacmanX > newCenterX && !isWalkableForPacman(newCol + 1, newRow, newRow)) {
                    pacmanX = newCenterX
                }
            }
            PacDirection.LEFT -> {
                if (pacmanX < newCenterX && !isWalkableForPacman(newCol - 1, newRow, newRow)) {
                    pacmanX = newCenterX
                }
            }
            PacDirection.UP -> {
                if (pacmanY > newCenterY && !isWalkableForPacman(newCol, newRow + 1, newRow)) {
                    pacmanY = newCenterY
                }
            }
            PacDirection.DOWN -> {
                if (pacmanY < newCenterY && !isWalkableForPacman(newCol, newRow - 1, newRow)) {
                    pacmanY = newCenterY
                }
            }
            PacDirection.NONE -> Unit
        }
    }

    private fun consumeCurrentTile() {
        val col = floor(pacmanX).toInt().coerceIn(0, cols - 1)
        val row = floor(pacmanY).toInt().coerceIn(0, rows - 1)
        when (grid[row][col]) {
            TileType.DOT -> {
                grid[row][col] = TileType.EMPTY
                dotsRemaining--
                state = state.copy(score = state.score + 10)
            }
            TileType.POWER_PELLET -> {
                grid[row][col] = TileType.EMPTY
                dotsRemaining--
                frightenedTimer = FRIGHTENED_DURATION
                frightenedComboCount = 0
                for (ghost in _ghosts) {
                    if (ghost.mode != GhostMode.EATEN) {
                        ghost.mode = GhostMode.FRIGHTENED
                        ghost.dir = ghost.dir.opposite()
                    }
                }
                state = state.copy(score = state.score + 50)
            }
            else -> Unit
        }
    }

    private fun moveGhosts(dt: Float) {
        for (ghost in _ghosts) {
            val speed = ghostSpeed(ghost.mode)
            val prevX = ghost.x
            val prevY = ghost.y

            val curCol = floor(ghost.x).toInt().coerceIn(0, cols - 1)
            val curRow = floor(ghost.y).toInt().coerceIn(0, rows - 1)
            val centerX = curCol + 0.5f
            val centerY = curRow + 0.5f

            val nextX = prevX + ghost.dir.dx * speed * dt
            val nextY = prevY + ghost.dir.dy * speed * dt

            val crossedCenter = when (ghost.dir) {
                PacDirection.LEFT, PacDirection.RIGHT -> (prevX - centerX) * (nextX - centerX) <= 0f
                PacDirection.UP, PacDirection.DOWN -> (prevY - centerY) * (nextY - centerY) <= 0f
                PacDirection.NONE -> true
            }

            if (crossedCenter) {
                ghost.x = centerX
                ghost.y = centerY
                ghost.dir = chooseGhostDirection(ghost, curCol, curRow)
                val rem = hypot((nextX - prevX).toDouble(), (nextY - prevY).toDouble()).toFloat()
                ghost.x += ghost.dir.dx * rem * 0.5f
                ghost.y += ghost.dir.dy * rem * 0.5f
            } else {
                ghost.x = nextX
                ghost.y = nextY
            }

            // Túnel lateral para fantasmas
            if (ghost.x < 0f) {
                ghost.x += cols.toFloat()
            } else if (ghost.x >= cols.toFloat()) {
                ghost.x -= cols.toFloat()
            }

            // Si un fantasma EATEN llega a la casa, vuelve a CHASE
            if (ghost.mode == GhostMode.EATEN) {
                val distToHouse = hypot(
                    (ghost.x - GHOST_HOUSE_CENTER_X).toDouble(),
                    (ghost.y - GHOST_HOUSE_CENTER_Y).toDouble()
                )
                if (distToHouse < 0.45) {
                    ghost.mode = GhostMode.CHASE
                    ghost.x = GHOST_HOUSE_CENTER_X
                    ghost.y = GHOST_HOUSE_CENTER_Y
                    ghost.dir = PacDirection.UP
                }
            }
        }
    }

    private fun chooseGhostDirection(ghost: Ghost, col: Int, row: Int): PacDirection {
        val inHouse = getTile(col, row) == TileType.GHOST_HOUSE
        val allowHouse = inHouse || ghost.mode == GhostMode.EATEN

        val candidates = listOf(PacDirection.UP, PacDirection.LEFT, PacDirection.DOWN, PacDirection.RIGHT)
            .filter { dir ->
                isWalkableForGhost(col + dir.dx, row + dir.dy, row, allowHouse)
            }

        if (candidates.isEmpty()) return ghost.dir.opposite()

        val nonReverse = candidates.filter { it != ghost.dir.opposite() }
        val choices = nonReverse.ifEmpty { candidates }

        if (ghost.mode == GhostMode.FRIGHTENED && !inHouse) {
            return choices[random.nextInt(choices.size)]
        }

        val (targetX, targetY) = when {
            ghost.mode == GhostMode.EATEN -> GHOST_HOUSE_CENTER_X to GHOST_HOUSE_CENTER_Y
            inHouse -> GHOST_HOUSE_EXIT_X to GHOST_HOUSE_EXIT_Y
            else -> targetForPersonality(ghost)
        }

        return choices.minByOrNull { dir ->
            val nx = col + dir.dx + 0.5f
            val ny = row + dir.dy + 0.5f
            val dx = nx - targetX
            val dy = ny - targetY
            dx * dx + dy * dy
        } ?: choices.first()
    }

    private fun targetForPersonality(ghost: Ghost): Pair<Float, Float> {
        return when (ghost.personality) {
            GhostPersonality.BLINKY -> pacmanX to pacmanY
            GhostPersonality.PINKY -> {
                (pacmanX + 4f * pacmanDir.dx) to (pacmanY + 4f * pacmanDir.dy)
            }
            GhostPersonality.INKY -> {
                val aheadX = pacmanX + 2f * pacmanDir.dx
                val aheadY = pacmanY + 2f * pacmanDir.dy
                val blinky = _ghosts.firstOrNull { it.personality == GhostPersonality.BLINKY }
                if (blinky != null) {
                    (2f * aheadX - blinky.x) to (2f * aheadY - blinky.y)
                } else {
                    aheadX to aheadY
                }
            }
            GhostPersonality.CLYDE -> {
                val dist = hypot((ghost.x - pacmanX).toDouble(), (ghost.y - pacmanY).toDouble())
                if (dist >= 5.0) {
                    pacmanX to pacmanY
                } else {
                    1.5f to 1.5f
                }
            }
        }
    }

    private fun checkGhostCollisions() {
        for (ghost in _ghosts) {
            val dist = hypot((pacmanX - ghost.x).toDouble(), (pacmanY - ghost.y).toDouble())
            if (dist < COLLISION_RADIUS) {
                when (ghost.mode) {
                    GhostMode.FRIGHTENED -> {
                        ghost.mode = GhostMode.EATEN
                        val points = 200 shl frightenedComboCount.coerceAtMost(3)
                        frightenedComboCount++
                        state = state.copy(score = state.score + points)
                    }
                    GhostMode.CHASE -> {
                        val remainingLives = state.lives - 1
                        if (remainingLives <= 0) {
                            state = state.copy(lives = 0, status = GameStatus.GAME_OVER)
                        } else {
                            state = state.copy(lives = remainingLives)
                            resetPositions()
                        }
                        return
                    }
                    GhostMode.EATEN -> Unit
                }
            }
        }
    }

    private fun advanceLevel() {
        state = state.copy(level = state.level + 1)
        loadMaze()
        resetPositions()
    }

    private fun isWalkableForPacman(col: Int, row: Int, fromRow: Int): Boolean {
        if (row !in 0 until rows) return false
        if (col < 0 || col >= cols) {
            return getTile(if (col < 0) 0 else cols - 1, fromRow) == TileType.TUNNEL
        }
        val tile = grid[row][col]
        return tile != TileType.WALL && tile != TileType.GHOST_HOUSE
    }

    private fun isWalkableForGhost(col: Int, row: Int, fromRow: Int, allowHouse: Boolean): Boolean {
        if (row !in 0 until rows) return false
        if (col < 0 || col >= cols) {
            return getTile(if (col < 0) 0 else cols - 1, fromRow) == TileType.TUNNEL
        }
        val tile = grid[row][col]
        if (tile == TileType.WALL) return false
        if (tile == TileType.GHOST_HOUSE && !allowHouse) return false
        return true
    }

    private fun loadMaze() {
        var count = 0
        for (layoutIdx in MAZE_LAYOUT.indices) {
            val row = ROWS - 1 - layoutIdx
            val line = MAZE_LAYOUT[layoutIdx]
            for (col in 0 until COLS) {
                val tile = when (line[col]) {
                    '#' -> TileType.WALL
                    '.' -> {
                        count++
                        TileType.DOT
                    }
                    'o' -> {
                        count++
                        TileType.POWER_PELLET
                    }
                    'G' -> TileType.GHOST_HOUSE
                    'T' -> TileType.TUNNEL
                    else -> TileType.EMPTY
                }
                grid[row][col] = tile
            }
        }
        dotsRemaining = count
    }

    private fun resetPositions() {
        pacmanX = PACMAN_SPAWN_X
        pacmanY = PACMAN_SPAWN_Y
        pacmanDir = PacDirection.NONE
        nextDir = PacDirection.NONE

        frightenedTimer = 0f
        frightenedComboCount = 0

        _ghosts.clear()
        _ghosts.add(Ghost(0, GhostPersonality.BLINKY, GHOST_HOUSE_EXIT_X, GHOST_HOUSE_EXIT_Y, PacDirection.LEFT))
        _ghosts.add(Ghost(1, GhostPersonality.PINKY, 9.5f, 11.5f, PacDirection.UP))
        _ghosts.add(Ghost(2, GhostPersonality.INKY, 8.5f, 11.5f, PacDirection.RIGHT))
        _ghosts.add(Ghost(3, GhostPersonality.CLYDE, 10.5f, 11.5f, PacDirection.LEFT))
    }

    // ── Métodos auxiliares internos para tests ────────────────────────────────

    internal fun setPositionForTest(x: Float, y: Float, dir: PacDirection = pacmanDir) {
        pacmanX = x
        pacmanY = y
        pacmanDir = dir
        nextDir = PacDirection.NONE
    }

    internal fun clearDotsExceptOneForTest(keepCol: Int, keepRow: Int) {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (grid[r][c] == TileType.DOT || grid[r][c] == TileType.POWER_PELLET) {
                    grid[r][c] = TileType.EMPTY
                }
            }
        }
        grid[keepRow][keepCol] = TileType.DOT
        dotsRemaining = 1
    }

    internal fun setTileForTest(col: Int, row: Int, tile: TileType) {
        val old = grid[row][col]
        if (old == TileType.DOT || old == TileType.POWER_PELLET) dotsRemaining--
        grid[row][col] = tile
        if (tile == TileType.DOT || tile == TileType.POWER_PELLET) dotsRemaining++
    }
}
