package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.ControlMode
import com.retroarcade.core.GameId
import com.retroarcade.core.GameStatus
import com.retroarcade.core.ScoreEntry
import com.retroarcade.domain.puzzlebobble.BubbleColor
import com.retroarcade.domain.puzzlebobble.PuzzleBobbleEngine
import com.retroarcade.domain.puzzlebobble.TrajectoryCalc
import kotlin.math.atan2

class PuzzleBobbleScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val engine = PuzzleBobbleEngine()
    private val audio = ChiptuneAudio(game.settingsRepository)
    private val touchPoint = Vector3()

    private var playerName = "JUGADOR"
    private var isHighScoreSaved = false
    private var blinkTimer = 0f

    // Botones de Virtual Pad (estilo Space Invaders: disparar izquierda, apuntar derecha)
    private val btnFire = ArcadeButton(30f, 75f, 250f, 100f, "DISPARAR", Color.GREEN) {
        if (engine.shoot()) audio.playMove()
    }
    private val btnAimLeft = ArcadeButton(360f, 75f, 160f, 100f, "<<", Color.CYAN) {
        engine.rotateCannon(-5f)
    }
    private val btnAimRight = ArcadeButton(535f, 75f, 155f, 100f, ">>", Color.CYAN) {
        engine.rotateCannon(5f)
    }

    // Botón de Pausa HUD
    private val btnPause = ArcadeButton(610f, 1180f, 80f, 60f, "||", Color.YELLOW) {
        engine.togglePause()
    }

    // Botones de Overlay Pausa
    private val btnResume = ArcadeButton(210f, 680f, 300f, 75f, "CONTINUAR", Color.GREEN) {
        engine.togglePause()
    }
    private val btnRestart = ArcadeButton(210f, 580f, 300f, 75f, "REINICIAR", Color.YELLOW) {
        engine.reset()
        isHighScoreSaved = false
    }
    private val btnQuit = ArcadeButton(210f, 480f, 300f, 75f, "SALIR AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    // Botones de Overlay Game Over
    private lateinit var btnEditName: ArcadeButton
    private lateinit var btnSaveScore: ArcadeButton
    private val btnRetry = ArcadeButton(140f, 320f, 440f, 75f, "REINTENTAR", Color.YELLOW) {
        engine.reset()
        isHighScoreSaved = false
    }
    private val btnGameOverMenu = ArcadeButton(140f, 220f, 440f, 75f, "SALIR AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    private var pressedButton: ArcadeButton? = null
    private var playedGameOverSound = false
    private var playedLevelClearSound = false

    override fun show() {
        updateGameOverButtons()

        Gdx.input.inputProcessor = object : InputAdapter() {
            private var isDraggingAim = false

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                pressedButton = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                if (pressedButton != null) return true

                if (engine.status == GameStatus.RUNNING && game.settingsRepository.getControlMode() == ControlMode.TOUCH_GESTURES) {
                    if (touchPoint.y > 280f) {
                        isDraggingAim = true
                        updateAimFromTouch(touchPoint.x, touchPoint.y)
                        return true
                    }
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                if (isDraggingAim && engine.status == GameStatus.RUNNING) {
                    updateAimFromTouch(touchPoint.x, touchPoint.y)
                    return true
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                if (isDraggingAim && engine.status == GameStatus.RUNNING) {
                    isDraggingAim = false
                    updateAimFromTouch(touchPoint.x, touchPoint.y)
                    if (engine.shoot()) audio.playMove()
                    return true
                }

                pressedButton?.let { btn ->
                    if (btn.contains(touchPoint.x, touchPoint.y)) {
                        btn.onClick()
                    }
                }
                pressedButton = null
                return true
            }
        }
    }

    private fun updateAimFromTouch(tx: Float, ty: Float) {
        val dx = tx - engine.cannonX
        val dy = ty - engine.cannonY
        if (dy > 20f) {
            val angleRad = atan2(dx, dy)
            val angleDeg = angleRad * 180f / Math.PI.toFloat()
            engine.setAim(angleDeg)
        }
    }

    private fun updateGameOverButtons() {
        btnEditName = ArcadeButton(140f, 540f, 440f, 75f, "NOMBRE: $playerName", Color.CYAN) {
            promptPlayerName()
        }
        btnSaveScore = ArcadeButton(140f, 440f, 440f, 75f, "GUARDAR RECORD", Color.GREEN) {
            saveHighScoreAndExit()
        }
    }

    private fun promptPlayerName() {
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String) {
                val clean = text.trim().uppercase().take(8)
                if (clean.isNotEmpty()) {
                    playerName = clean
                    updateGameOverButtons()
                }
            }
            override fun canceled() {}
        }, "ESCRIBE TU NOMBRE", playerName, "Maximo 8 caracteres")
    }

    private fun getActiveButtons(): List<ArcadeButton> {
        val list = mutableListOf<ArcadeButton>()
        when (engine.status) {
            GameStatus.RUNNING -> {
                list.add(btnPause)
                if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
                    list.addAll(listOf(btnAimLeft, btnAimRight, btnFire))
                }
            }
            GameStatus.PAUSED -> {
                list.addAll(listOf(btnResume, btnRestart, btnQuit))
            }
            GameStatus.GAME_OVER -> {
                val isHighScore = game.scoreRepository.isHighScore(GameId.PUZZLE_BOBBLE, engine.score)
                if (isHighScore && !isHighScoreSaved && engine.score > 0) {
                    list.add(btnEditName)
                    list.add(btnSaveScore)
                }
                list.addAll(listOf(btnRetry, btnGameOverMenu))
            }
        }
        return list
    }

    private fun saveHighScoreAndExit() {
        if (!isHighScoreSaved && engine.score > 0) {
            val entry = ScoreEntry(
                initials = playerName,
                score = engine.score,
                level = engine.level
            )
            game.scoreRepository.addScore(GameId.PUZZLE_BOBBLE, entry)
            isHighScoreSaved = true
            game.goToHighScores(GameId.PUZZLE_BOBBLE)
        }
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        blinkTimer += delta
        val scoreBefore = engine.score
        engine.update(delta)

        if (engine.score > scoreBefore) {
            audio.playClear()
        }

        // Sonido y control de victoria de nivel
        if (engine.isLevelClearing && !playedLevelClearSound) {
            audio.playClear()
            playedLevelClearSound = true
        } else if (!engine.isLevelClearing) {
            playedLevelClearSound = false
        }

        if (engine.status == GameStatus.GAME_OVER && !playedGameOverSound) {
            audio.playGameOver()
            playedGameOverSound = true
            updateGameOverButtons()
        } else if (engine.status != GameStatus.GAME_OVER) {
            playedGameOverSound = false
        }

        ScreenUtils.clear(0.03f, 0.03f, 0.06f, 1f)
        game.viewport.apply()
        val renderer = game.shapeRenderer
        renderer.projectionMatrix = game.camera.combined

        renderer.begin(ShapeRenderer.ShapeType.Filled)

        // 1. HUD Superior
        drawHUD(renderer)

        // 2. Tablero, Línea Guía y Burbujas
        drawGameBoard(renderer)

        // 3. Cañón y Controles
        drawCannonAndControls(renderer)

        // 4. Cartel de Nivel Superado
        if (engine.isLevelClearing) {
            drawLevelClearBanner(renderer)
        }

        // 5. Overlays de Pausa o Game Over
        when (engine.status) {
            GameStatus.PAUSED -> drawPauseOverlay(renderer)
            GameStatus.GAME_OVER -> drawGameOverOverlay(renderer)
            else -> {}
        }

        renderer.end()
    }

    private fun drawHUD(renderer: ShapeRenderer) {
        // Marco de Score
        ArcadeUIHelper.drawNeonBox(renderer, 30f, 1140f, 260f, 100f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.CYAN, 3f)
        ArcadeFont.drawText(renderer, "SCORE", 50f, 1205f, 2.2f, Color.CYAN)
        ArcadeFont.drawText(renderer, engine.score.toString().padStart(6, '0'), 50f, 1160f, 3.2f, Color.WHITE)

        // Indicador de Nivel y Disparos de Techo
        ArcadeUIHelper.drawNeonBox(renderer, 300f, 1140f, 190f, 100f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.MAGENTA, 3f)
        ArcadeFont.drawText(renderer, "NIVEL ${engine.level}", 315f, 1205f, 2.2f, Color.YELLOW)

        val ceilingStatusText = if (engine.currentCeilingY > engine.targetCeilingY) {
            "BAJANDO!"
        } else {
            "TECHO: ${engine.shotsFiredUntilCeilingDrop}"
        }
        val ceilingStatusColor = if (engine.shotsFiredUntilCeilingDrop <= 2) Color.RED else Color.LIGHT_GRAY
        ArcadeFont.drawText(renderer, ceilingStatusText, 315f, 1160f, 2.2f, ceilingStatusColor)

        // Botón Pausa
        ArcadeUIHelper.drawButton(renderer, btnPause, btnPause == pressedButton, 2.8f)

        // Récord actual
        val highScore = game.scoreRepository.getScores(GameId.PUZZLE_BOBBLE).firstOrNull()?.score ?: 0
        ArcadeFont.drawText(renderer, "RECORD: ${highScore.toString().padStart(6, '0')}", 35f, 1095f, 2.3f, Color.YELLOW)
    }

    private fun drawGameBoard(renderer: ShapeRenderer) {
        val boardW = engine.boardRight - engine.boardLeft
        val boardH = engine.initialCeilingY - 260f

        // Marco del área de juego
        ArcadeUIHelper.drawNeonBox(renderer, engine.boardLeft - 6f, 260f, boardW + 12f, boardH, Color(0.02f, 0.03f, 0.07f, 1f), Color.CYAN, 4f)

        // Línea roja inferior de peligro (Deadline)
        renderer.color = Color(0.85f, 0.1f, 0.1f, 0.8f)
        var dashX = engine.boardLeft
        while (dashX < engine.boardRight) {
            renderer.rect(dashX, engine.bottomDeadlineY, 12f, 3f)
            dashX += 20f
        }

        // Techo de púas neón con posición gradual suave
        val ceilingY = engine.currentCeilingY
        val isWarning = (engine.shotsFiredUntilCeilingDrop <= 2 || engine.currentCeilingY > engine.targetCeilingY)
        val spikeColor = if (isWarning && (blinkTimer % 0.4f > 0.2f)) Color.YELLOW else Color.RED

        renderer.color = spikeColor
        renderer.rect(engine.boardLeft, ceilingY, boardW, 6f)
        var spikeX = engine.boardLeft + 12f
        while (spikeX < engine.boardRight) {
            renderer.triangle(
                spikeX - 8f, ceilingY,
                spikeX + 8f, ceilingY,
                spikeX, ceilingY - 12f
            )
            spikeX += 24f
        }

        // Línea guía de trayectoria (Aim Guide Line)
        if (engine.status == GameStatus.RUNNING && engine.flyingBubble == null && !engine.isLevelClearing) {
            val guidePoints = TrajectoryCalc.calculateAimGuide(
                startX = engine.cannonX,
                startY = engine.cannonY,
                angleDegrees = engine.aimAngle,
                leftWall = engine.boardLeft + engine.bubbleRadius,
                rightWall = engine.boardRight - engine.bubbleRadius,
                topCeiling = ceilingY - engine.bubbleRadius,
                stepDistance = 28f,
                maxPoints = 25
            )
            renderer.color = Color(1f, 1f, 1f, 0.45f)
            for (p in guidePoints) {
                renderer.circle(p.first, p.second, 4f)
            }
        }

        // Burbujas asentadas en la rejilla hexagonal (todas las filas visibles)
        for (r in 0 until engine.grid.maxRows) {
            val cols = engine.grid.getColsInRow(r)
            for (c in 0 until cols) {
                val color = engine.grid.getBubble(r, c)
                if (color != BubbleColor.NONE) {
                    val (bx, by) = engine.getCellCenter(r, c)
                    drawBubble(renderer, bx, by, engine.bubbleRadius, color)
                }
            }
        }

        // Burbuja en vuelo
        val flying = engine.flyingBubble
        if (flying != null) {
            drawBubble(renderer, flying.x, flying.y, engine.bubbleRadius, flying.color)
        }
    }

    private fun drawBubble(renderer: ShapeRenderer, cx: Float, cy: Float, radius: Float, color: BubbleColor) {
        val gColor = getBubbleColor(color)
        renderer.color = gColor
        renderer.circle(cx, cy, radius)

        renderer.color = gColor.cpy().mul(0.7f)
        renderer.circle(cx, cy, radius * 0.75f)

        renderer.color = Color.WHITE
        renderer.circle(cx - radius * 0.3f, cy + radius * 0.3f, radius * 0.25f)
    }

    private fun drawCannonAndControls(renderer: ShapeRenderer) {
        // Base del cañón
        renderer.color = Color(0.12f, 0.16f, 0.28f, 1f)
        renderer.circle(engine.cannonX, engine.cannonY, 40f)

        // Puntero / Flecha del cañón
        val (dirX, dirY) = TrajectoryCalc.calculateDirection(engine.aimAngle)
        val pointerLen = 65f
        val px = engine.cannonX + dirX * pointerLen
        val py = engine.cannonY + dirY * pointerLen

        renderer.color = Color.YELLOW
        renderer.rectLine(engine.cannonX, engine.cannonY, px, py, 6f)

        // Burbuja actual cargada en el cañón
        if (engine.flyingBubble == null && !engine.isLevelClearing) {
            drawBubble(renderer, engine.cannonX, engine.cannonY, engine.bubbleRadius, engine.currentBubble)
        }

        // Siguiente burbuja (NEXT) a la izquierda de la base
        ArcadeFont.drawText(renderer, "NEXT", engine.cannonX - 140f, engine.cannonY + 15f, 1.8f, Color.GRAY)
        drawBubble(renderer, engine.cannonX - 90f, engine.cannonY, engine.bubbleRadius * 0.85f, engine.nextBubble)

        // Controles según modo
        val isPad = (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD)
        if (isPad) {
            ArcadeUIHelper.drawButton(renderer, btnAimLeft, btnAimLeft == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnAimRight, btnAimRight == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnFire, btnFire == pressedButton, 2.8f)
        } else {
            ArcadeFont.drawCenteredText(renderer, "ARRASTRA PARA APUNTAR * SUELTA PARA DISPARAR", 360f, 140f, 2.0f, Color.LIGHT_GRAY)
        }
    }

    private fun drawLevelClearBanner(renderer: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(renderer, 100f, 560f, 520f, 200f, Color(0.05f, 0.08f, 0.18f, 0.98f), Color.GREEN, 4f)
        ArcadeFont.drawCenteredText(renderer, "NIVEL COMPLETADO", 360f, 680f, 4.0f, Color.GREEN)
        ArcadeFont.drawCenteredText(renderer, "BONUS +${1000 * engine.level} PUNTOS", 360f, 625f, 2.8f, Color.YELLOW)
        ArcadeFont.drawCenteredText(renderer, "PREPARANDO SIGUIENTE NIVEL...", 360f, 585f, 1.9f, Color.LIGHT_GRAY)
    }

    private fun drawPauseOverlay(renderer: ShapeRenderer) {
        renderer.color = Color(0f, 0f, 0f, 0.75f)
        renderer.rect(0f, 0f, 720f, 1280f)

        ArcadeUIHelper.drawNeonBox(renderer, 150f, 430f, 420f, 420f, Color(0.06f, 0.08f, 0.18f, 0.98f), Color.YELLOW, 4f)
        ArcadeFont.drawCenteredText(renderer, "PAUSA", 360f, 790f, 4.5f, Color.YELLOW)

        ArcadeUIHelper.drawButton(renderer, btnResume, btnResume == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(renderer, btnRestart, btnRestart == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(renderer, btnQuit, btnQuit == pressedButton, 2.8f)
    }

    private fun drawGameOverOverlay(renderer: ShapeRenderer) {
        renderer.color = Color(0f, 0f, 0f, 0.85f)
        renderer.rect(0f, 0f, 720f, 1280f)

        val isHighScore = game.scoreRepository.isHighScore(GameId.PUZZLE_BOBBLE, engine.score) && engine.score > 0
        val boxH = if (isHighScore && !isHighScoreSaved) 660f else 460f
        val boxY = if (isHighScore && !isHighScoreSaved) 190f else 350f

        ArcadeUIHelper.drawNeonBox(renderer, 100f, boxY, 520f, boxH, Color(0.06f, 0.08f, 0.18f, 0.98f), Color.RED, 4f)
        ArcadeFont.drawCenteredText(renderer, "FIN DE PARTIDA", 360f, boxY + boxH - 60f, 4.0f, Color.RED)

        val scoreStr = "PUNTOS: ${engine.score.toString().padStart(6, '0')}"
        ArcadeFont.drawCenteredText(renderer, scoreStr, 360f, boxY + boxH - 120f, 2.8f, Color.YELLOW)

        val detailsStr = "NIVEL: ${engine.level}"
        ArcadeFont.drawCenteredText(renderer, detailsStr, 360f, boxY + boxH - 165f, 2.4f, Color.WHITE)

        if (isHighScore && !isHighScoreSaved) {
            ArcadeFont.drawCenteredText(renderer, "- NUEVO RECORD TOP 10 -", 360f, boxY + boxH - 220f, 2.4f, Color.GREEN)
            ArcadeFont.drawCenteredText(renderer, "TOCA PARA EDITAR TU NOMBRE:", 360f, boxY + boxH - 260f, 1.9f, Color.LIGHT_GRAY)

            ArcadeUIHelper.drawButton(renderer, btnEditName, btnEditName == pressedButton, 2.8f)
            ArcadeUIHelper.drawButton(renderer, btnSaveScore, btnSaveScore == pressedButton, 2.8f)
        }

        ArcadeUIHelper.drawButton(renderer, btnRetry, btnRetry == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(renderer, btnGameOverMenu, btnGameOverMenu == pressedButton, 2.8f)
    }

    private fun getBubbleColor(color: BubbleColor): Color {
        return when (color) {
            BubbleColor.CYAN -> Color.CYAN
            BubbleColor.YELLOW -> Color.YELLOW
            BubbleColor.MAGENTA -> Color.MAGENTA
            BubbleColor.GREEN -> Color.GREEN
            BubbleColor.RED -> Color.RED
            BubbleColor.BLUE -> Color.ROYAL
            BubbleColor.NONE -> Color.BLACK
        }
    }

    override fun dispose() {
        audio.dispose()
    }
}
