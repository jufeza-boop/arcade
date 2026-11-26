package com.retroarcade.domain.arkanoid

import com.retroarcade.core.EngineState
import com.retroarcade.core.GameEngine
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// ──────────────────────────────────────────────
// Modelos de datos (Kotlin puro – sin GDX/Android)
// ──────────────────────────────────────────────

data class Brick(
    val x: Float,       // esquina izquierda
    val y: Float,       // esquina inferior
    val width: Float,   // 72f
    val height: Float,  // 28f
    var hp: Int,        // 1, 2 o 3
    val maxHp: Int      // para calcular el color de degradación
)

enum class PowerUpType { WIDE_PADDLE, MULTIBALL, LASER }

data class PowerUp(
    val type: PowerUpType,
    val x: Float,
    var y: Float
)

// ──────────────────────────────────────────────
// Motor de Arkanoid – Kotlin puro
// ──────────────────────────────────────────────

class ArkanoidEngine(
    private val random: kotlin.random.Random = kotlin.random.Random.Default
) : GameEngine {

    // ── Constantes de tablero ──
    companion object {
        const val BOARD_WIDTH  = 720f
        const val BOARD_HEIGHT = 1280f

        const val PADDLE_Y          = 140f
        const val PADDLE_HEIGHT     = 20f
        const val PADDLE_WIDTH_NORMAL = 140f
        const val PADDLE_WIDTH_WIDE   = 220f

        const val BALL_RADIUS       = 12f
        const val BALL_SPEED_BASE   = 320f
        const val BALL_SPEED_INC    = 40f   // por nivel

        const val BRICK_WIDTH       = 72f
        const val BRICK_HEIGHT      = 28f
        const val BRICK_COLS        = 8
        const val BRICK_ROWS        = 5
        const val BRICK_AREA_TOP    = 1100f
        const val BRICK_AREA_BOTTOM = 850f   // Y base del bloque inferior

        const val POWERUP_FALL_SPEED = 180f
        const val POWERUP_CHANCE     = 0.25f

        const val WALL_LEFT   = 10f
        const val WALL_RIGHT  = 710f
        const val CEILING_Y   = 1240f

        const val MAX_BOUNCE_ANGLE_DEG = 70f
    }

    // ── Estado GameEngine ──
    override val gameId: GameId = GameId.ARKANOID

    override var state: EngineState = EngineState(score = 0, level = 1, lives = 3, status = GameStatus.RUNNING)
        private set

    // ── Pala ──
    var paddleX: Float = BOARD_WIDTH / 2f
        internal set
    val paddleY: Float = PADDLE_Y
    var paddleWidth: Float = PADDLE_WIDTH_NORMAL
        internal set
    val paddleHeight: Float = PADDLE_HEIGHT

    // ── Bola ──
    var ballX: Float = BOARD_WIDTH / 2f
        internal set
    var ballY: Float = PADDLE_Y + PADDLE_HEIGHT + BALL_RADIUS
        internal set
    val ballRadius: Float = BALL_RADIUS

    // Velocidad de la bola
    internal var ballVelX: Float = 0f
    internal var ballVelY: Float = 0f

    // La bola empieza posada sobre la pala hasta el primer movimiento
    private var ballLaunched: Boolean = false

    // ── Ladrillos y power-ups ──
    private val _bricks: MutableList<Brick> = mutableListOf()
    val bricks: List<Brick> get() = _bricks

    private val _powerUps: MutableList<PowerUp> = mutableListOf()
    val powerUps: List<PowerUp> get() = _powerUps

    // ── Alias de estado (delegados a state) ──
    override val score:  Int        get() = state.score
    override val level:  Int        get() = state.level
    override val lives:  Int        get() = state.lives
    override val status: GameStatus get() = state.status

    init {
        spawnBricks()
        launchBallOnPaddle()
    }

    // ──────────────────────────────────────────────
    // Inicialización / Reset
    // ──────────────────────────────────────────────

    private fun launchBallOnPaddle() {
        ballX = paddleX
        ballY = paddleY + paddleHeight + ballRadius
        ballVelX = 0f
        ballVelY = 0f
        ballLaunched = false
    }

    private fun currentBallSpeed(): Float = BALL_SPEED_BASE + (level - 1) * BALL_SPEED_INC

    /** Lanza la bola hacia arriba ligeramente sesgada. */
    private fun doLaunch() {
        val speed = currentBallSpeed()
        ballVelX = 0f
        ballVelY = speed
        ballLaunched = true
    }

    private fun spawnBricks() {
        _bricks.clear()
        // 8 columnas en 720 px: margen lateral = (720 - 8*72) / 2 = (720-576)/2 = 72 → offset 36 para centrar
        val marginLeft = (BOARD_WIDTH - BRICK_COLS * BRICK_WIDTH) / 2f
        val rowSpacing = (BRICK_AREA_TOP - BRICK_AREA_BOTTOM) / BRICK_ROWS.toFloat()

        for (row in 0 until BRICK_ROWS) {
            // Más HP en filas superiores
            val hp = when {
                row < 2 -> 3
                row < 4 -> 2
                else    -> 1
            }
            val brickY = BRICK_AREA_BOTTOM + row * rowSpacing
            for (col in 0 until BRICK_COLS) {
                val brickX = marginLeft + col * BRICK_WIDTH
                _bricks.add(Brick(x = brickX, y = brickY, width = BRICK_WIDTH, height = BRICK_HEIGHT, hp = hp, maxHp = hp))
            }
        }
    }

    override fun reset() {
        state = EngineState(score = 0, level = 1, lives = 3, status = GameStatus.RUNNING)
        paddleX = BOARD_WIDTH / 2f
        paddleWidth = PADDLE_WIDTH_NORMAL
        _powerUps.clear()
        spawnBricks()
        launchBallOnPaddle()
    }

    // ── Propiedades de conveniencia para el renderer ──

    /** True cuando la bola ya fue lanzada (no está posada sobre la pala). */
    val ballInPlay: Boolean get() = ballLaunched

    /** Alias público para lanzar la bola desde el renderer/botón. */
    fun launchBall() {
        if (!ballLaunched && status == GameStatus.RUNNING) doLaunch()
    }

    // ──────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────

    override fun handleInput(command: InputCommand) {
        when (command) {
            is InputCommand.Pause -> togglePause()
            else -> {
                if (status != GameStatus.RUNNING) return
                when (command) {
                    is InputCommand.MoveLeft  -> { movePaddleBy(-12f); tryLaunch() }
                    is InputCommand.MoveRight -> { movePaddleBy( 12f); tryLaunch() }
                    is InputCommand.DragPosition -> {
                        movePaddle(command.x)
                        tryLaunch()
                    }
                    is InputCommand.Tap -> tryLaunch()
                    else -> {}
                }
            }
        }
    }

    private fun tryLaunch() {
        if (!ballLaunched) doLaunch()
    }

    fun togglePause() {
        state = when (status) {
            GameStatus.RUNNING -> state.copy(status = GameStatus.PAUSED)
            GameStatus.PAUSED  -> state.copy(status = GameStatus.RUNNING)
            else               -> state
        }
    }

    // ── Movimiento de pala ──

    fun movePaddle(targetX: Float) {
        val half = paddleWidth / 2f
        paddleX = targetX.coerceIn(half, BOARD_WIDTH - half)
        if (!ballLaunched) {
            ballX = paddleX
        }
    }

    fun movePaddleBy(dx: Float) {
        movePaddle(paddleX + dx)
    }

    // ──────────────────────────────────────────────
    // Bucle de juego
    // ──────────────────────────────────────────────

    override fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING) return
        if (!ballLaunched) return

        moveBall(deltaSeconds)
        updatePowerUps(deltaSeconds)
        checkBallLost()
    }

    private fun moveBall(dt: Float) {
        ballX += ballVelX * dt
        ballY += ballVelY * dt

        // Rebote en paredes laterales
        if (ballX - ballRadius < WALL_LEFT) {
            ballX = WALL_LEFT + ballRadius
            ballVelX = abs(ballVelX)
        } else if (ballX + ballRadius > WALL_RIGHT) {
            ballX = WALL_RIGHT - ballRadius
            ballVelX = -abs(ballVelX)
        }

        // Rebote en techo
        if (ballY + ballRadius > CEILING_Y) {
            ballY = CEILING_Y - ballRadius
            ballVelY = -abs(ballVelY)
        }

        // Colisión con ladrillos
        checkBallBrickCollisions()

        // Colisión con pala (solo si la bola va hacia abajo)
        if (ballVelY < 0) {
            checkBallPaddleCollision()
        }
    }

    private fun checkBallBrickCollisions() {
        val iter = _bricks.iterator()
        while (iter.hasNext()) {
            val brick = iter.next()
            if (ballOverlapsBrick(brick)) {
                brick.hp--
                // Rebote simple AABB: invertir velY
                ballVelY = -ballVelY

                if (brick.hp <= 0) {
                    iter.remove()
                    val points = 10 * brick.maxHp * level
                    state = state.copy(score = state.score + points)
                    maybeSpawnPowerUp(brick)
                }
                break // una colisión por frame
            }
        }

        // ¿Todos los ladrillos destruidos?
        if (_bricks.isEmpty()) {
            nextLevel()
        }
    }

    private fun ballOverlapsBrick(brick: Brick): Boolean {
        // AABB con radio de bola
        val closestX = ballX.coerceIn(brick.x, brick.x + brick.width)
        val closestY = ballY.coerceIn(brick.y, brick.y + brick.height)
        val dx = ballX - closestX
        val dy = ballY - closestY
        return dx * dx + dy * dy <= ballRadius * ballRadius
    }

    private fun checkBallPaddleCollision() {
        val paddleLeft  = paddleX - paddleWidth / 2f
        val paddleRight = paddleX + paddleWidth / 2f
        val paddleTop   = paddleY + paddleHeight

        if (ballY - ballRadius <= paddleTop &&
            ballY + ballRadius >= paddleY &&
            ballX >= paddleLeft &&
            ballX <= paddleRight
        ) {
            // Calcular ángulo según punto de impacto
            val offset = (ballX - paddleX) / (paddleWidth / 2f)   // [-1, 1]
            val angleDeg = offset * MAX_BOUNCE_ANGLE_DEG
            val angleRad = angleDeg * (PI / 180.0)
            val speed = currentBallSpeed()

            ballVelX = (speed * sin(angleRad)).toFloat()
            ballVelY =  abs((speed * cos(angleRad)).toFloat())  // siempre hacia arriba

            // Evitar que la bola quede dentro de la pala
            ballY = paddleTop + ballRadius
        }
    }

    private fun checkBallLost() {
        if (ballY < 0f) {
            val newLives = lives - 1
            if (newLives <= 0) {
                state = state.copy(lives = 0, status = GameStatus.GAME_OVER)
            } else {
                state = state.copy(lives = newLives)
                paddleWidth = PADDLE_WIDTH_NORMAL
                _powerUps.clear()
                launchBallOnPaddle()
            }
        }
    }

    private fun nextLevel() {
        val newLevel = level + 1
        state = state.copy(level = newLevel)
        paddleWidth = PADDLE_WIDTH_NORMAL
        _powerUps.clear()
        spawnBricks()
        launchBallOnPaddle()
    }

    // ──────────────────────────────────────────────
    // Power-ups
    // ──────────────────────────────────────────────

    private fun maybeSpawnPowerUp(brick: Brick) {
        if (random.nextFloat() < POWERUP_CHANCE) {
            val type = PowerUpType.entries.random(random)
            _powerUps.add(
                PowerUp(
                    type = type,
                    x    = brick.x + brick.width / 2f,
                    y    = brick.y
                )
            )
        }
    }

    private fun updatePowerUps(dt: Float) {
        val iter = _powerUps.iterator()
        while (iter.hasNext()) {
            val pu = iter.next()
            pu.y -= POWERUP_FALL_SPEED * dt

            // ¿Power-up recogido por la pala?
            val paddleLeft  = paddleX - paddleWidth / 2f
            val paddleRight = paddleX + paddleWidth / 2f
            if (pu.y <= paddleY + paddleHeight && pu.y >= paddleY &&
                pu.x >= paddleLeft && pu.x <= paddleRight
            ) {
                applyPowerUp(pu.type)
                iter.remove()
            } else if (pu.y < 0f) {
                iter.remove()
            }
        }
    }

    private fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.WIDE_PADDLE -> paddleWidth = PADDLE_WIDTH_WIDE
            PowerUpType.MULTIBALL   -> { /* marcado como colectado – sin implementar multiball completo */ }
            PowerUpType.LASER       -> { /* marcado como colectado – sin implementar laser completo */ }
        }
    }

    // ──────────────────────────────────────────────
    // Acceso interno para tests
    // ──────────────────────────────────────────────

    /** Permite a los tests inyectar el estado de los ladrillos directamente. */
    internal fun setBricksForTest(newBricks: List<Brick>) {
        _bricks.clear()
        _bricks.addAll(newBricks)
    }

    /** Lanza la bola con una velocidad predefinida (para tests). */
    internal fun launchWithVelocity(vx: Float, vy: Float) {
        ballLaunched = true
        ballVelX = vx
        ballVelY = vy
    }

    /** Coloca la bola en una posición concreta (para tests). */
    internal fun setBallPosition(x: Float, y: Float) {
        ballX = x
        ballY = y
    }
}
