package com.retroarcade.domain.snake

import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SnakeEngineTest {

    private lateinit var engine: SnakeEngine

    @BeforeEach
    fun setUp() {
        engine = SnakeEngine(wrapWalls = false, random = Random(42))
    }

    @Test
    fun snakeMovesInCurrentDirectionOnTick() {
        assertEquals(GameId.SNAKE, engine.gameId)
        assertEquals(listOf(GridPoint(10, 14), GridPoint(9, 14), GridPoint(8, 14)), engine.snake)
        assertEquals(Direction.RIGHT, engine.direction)

        // Avanzar exactamente un tick
        val tickDuration = 1f / engine.ticksPerSecond + 0.001f
        engine.update(tickDuration)

        assertEquals(listOf(GridPoint(11, 14), GridPoint(10, 14), GridPoint(9, 14)), engine.snake)
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun opposite180DegreeTurnIsIgnored() {
        // RIGHT -> LEFT ignorado
        engine.setDirection(Direction.LEFT)
        engine.stepOnceForTest()
        assertEquals(Direction.RIGHT, engine.direction)

        // UP -> DOWN ignorado
        engine.setSnakeForTest(
            listOf(GridPoint(10, 14), GridPoint(10, 13), GridPoint(10, 12)),
            Direction.UP
        )
        engine.setDirection(Direction.DOWN)
        engine.stepOnceForTest()
        assertEquals(Direction.UP, engine.direction)

        // LEFT -> RIGHT ignorado
        engine.setSnakeForTest(
            listOf(GridPoint(10, 14), GridPoint(11, 14), GridPoint(12, 14)),
            Direction.LEFT
        )
        engine.setDirection(Direction.RIGHT)
        engine.stepOnceForTest()
        assertEquals(Direction.LEFT, engine.direction)

        // DOWN -> UP ignorado
        engine.setSnakeForTest(
            listOf(GridPoint(10, 14), GridPoint(10, 15), GridPoint(10, 16)),
            Direction.DOWN
        )
        engine.setDirection(Direction.UP)
        engine.stepOnceForTest()
        assertEquals(Direction.DOWN, engine.direction)
    }

    @Test
    fun rapidDoubleTurnWithinSameTickCannotReverseIntoNeck() {
        // Yendo hacia RIGHT, cabeza en (10,14) y cuello en (9,14)
        engine.setFoodForTest(GridPoint(0, 0))
        engine.handleInput(InputCommand.MoveUp)
        engine.handleInput(InputCommand.MoveLeft)

        engine.stepOnceForTest()

        // No debe revertir a LEFT ni chocar con el cuello; debe haber girado a UP
        assertEquals(Direction.UP, engine.direction)
        assertEquals(GridPoint(10, 15), engine.snake.first())
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun snakeGrowsAndScoreIncreasesWhenEatingFood() {
        val initialSize = engine.snake.size
        engine.setFoodForTest(GridPoint(11, 14))

        engine.stepOnceForTest()

        assertEquals(initialSize + 1, engine.snake.size)
        assertEquals(GridPoint(11, 14), engine.snake.first())
        assertEquals(10, engine.score)
        assertNotEquals(GridPoint(11, 14), engine.food)
    }

    @Test
    fun gameOverOnWallCollision() {
        engine.setSnakeForTest(
            listOf(GridPoint(19, 14), GridPoint(18, 14), GridPoint(17, 14)),
            Direction.RIGHT
        )
        engine.setFoodForTest(GridPoint(0, 0))

        engine.stepOnceForTest()

        assertEquals(GameStatus.GAME_OVER, engine.status)
        assertEquals(0, engine.lives)
    }

    @Test
    fun gameOverOnSelfCollision() {
        // Serpiente en U: cabeza en (10,14) mirando UP, segmento en (10,15)
        engine.setSnakeForTest(
            listOf(
                GridPoint(10, 14),
                GridPoint(11, 14),
                GridPoint(11, 15),
                GridPoint(10, 15),
                GridPoint(9, 15)
            ),
            Direction.UP
        )
        engine.setFoodForTest(GridPoint(0, 0))

        engine.stepOnceForTest()

        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    fun wrapWallsTeleportsSnakeWhenEnabled() {
        val wrapEngine = SnakeEngine(wrapWalls = true, random = Random(42))
        wrapEngine.setFoodForTest(GridPoint(5, 5))

        // Borde derecho -> izquierdo
        wrapEngine.setSnakeForTest(
            listOf(GridPoint(19, 14), GridPoint(18, 14), GridPoint(17, 14)),
            Direction.RIGHT
        )
        wrapEngine.stepOnceForTest()
        assertEquals(GameStatus.RUNNING, wrapEngine.status)
        assertEquals(GridPoint(0, 14), wrapEngine.snake.first())

        // Borde superior -> inferior
        wrapEngine.setSnakeForTest(
            listOf(GridPoint(10, 27), GridPoint(10, 26), GridPoint(10, 25)),
            Direction.UP
        )
        wrapEngine.stepOnceForTest()
        assertEquals(GameStatus.RUNNING, wrapEngine.status)
        assertEquals(GridPoint(10, 0), wrapEngine.snake.first())
    }

    @Test
    fun speedIncreasesWhenEatingFood() {
        val initialSpeed = engine.ticksPerSecond
        assertEquals(6.5f, initialSpeed)

        engine.setFoodForTest(GridPoint(11, 14))
        engine.stepOnceForTest()

        assertEquals(initialSpeed + 0.25f, engine.ticksPerSecond)
        assertTrue(engine.ticksPerSecond <= SnakeEngine.MAX_TICKS_PER_SECOND)
    }

    @Test
    fun bonusFoodSpawnsAndAwardsExtraPoints() {
        // Comer 5 comidas normales para provocar la aparición de bonusFood
        for (i in 1..5) {
            val head = engine.snake.first()
            val next = GridPoint(head.x + engine.direction.dx, head.y + engine.direction.dy)
            // Girar si se acerca al borde derecho
            if (next.x >= engine.cols - 1 && engine.direction == Direction.RIGHT) {
                engine.setDirection(Direction.UP)
            }
            val target = GridPoint(
                engine.snake.first().x + if (engine.direction == Direction.RIGHT) 1 else 0,
                engine.snake.first().y + if (engine.direction == Direction.UP) 1 else 0
            )
            engine.setFoodForTest(target)
            engine.stepOnceForTest()
        }

        val spawnedBonus = engine.bonusFood
        assertNotNull(spawnedBonus, "Debe aparecer bonusFood tras 5 comidas normales")
        assertEquals(SnakeEngine.BONUS_FOOD_DURATION, spawnedBonus!!.timer)

        // Colocar la serpiente justo antes de la comida bonus y comerla
        val scoreBeforeBonus = engine.score
        val bonusPos = GridPoint(5, 5)
        engine.setFoodForTest(GridPoint(0, 0))
        engine.setBonusFoodForTest(BonusFood(pos = bonusPos, timer = 5f))
        engine.setSnakeForTest(
            listOf(GridPoint(4, 5), GridPoint(3, 5), GridPoint(2, 5)),
            Direction.RIGHT
        )

        engine.stepOnceForTest()

        assertNull(engine.bonusFood)
        assertEquals(scoreBeforeBonus + SnakeEngine.BONUS_FOOD_POINTS * engine.level, engine.score)
    }

    @Test
    fun resetRestoresInitialState() {
        engine.setFoodForTest(GridPoint(11, 14))
        engine.stepOnceForTest()
        engine.setBonusFoodForTest(BonusFood(GridPoint(3, 3), 4f))

        engine.reset()

        assertEquals(0, engine.score)
        assertEquals(1, engine.level)
        assertEquals(1, engine.lives)
        assertEquals(GameStatus.RUNNING, engine.status)
        assertEquals(Direction.RIGHT, engine.direction)
        assertEquals(SnakeEngine.INITIAL_TICKS_PER_SECOND, engine.ticksPerSecond)
        assertEquals(listOf(GridPoint(10, 14), GridPoint(9, 14), GridPoint(8, 14)), engine.snake)
        assertNull(engine.bonusFood)
    }
}
