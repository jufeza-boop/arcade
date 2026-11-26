package com.retroarcade.domain.arkanoid

import com.retroarcade.core.GameStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ArkanoidEngineTest {

    private lateinit var engine: ArkanoidEngine

    @BeforeEach
    fun setUp() {
        engine = ArkanoidEngine(kotlin.random.Random(42))
    }

    // ──────────────────────────────────────────────
    // 1. Rebotes en paredes
    // ──────────────────────────────────────────────

    @Test
    fun ballBouncesOffLeftWall() {
        // Colocar bola cerca del borde izquierdo, moviéndose a la izquierda
        engine.setBallPosition(ArkanoidEngine.WALL_LEFT + ArkanoidEngine.BALL_RADIUS - 1f, 600f)
        engine.launchWithVelocity(-200f, 100f)

        engine.update(0.016f)

        // Después del rebote, velX debe ser positivo
        assertTrue(engine.ballVelX > 0f, "Tras rebotar en pared izquierda velX debe ser positivo, pero fue ${engine.ballVelX}")
    }

    @Test
    fun ballBouncesOffRightWall() {
        // Colocar bola cerca del borde derecho, moviéndose a la derecha
        engine.setBallPosition(ArkanoidEngine.WALL_RIGHT - ArkanoidEngine.BALL_RADIUS + 1f, 600f)
        engine.launchWithVelocity(200f, 100f)

        engine.update(0.016f)

        // Después del rebote, velX debe ser negativo
        assertTrue(engine.ballVelX < 0f, "Tras rebotar en pared derecha velX debe ser negativo, pero fue ${engine.ballVelX}")
    }

    @Test
    fun ballBouncesOffTopWall() {
        // Colocar bola cerca del techo, moviéndose hacia arriba
        engine.setBallPosition(360f, ArkanoidEngine.CEILING_Y - ArkanoidEngine.BALL_RADIUS + 1f)
        engine.launchWithVelocity(0f, 300f)

        engine.update(0.016f)

        // Después del rebote, velY debe ser negativo (hacia abajo)
        assertTrue(engine.ballVelY < 0f, "Tras rebotar en techo velY debe ser negativo, pero fue ${engine.ballVelY}")
    }

    // ──────────────────────────────────────────────
    // 2. Colisión bola-ladrillo
    // ──────────────────────────────────────────────

    @Test
    fun brickHpDecreasesOnBallHit() {
        val brick = Brick(x = 350f, y = 600f, width = 72f, height = 28f, hp = 2, maxHp = 2)
        engine.setBricksForTest(listOf(brick))

        // Colocar la bola justo encima del ladrillo moviéndose hacia abajo
        engine.setBallPosition(brick.x + brick.width / 2f, brick.y + brick.height + ArkanoidEngine.BALL_RADIUS - 1f)
        engine.launchWithVelocity(0f, -200f)

        engine.update(0.016f)

        // El ladrillo debería tener hp=1
        assertEquals(1, engine.bricks.firstOrNull()?.hp,
            "El HP del ladrillo debería haberse reducido de 2 a 1")
    }

    @Test
    fun brickRemovedWhenHpReachesZero() {
        val brick = Brick(x = 350f, y = 600f, width = 72f, height = 28f, hp = 1, maxHp = 1)
        engine.setBricksForTest(listOf(brick))

        engine.setBallPosition(brick.x + brick.width / 2f, brick.y + brick.height + ArkanoidEngine.BALL_RADIUS - 1f)
        engine.launchWithVelocity(0f, -200f)

        engine.update(0.016f)

        // El ladrillo con hp=1 debe haber desaparecido (o hay nivel nuevo con ladrillos reiniciados)
        // Verificamos que no existe ese ladrillo concreto en la lista
        val stillPresent = engine.bricks.any { it.x == brick.x && it.y == brick.y && it.maxHp == 1 }
        assertFalse(stillPresent, "El ladrillo con hp=1 debería haberse eliminado tras ser golpeado")
    }

    @Test
    fun scoreIncreasesWhenBrickDestroyed() {
        val brick = Brick(x = 350f, y = 600f, width = 72f, height = 28f, hp = 1, maxHp = 1)
        engine.setBricksForTest(listOf(brick))
        val initialScore = engine.score

        engine.setBallPosition(brick.x + brick.width / 2f, brick.y + brick.height + ArkanoidEngine.BALL_RADIUS - 1f)
        engine.launchWithVelocity(0f, -200f)

        engine.update(0.016f)

        // Puede que se haya disparado nextLevel (todos destruidos), pero score debe haber subido
        assertTrue(engine.score > initialScore, "El score debería haber aumentado tras destruir un ladrillo")
    }

    // ──────────────────────────────────────────────
    // 3. Pérdida de vida y Game Over
    // ──────────────────────────────────────────────

    @Test
    fun lifeLostWhenBallFallsOffBottom() {
        val initialLives = engine.lives
        // Colocar la bola bajo el borde inferior
        engine.setBallPosition(360f, -50f)
        engine.launchWithVelocity(0f, -100f)

        engine.update(0.016f)

        assertTrue(engine.lives < initialLives, "Debería perderse una vida cuando la bola cae por el fondo")
    }

    @Test
    fun gameOverWhenNoLivesLeft() {
        // Reducir vidas a 1 manualmente estableciendo el estado
        // Usamos reflexión a través de launchWithVelocity para forzar la situación
        // Primero dejamos vidas = 1 iterando pérdidas de bola
        repeat(engine.lives - 1) {
            engine.setBallPosition(360f, -50f)
            engine.launchWithVelocity(0f, -100f)
            engine.update(0.016f)
        }
        // Ahora debería quedar 1 vida
        assertEquals(1, engine.lives)

        // Perder la última vida
        engine.setBallPosition(360f, -50f)
        engine.launchWithVelocity(0f, -100f)
        engine.update(0.016f)

        assertEquals(GameStatus.GAME_OVER, engine.status,
            "El estado debe ser GAME_OVER cuando se agotan las vidas")
    }

    // ──────────────────────────────────────────────
    // 4. Movimiento de pala
    // ──────────────────────────────────────────────

    @Test
    fun paddleMoveClampedToBoard() {
        // Intentar mover la pala fuera del borde izquierdo
        engine.movePaddle(-9999f)
        assertTrue(engine.paddleX >= engine.paddleWidth / 2f,
            "La pala no debería salirse por la izquierda")

        // Intentar mover la pala fuera del borde derecho
        engine.movePaddle(9999f)
        assertTrue(engine.paddleX <= ArkanoidEngine.BOARD_WIDTH - engine.paddleWidth / 2f,
            "La pala no debería salirse por la derecha")
    }

    // ──────────────────────────────────────────────
    // 5. Siguiente nivel
    // ──────────────────────────────────────────────

    @Test
    fun nextLevelWhenAllBricksDestroyed() {
        val initialLevel = engine.level

        // Destruir todos los ladrillos colocando la bola en cada uno
        // Estrategia: vaciar la lista directamente y forzar una actualización
        // Para eso usamos setBricksForTest con un único ladrillo de hp=1
        val brick = Brick(x = 350f, y = 600f, width = 72f, height = 28f, hp = 1, maxHp = 1)
        engine.setBricksForTest(listOf(brick))

        engine.setBallPosition(brick.x + brick.width / 2f, brick.y + brick.height + ArkanoidEngine.BALL_RADIUS - 1f)
        engine.launchWithVelocity(0f, -200f)

        engine.update(0.016f)

        assertEquals(initialLevel + 1, engine.level,
            "El nivel debería haber aumentado tras destruir todos los ladrillos")
    }

    // ──────────────────────────────────────────────
    // 6. Ángulo de rebote en la pala
    // ──────────────────────────────────────────────

    @Test
    fun paddleBounceBallAngleCenter() {
        // Impacto en el centro de la pala: velX ≈ 0, velY > 0
        val paddleCenter = engine.paddleX
        engine.setBallPosition(paddleCenter, engine.paddleY + engine.paddleHeight + engine.ballRadius - 1f)
        engine.launchWithVelocity(0f, -ArkanoidEngine.BALL_SPEED_BASE)

        engine.update(0.016f)

        // velX debe ser cercano a 0 (impacto central)
        val speed = ArkanoidEngine.BALL_SPEED_BASE
        assertTrue(abs(engine.ballVelX) < speed * 0.2f,
            "Impacto central: |velX|=${abs(engine.ballVelX)} debería ser < 20% de la velocidad")
        assertTrue(engine.ballVelY > 0f,
            "Tras rebote en pala la bola debe ir hacia arriba (velY > 0)")
    }

    @Test
    fun paddleBounceBallAngleEdge() {
        // Impacto en el borde derecho de la pala: |velX| debe ser significativo
        val paddleRightEdge = engine.paddleX + engine.paddleWidth / 2f - 1f
        engine.setBallPosition(paddleRightEdge, engine.paddleY + engine.paddleHeight + engine.ballRadius - 1f)
        engine.launchWithVelocity(0f, -ArkanoidEngine.BALL_SPEED_BASE)

        engine.update(0.016f)

        // En el extremo, el ángulo es ~70° → velX debería ser grande
        assertTrue(abs(engine.ballVelX) > abs(engine.ballVelY) * 0.5f,
            "Impacto en extremo: |velX|=${abs(engine.ballVelX)} debería ser > 50% de |velY|=${abs(engine.ballVelY)}")
        assertTrue(engine.ballVelY > 0f,
            "Tras rebote en pala la bola debe ir hacia arriba (velY > 0)")
    }
}
