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
import com.retroarcade.domain.snake.Direction
import com.retroarcade.domain.snake.SnakeEngine

class SnakeScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val engine = SnakeEngine()
    private val audio = ChiptuneAudio(game.settingsRepository)
    private val touchPoint = Vector3()

    private val cellSize = 26f
    private val boardW = engine.cols * cellSize // 520f
    private val boardH = engine.rows * cellSize // 728f
    private val boardX = (720f - boardW) / 2f  // 100f
    private val boardY = 350f

    private var playerName = "JUGADOR"
    private var isHighScoreSaved = false
    private var blinkTimer = 0f

    // Cruceta Virtual Pad (botones amplios estilo arcade)
    private val btnUp = ArcadeButton(302f, 215f, 116f, 92f, "↑", Color.CYAN) {
        engine.setDirection(Direction.UP)
    }
    private val btnDown = ArcadeButton(302f, 25f, 116f, 92f, "↓", Color.CYAN) {
        engine.setDirection(Direction.DOWN)
    }
    private val btnLeft = ArcadeButton(180f, 120f, 116f, 92f, "<", Color.CYAN) {
        engine.setDirection(Direction.LEFT)
    }
    private val btnRight = ArcadeButton(424f, 120f, 116f, 92f, ">", Color.CYAN) {
        engine.setDirection(Direction.RIGHT)
    }

    // Pausa y Overlays
    private val btnPause = ArcadeButton(610f, 1200f, 80f, 60f, "||", Color.YELLOW) {
        engine.togglePause()
    }
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

    override fun show() {
        updateGameOverButtons()
        Gdx.input.inputProcessor = object : InputAdapter() {
            private var touchDownX = 0f
            private var touchDownY = 0f

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                touchDownX = touchPoint.x
                touchDownY = touchPoint.y

                val hit = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                pressedButton = hit
                // Respuesta inmediata en cruceta direccional
                if (hit != null && engine.status == GameStatus.RUNNING &&
                    (hit === btnUp || hit === btnDown || hit === btnLeft || hit === btnRight)
                ) {
                    hit.onClick()
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (engine.status != GameStatus.RUNNING) return false

                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
                    val overDir = listOf(btnUp, btnDown, btnLeft, btnRight)
                        .find { it.contains(touchPoint.x, touchPoint.y) }
                    if (overDir != null && overDir !== pressedButton) {
                        pressedButton = overDir
                        overDir.onClick()
                    }
                    return true
                }

                val dx = touchPoint.x - touchDownX
                val dy = touchPoint.y - touchDownY
                val threshold = 36f

                if (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold) {
                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        engine.setDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                    } else {
                        engine.setDirection(if (dy > 0) Direction.UP else Direction.DOWN)
                    }
                    touchDownX = touchPoint.x
                    touchDownY = touchPoint.y
                    return true
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                pressedButton?.let { btn ->
                    if (btn !== btnUp && btn !== btnDown && btn !== btnLeft && btn !== btnRight &&
                        btn.contains(touchPoint.x, touchPoint.y)
                    ) {
                        btn.onClick()
                    }
                }
                pressedButton = null
                return true
            }
        }
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    private fun updateGameOverButtons() {
        btnEditName = ArcadeButton(140f, 540f, 440f, 75f, "NOMBRE: $playerName", Color.CYAN) {
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
        btnSaveScore = ArcadeButton(140f, 440f, 440f, 75f, "GUARDAR RECORD", Color.GREEN) {
            if (!isHighScoreSaved && engine.score > 0) {
                game.scoreRepository.addScore(
                    GameId.SNAKE,
                    ScoreEntry(initials = playerName, score = engine.score, level = engine.level)
                )
                isHighScoreSaved = true
                game.goToHighScores(GameId.SNAKE)
            }
        }
    }

    private fun getActiveButtons(): List<ArcadeButton> {
        val list = mutableListOf<ArcadeButton>()
        when (engine.status) {
            GameStatus.RUNNING -> {
                list.add(btnPause)
                if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
                    list.addAll(listOf(btnUp, btnDown, btnLeft, btnRight))
                }
            }
            GameStatus.PAUSED -> list.addAll(listOf(btnResume, btnRestart, btnQuit))
            GameStatus.GAME_OVER -> {
                val isHighScore = game.scoreRepository.isHighScore(GameId.SNAKE, engine.score)
                if (isHighScore && !isHighScoreSaved && engine.score > 0) {
                    list.add(btnEditName)
                    list.add(btnSaveScore)
                }
                list.addAll(listOf(btnRetry, btnGameOverMenu))
            }
        }
        return list
    }

    override fun render(delta: Float) {
        blinkTimer += delta
        val scoreBefore = engine.score
        engine.update(delta)

        if (engine.score > scoreBefore) {
            if (engine.score - scoreBefore >= 40) audio.playClear() else audio.playMove()
        }

        if (engine.status == GameStatus.GAME_OVER && !playedGameOverSound) {
            audio.playGameOver()
            playedGameOverSound = true
            updateGameOverButtons()
        } else if (engine.status != GameStatus.GAME_OVER) {
            playedGameOverSound = false
        }

        ScreenUtils.clear(0.02f, 0.04f, 0.03f, 1f)
        game.viewport.apply()
        val r = game.shapeRenderer
        r.projectionMatrix = game.camera.combined
        r.begin(ShapeRenderer.ShapeType.Filled)

        drawHUD(r)
        drawBoard(r)
        if (engine.status == GameStatus.RUNNING) drawControls(r)

        when (engine.status) {
            GameStatus.PAUSED -> drawPauseOverlay(r)
            GameStatus.GAME_OVER -> drawGameOverOverlay(r)
            else -> {}
        }

        r.end()
    }

    private fun drawHUD(r: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(r, 30f, 1150f, 250f, 95f, Color(0.04f, 0.08f, 0.06f, 0.95f), Color.GREEN, 3f)
        ArcadeFont.drawText(r, "SCORE", 50f, 1210f, 2.1f, Color.GREEN)
        ArcadeFont.drawText(r, engine.score.toString().padStart(6, '0'), 50f, 1168f, 3.0f, Color.WHITE)

        ArcadeUIHelper.drawNeonBox(r, 295f, 1150f, 200f, 95f, Color(0.04f, 0.08f, 0.06f, 0.95f), Color.CYAN, 3f)
        ArcadeFont.drawText(r, "NIVEL ${engine.level}", 315f, 1210f, 2.1f, Color.YELLOW)
        ArcadeFont.drawText(r, "TAM ${engine.snake.size}", 315f, 1168f, 2.1f, Color.LIGHT_GRAY)

        ArcadeUIHelper.drawButton(r, btnPause, btnPause == pressedButton, 2.8f)

        val highScore = game.scoreRepository.getScores(GameId.SNAKE).firstOrNull()?.score ?: 0
        ArcadeFont.drawText(r, "RECORD: ${highScore.toString().padStart(6, '0')}", 35f, 1110f, 2.2f, Color.YELLOW)
    }

    private fun drawBoard(r: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(r, boardX - 5f, boardY - 5f, boardW + 10f, boardH + 10f, Color(0.02f, 0.05f, 0.03f, 1f), Color.GREEN, 4f)

        // Rejilla sutil
        r.color = Color(0.06f, 0.12f, 0.08f, 1f)
        for (c in 1 until engine.cols) {
            r.rect(boardX + c * cellSize, boardY, 1f, boardH)
        }
        for (row in 1 until engine.rows) {
            r.rect(boardX, boardY + row * cellSize, boardW, 1f)
        }

        // Comida normal
        val fx = boardX + engine.food.x * cellSize + cellSize / 2f
        val fy = boardY + engine.food.y * cellSize + cellSize / 2f
        r.color = Color.RED
        r.circle(fx, fy, cellSize * 0.4f)
        r.color = Color.WHITE
        r.circle(fx - 3f, fy + 3f, cellSize * 0.12f)

        // Fruta bonus temporal
        engine.bonusFood?.let { bonus ->
            if ((blinkTimer % 0.3f) > 0.1f) {
                val bx = boardX + bonus.pos.x * cellSize + cellSize / 2f
                val by = boardY + bonus.pos.y * cellSize + cellSize / 2f
                r.color = Color.YELLOW
                r.circle(bx, by, cellSize * 0.46f)
                r.color = Color.MAGENTA
                r.circle(bx, by, cellSize * 0.25f)
            }
        }

        // Serpiente
        engine.snake.forEachIndexed { idx, pt ->
            val sx = boardX + pt.x * cellSize
            val sy = boardY + pt.y * cellSize
            if (idx == 0) {
                r.color = Color(0.4f, 1f, 0.5f, 1f)
                r.rect(sx + 1f, sy + 1f, cellSize - 2f, cellSize - 2f)
                r.color = Color.BLACK
                r.rect(sx + 6f, sy + cellSize - 9f, 4f, 4f)
                r.rect(sx + cellSize - 10f, sy + cellSize - 9f, 4f, 4f)
            } else {
                val shade = (1f - (idx.toFloat() / (engine.snake.size + 6f)) * 0.45f).coerceIn(0.4f, 1f)
                r.color = Color(0.1f * shade, 0.85f * shade, 0.25f * shade, 1f)
                r.rect(sx + 2f, sy + 2f, cellSize - 4f, cellSize - 4f)
            }
        }
    }

    private fun drawControls(r: ShapeRenderer) {
        if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
            ArcadeUIHelper.drawButton(r, btnUp, btnUp == pressedButton, 3.8f)
            ArcadeUIHelper.drawButton(r, btnDown, btnDown == pressedButton, 3.8f)
            ArcadeUIHelper.drawButton(r, btnLeft, btnLeft == pressedButton, 3.8f)
            ArcadeUIHelper.drawButton(r, btnRight, btnRight == pressedButton, 3.8f)
        } else {
            ArcadeFont.drawCenteredText(r, "DESLIZA EN 4 DIRECCIONES PARA GIRAR", 360f, 140f, 2.0f, Color.GRAY)
        }
    }

    private fun drawPauseOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.75f)
        r.rect(0f, 0f, 720f, 1280f)
        ArcadeUIHelper.drawNeonBox(r, 150f, 430f, 420f, 420f, Color(0.04f, 0.08f, 0.06f, 0.98f), Color.YELLOW, 4f)
        ArcadeFont.drawCenteredText(r, "PAUSA", 360f, 790f, 4.5f, Color.YELLOW)
        ArcadeUIHelper.drawButton(r, btnResume, btnResume == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnRestart, btnRestart == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnQuit, btnQuit == pressedButton, 2.8f)
    }

    private fun drawGameOverOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.85f)
        r.rect(0f, 0f, 720f, 1280f)

        val isHighScore = game.scoreRepository.isHighScore(GameId.SNAKE, engine.score) && engine.score > 0
        val boxH = if (isHighScore && !isHighScoreSaved) 660f else 460f
        val boxY = if (isHighScore && !isHighScoreSaved) 190f else 350f

        ArcadeUIHelper.drawNeonBox(r, 100f, boxY, 520f, boxH, Color(0.04f, 0.08f, 0.06f, 0.98f), Color.RED, 4f)
        ArcadeFont.drawCenteredText(r, "FIN DE PARTIDA", 360f, boxY + boxH - 60f, 4.0f, Color.RED)
        ArcadeFont.drawCenteredText(r, "PUNTOS: ${engine.score.toString().padStart(6, '0')}", 360f, boxY + boxH - 120f, 2.8f, Color.YELLOW)
        ArcadeFont.drawCenteredText(r, "LONGITUD: ${engine.snake.size}   NIVEL: ${engine.level}", 360f, boxY + boxH - 165f, 2.1f, Color.WHITE)

        if (isHighScore && !isHighScoreSaved) {
            ArcadeFont.drawCenteredText(r, "- NUEVO RECORD TOP 10 -", 360f, boxY + boxH - 220f, 2.4f, Color.GREEN)
            ArcadeFont.drawCenteredText(r, "TOCA PARA EDITAR TU NOMBRE:", 360f, boxY + boxH - 260f, 1.9f, Color.LIGHT_GRAY)
            ArcadeUIHelper.drawButton(r, btnEditName, btnEditName == pressedButton, 2.8f)
            ArcadeUIHelper.drawButton(r, btnSaveScore, btnSaveScore == pressedButton, 2.8f)
        }

        ArcadeUIHelper.drawButton(r, btnRetry, btnRetry == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnGameOverMenu, btnGameOverMenu == pressedButton, 2.8f)
    }

    override fun dispose() {
        audio.dispose()
    }
}
