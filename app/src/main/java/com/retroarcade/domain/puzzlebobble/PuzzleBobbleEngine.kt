package com.retroarcade.domain.puzzlebobble

import com.retroarcade.core.EngineState
import com.retroarcade.core.GameEngine
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

data class FlyingBubble(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: BubbleColor
)

class PuzzleBobbleEngine(
    private val random: Random = Random.Default,
    val boardLeft: Float = 168f,
    val boardRight: Float = 552f,
    val initialCeilingY: Float = 1080f,
    val bottomDeadlineY: Float = 440f,
    val bubbleRadius: Float = 24f
) : GameEngine {

    override val gameId: GameId = GameId.PUZZLE_BOBBLE

    val grid: HexGrid = HexGrid()
    val bubbleDiameter: Float = bubbleRadius * 2f
    val rowHeight: Float = bubbleRadius * sqrt(3f)

    val cannonX: Float = (boardLeft + boardRight) / 2f
    val cannonY: Float = 300f
    val bubbleSpeed: Float = 950f

    var aimAngle: Float = 0f
        private set

    var currentBubble: BubbleColor = randomBubbleColor()
        private set

    var nextBubble: BubbleColor = randomBubbleColor()
        private set

    var flyingBubble: FlyingBubble? = null
        private set

    var currentCeilingY: Float = initialCeilingY
        private set

    var targetCeilingY: Float = initialCeilingY
        private set

    var ceilingDropsCount: Int = 0
        private set

    var shotsFiredUntilCeilingDrop: Int = SHOTS_PER_CEILING_DROP
        private set

    // Estado de transición al completar un nivel
    var isLevelClearing: Boolean = false
        private set

    var levelClearTimer: Float = 0f
        private set

    var lastClusterClearedSize: Int = 0
        private set

    var lastOrphansClearedSize: Int = 0
        private set

    override var state: EngineState = EngineState(
        score = 0,
        level = 1,
        lives = 3,
        status = GameStatus.RUNNING
    )
        private set

    companion object {
        const val SHOTS_PER_CEILING_DROP = 6
        const val LEVEL_CLEAR_DELAY = 1.5f
    }

    init {
        setupLevel(1)
    }

    fun setupLevel(lvl: Int) {
        grid.clear()
        shotsFiredUntilCeilingDrop = SHOTS_PER_CEILING_DROP
        flyingBubble = null
        isLevelClearing = false
        levelClearTimer = 0f
        ceilingDropsCount = 0
        currentCeilingY = initialCeilingY
        targetCeilingY = initialCeilingY

        // Número de filas iniciales y colores según el nivel
        val rowsToSpawn = (3 + (lvl % 3)).coerceAtMost(6)
        val numColors = (3 + (lvl % 4)).coerceAtMost(BubbleColor.PLAYABLE_COLORS.size)
        val availableColors = BubbleColor.PLAYABLE_COLORS.take(numColors)

        for (r in 0 until rowsToSpawn) {
            val cols = grid.getColsInRow(r)
            for (c in 0 until cols) {
                val color = availableColors[(c / 2 + r + lvl) % availableColors.size]
                grid.setBubble(r, c, color)
            }
        }
        currentBubble = randomBubbleFromGrid()
        nextBubble = randomBubbleFromGrid()
    }

    private fun randomBubbleColor(): BubbleColor {
        return BubbleColor.PLAYABLE_COLORS.random(random)
    }

    fun randomBubbleFromGrid(): BubbleColor {
        val existingColors = mutableSetOf<BubbleColor>()
        for (r in 0 until grid.maxRows) {
            for (c in 0 until grid.getColsInRow(r)) {
                val b = grid.getBubble(r, c)
                if (b != BubbleColor.NONE) existingColors.add(b)
            }
        }
        return if (existingColors.isNotEmpty()) {
            existingColors.random(random)
        } else {
            randomBubbleColor()
        }
    }

    override fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING) return

        // 1. Si estamos en la transición de victoria de nivel
        if (isLevelClearing) {
            levelClearTimer += deltaSeconds
            if (levelClearTimer >= LEVEL_CLEAR_DELAY) {
                isLevelClearing = false
                levelClearTimer = 0f
                val nextLvl = level + 1
                state = state.copy(level = nextLvl)
                setupLevel(nextLvl)
            }
            return
        }

        // 2. Descenso gradual y suave del techo
        if (currentCeilingY > targetCeilingY) {
            currentCeilingY -= deltaSeconds * 80f // Baja suavemente en píxeles
            if (currentCeilingY < targetCeilingY) {
                currentCeilingY = targetCeilingY
            }
            checkGameOverCondition()
        }

        // 3. Actualizar burbuja voladora
        val bubble = flyingBubble
        if (bubble != null) {
            updateFlyingBubble(bubble, deltaSeconds)
        }
    }

    private fun updateFlyingBubble(bubble: FlyingBubble, dt: Float) {
        bubble.x += bubble.vx * dt
        bubble.y += bubble.vy * dt

        // Rebote en pared izquierda
        if (bubble.x - bubbleRadius <= boardLeft) {
            bubble.x = boardLeft + bubbleRadius
            bubble.vx = -bubble.vx
        }
        // Rebote en pared derecha
        else if (bubble.x + bubbleRadius >= boardRight) {
            bubble.x = boardRight - bubbleRadius
            bubble.vx = -bubble.vx
        }

        // Colisión con techo
        if (bubble.y + bubbleRadius >= currentCeilingY) {
            attachBubbleToGrid(bubble)
            return
        }

        // Colisión con otras burbujas en el tablero
        for (r in 0 until grid.maxRows) {
            val cols = grid.getColsInRow(r)
            for (c in 0 until cols) {
                if (grid.getBubble(r, c) != BubbleColor.NONE) {
                    val (bx, by) = getCellCenter(r, c)
                    val dist = hypot(bubble.x - bx, bubble.y - by)
                    if (dist <= bubbleDiameter * 0.95f) {
                        attachBubbleToGrid(bubble)
                        return
                    }
                }
            }
        }
    }

    private fun attachBubbleToGrid(bubble: FlyingBubble) {
        var bestRow = -1
        var bestCol = -1
        var bestDist = Float.MAX_VALUE

        for (r in 0 until grid.maxRows) {
            val cols = grid.getColsInRow(r)
            for (c in 0 until cols) {
                if (grid.getBubble(r, c) == BubbleColor.NONE) {
                    val (cx, cy) = getCellCenter(r, c)
                    val dist = hypot(bubble.x - cx, bubble.y - cy)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestRow = r
                        bestCol = c
                    }
                }
            }
        }

        flyingBubble = null

        if (bestRow != -1 && bestCol != -1) {
            grid.setBubble(bestRow, bestCol, bubble.color)
            processMatchesAndOrphans(bestRow, bestCol)
        }

        // Comprobar victoria de nivel inmediatamente tras la eliminación
        if (grid.isEmpty()) {
            isLevelClearing = true
            levelClearTimer = 0f
            state = state.copy(score = state.score + 1000 * level)
            return
        }

        // Preparar siguiente burbuja
        currentBubble = nextBubble
        nextBubble = randomBubbleFromGrid()

        // Contar disparos para descenso del techo
        shotsFiredUntilCeilingDrop--
        if (shotsFiredUntilCeilingDrop <= 0) {
            shotsFiredUntilCeilingDrop = SHOTS_PER_CEILING_DROP
            triggerCeilingDrop()
        }

        // Comprobar fin de partida (burbuja que cruza el deadline inferior)
        checkGameOverCondition()
    }

    fun triggerCeilingDrop() {
        ceilingDropsCount++
        targetCeilingY = initialCeilingY - ceilingDropsCount * rowHeight
    }

    private fun processMatchesAndOrphans(row: Int, col: Int) {
        val cluster = grid.findConnectedCluster(row, col)
        lastClusterClearedSize = 0
        lastOrphansClearedSize = 0

        if (cluster.size >= 3) {
            lastClusterClearedSize = cluster.size
            for ((cr, cc) in cluster) {
                grid.setBubble(cr, cc, BubbleColor.NONE)
            }
            state = state.copy(score = state.score + cluster.size * 20 * level)

            // Buscar y eliminar burbujas huérfanas
            val orphans = grid.findOrphanBubbles()
            if (orphans.isNotEmpty()) {
                lastOrphansClearedSize = orphans.size
                for ((or, oc) in orphans) {
                    grid.setBubble(or, oc, BubbleColor.NONE)
                }
                state = state.copy(score = state.score + orphans.size * 50 * level)
            }
        }
    }

    private fun checkGameOverCondition() {
        for (r in 0 until grid.maxRows) {
            val cols = grid.getColsInRow(r)
            for (c in 0 until cols) {
                if (grid.getBubble(r, c) != BubbleColor.NONE) {
                    val (_, cy) = getCellCenter(r, c)
                    if (cy - bubbleRadius <= bottomDeadlineY) {
                        state = state.copy(status = GameStatus.GAME_OVER)
                        return
                    }
                }
            }
        }
    }

    fun getCellCenter(row: Int, col: Int): Pair<Float, Float> {
        val isEven = (row % 2 == 0)
        val startX = if (isEven) boardLeft + bubbleRadius else boardLeft + bubbleRadius * 2f
        val x = startX + col * bubbleDiameter
        val y = currentCeilingY - row * rowHeight - bubbleRadius
        return x to y
    }

    override fun handleInput(command: InputCommand) {
        when (command) {
            is InputCommand.Pause -> togglePause()
            else -> {
                if (status != GameStatus.RUNNING || isLevelClearing) return
                when (command) {
                    is InputCommand.MoveLeft -> rotateCannon(-3f)
                    is InputCommand.MoveRight -> rotateCannon(3f)
                    is InputCommand.AimAngle -> setAim(command.degrees)
                    is InputCommand.ActionA, is InputCommand.Tap, is InputCommand.Release -> shoot()
                    else -> {}
                }
            }
        }
    }

    fun rotateCannon(deltaDegrees: Float) {
        aimAngle = TrajectoryCalc.clampAngle(aimAngle + deltaDegrees)
    }

    fun setAim(degrees: Float) {
        aimAngle = TrajectoryCalc.clampAngle(degrees)
    }

    fun shoot(): Boolean {
        if (flyingBubble != null || isLevelClearing) return false
        val (dirX, dirY) = TrajectoryCalc.calculateDirection(aimAngle)
        flyingBubble = FlyingBubble(
            x = cannonX,
            y = cannonY,
            vx = dirX * bubbleSpeed,
            vy = dirY * bubbleSpeed,
            color = currentBubble
        )
        return true
    }

    fun togglePause() {
        if (status == GameStatus.RUNNING) {
            state = state.copy(status = GameStatus.PAUSED)
        } else if (status == GameStatus.PAUSED) {
            state = state.copy(status = GameStatus.RUNNING)
        }
    }

    override fun reset() {
        state = EngineState(
            score = 0,
            level = 1,
            lives = 3,
            status = GameStatus.RUNNING
        )
        setupLevel(1)
        aimAngle = 0f
    }
}
