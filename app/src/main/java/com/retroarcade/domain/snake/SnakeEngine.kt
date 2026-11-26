package com.retroarcade.domain.snake

import com.retroarcade.core.EngineState
import com.retroarcade.core.GameEngine
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import com.retroarcade.core.SwipeDirection
import kotlin.random.Random

// ──────────────────────────────────────────────
// Modelos de datos (Kotlin puro – sin GDX/Android)
// ──────────────────────────────────────────────

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, 1),
    DOWN(0, -1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun isOpposite(other: Direction): Boolean = when (this) {
        UP -> other == DOWN
        DOWN -> other == UP
        LEFT -> other == RIGHT
        RIGHT -> other == LEFT
    }
}

data class GridPoint(val x: Int, val y: Int)

data class BonusFood(val pos: GridPoint, var timer: Float)

// ──────────────────────────────────────────────
// Motor de Snake – Kotlin puro
// ──────────────────────────────────────────────

class SnakeEngine(
    val wrapWalls: Boolean = false,
    private val random: Random = Random.Default
) : GameEngine {

    companion object {
        const val DEFAULT_COLS = 20
        const val DEFAULT_ROWS = 28

        const val INITIAL_TICKS_PER_SECOND = 6.5f
        const val SPEED_INCREMENT_PER_FOOD = 0.25f
        const val MAX_TICKS_PER_SECOND = 16f

        const val NORMAL_FOOD_POINTS = 10
        const val BONUS_FOOD_POINTS = 50
        const val BONUS_FOOD_DURATION = 8f
        const val BONUS_FOOD_INTERVAL = 5
        const val FOODS_PER_LEVEL = 10
    }

    val cols: Int = DEFAULT_COLS
    val rows: Int = DEFAULT_ROWS

    override val gameId: GameId = GameId.SNAKE

    override var state: EngineState = EngineState(
        score = 0,
        level = 1,
        lives = 1,
        status = GameStatus.RUNNING
    )
        private set

    private val _snake: MutableList<GridPoint> = mutableListOf()
    val snake: List<GridPoint> get() = _snake

    var direction: Direction = Direction.RIGHT
        private set
    private var pendingDirection: Direction = Direction.RIGHT

    var food: GridPoint = GridPoint(15, 14)
        private set

    var bonusFood: BonusFood? = null
        private set

    var ticksPerSecond: Float = INITIAL_TICKS_PER_SECOND
        private set

    private var foodsEaten: Int = 0
    private var tickAccumulator: Float = 0f

    init {
        reset()
    }

    override fun reset() {
        _snake.clear()
        _snake.add(GridPoint(10, 14))
        _snake.add(GridPoint(9, 14))
        _snake.add(GridPoint(8, 14))

        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        ticksPerSecond = INITIAL_TICKS_PER_SECOND
        foodsEaten = 0
        tickAccumulator = 0f
        bonusFood = null

        state = EngineState(
            score = 0,
            level = 1,
            lives = 1,
            status = GameStatus.RUNNING
        )

        food = spawnFreeCell(excluded = emptySet()) ?: GridPoint(0, 0)
    }

    fun setDirection(newDir: Direction) {
        if (status != GameStatus.RUNNING) return
        // Validamos contra `direction` (dirección del tick actual) para impedir
        // que dos pulsaciones rápidas en el mismo tick reviertan hacia el cuello.
        if (!newDir.isOpposite(direction)) {
            pendingDirection = newDir
        }
    }

    fun togglePause() {
        state = when (status) {
            GameStatus.RUNNING -> state.copy(status = GameStatus.PAUSED)
            GameStatus.PAUSED -> state.copy(status = GameStatus.RUNNING)
            GameStatus.GAME_OVER -> state
        }
    }

    override fun handleInput(command: InputCommand) {
        when (command) {
            InputCommand.MoveUp,
            InputCommand.Swipe(SwipeDirection.UP) -> setDirection(Direction.UP)

            InputCommand.MoveDown,
            InputCommand.Swipe(SwipeDirection.DOWN) -> setDirection(Direction.DOWN)

            InputCommand.MoveLeft,
            InputCommand.Swipe(SwipeDirection.LEFT) -> setDirection(Direction.LEFT)

            InputCommand.MoveRight,
            InputCommand.Swipe(SwipeDirection.RIGHT) -> setDirection(Direction.RIGHT)

            InputCommand.Pause -> togglePause()

            else -> Unit
        }
    }

    override fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING || deltaSeconds <= 0f) return

        bonusFood?.let { bf ->
            bf.timer -= deltaSeconds
            if (bf.timer <= 0f) {
                bonusFood = null
            }
        }

        tickAccumulator += deltaSeconds
        val tickInterval = 1f / ticksPerSecond
        while (tickAccumulator >= tickInterval && status == GameStatus.RUNNING) {
            tickAccumulator -= tickInterval
            step()
        }
    }

    private fun step() {
        direction = pendingDirection
        val head = _snake.first()
        var nextX = head.x + direction.dx
        var nextY = head.y + direction.dy

        if (wrapWalls) {
            nextX = ((nextX % cols) + cols) % cols
            nextY = ((nextY % rows) + rows) % rows
        } else if (nextX !in 0 until cols || nextY !in 0 until rows) {
            triggerGameOver()
            return
        }

        val nextHead = GridPoint(nextX, nextY)
        val ateFood = (nextHead == food)

        // Si no come comida normal, la cola avanza y deja libre la última celda.
        val bodyToCheck = if (ateFood) _snake else _snake.dropLast(1)
        if (nextHead in bodyToCheck) {
            triggerGameOver()
            return
        }

        _snake.add(0, nextHead)
        if (!ateFood) {
            _snake.removeAt(_snake.lastIndex)
        }

        // Comprobar comida bonus
        bonusFood?.let { bf ->
            if (nextHead == bf.pos) {
                state = state.copy(score = state.score + BONUS_FOOD_POINTS * state.level)
                bonusFood = null
            }
        }

        // Comprobar comida normal
        if (ateFood) {
            foodsEaten += 1
            val newLevel = 1 + (foodsEaten / FOODS_PER_LEVEL)
            val gainedScore = NORMAL_FOOD_POINTS * state.level
            state = state.copy(
                score = state.score + gainedScore,
                level = newLevel
            )
            ticksPerSecond = minOf(
                MAX_TICKS_PER_SECOND,
                ticksPerSecond + SPEED_INCREMENT_PER_FOOD
            )

            val excludedForFood = buildSet {
                bonusFood?.let { add(it.pos) }
            }
            val nextFood = spawnFreeCell(excludedForFood)
            if (nextFood != null) {
                food = nextFood
            } else {
                // Tablero completamente lleno
                triggerGameOver()
                return
            }

            if (foodsEaten % BONUS_FOOD_INTERVAL == 0) {
                val bonusPos = spawnFreeCell(setOf(food))
                if (bonusPos != null) {
                    bonusFood = BonusFood(pos = bonusPos, timer = BONUS_FOOD_DURATION)
                }
            }
        }
    }

    private fun triggerGameOver() {
        state = state.copy(lives = 0, status = GameStatus.GAME_OVER)
    }

    private fun spawnFreeCell(excluded: Set<GridPoint>): GridPoint? {
        val occupied = HashSet<GridPoint>(_snake.size + excluded.size)
        occupied.addAll(_snake)
        occupied.addAll(excluded)

        val freeCells = ArrayList<GridPoint>(cols * rows - occupied.size)
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val pt = GridPoint(x, y)
                if (pt !in occupied) {
                    freeCells.add(pt)
                }
            }
        }
        if (freeCells.isEmpty()) return null
        return freeCells[random.nextInt(freeCells.size)]
    }

    // ──────────────────────────────────────────────
    // Helpers internos para tests unitarios
    // ──────────────────────────────────────────────

    internal fun setSnakeForTest(segments: List<GridPoint>, dir: Direction = direction) {
        require(segments.isNotEmpty()) { "La serpiente debe tener al menos 1 segmento" }
        _snake.clear()
        _snake.addAll(segments)
        direction = dir
        pendingDirection = dir
    }

    internal fun setFoodForTest(pos: GridPoint) {
        food = pos
    }

    internal fun setBonusFoodForTest(bonus: BonusFood?) {
        bonusFood = bonus
    }

    internal fun stepOnceForTest() {
        if (status == GameStatus.RUNNING) {
            step()
        }
    }
}
