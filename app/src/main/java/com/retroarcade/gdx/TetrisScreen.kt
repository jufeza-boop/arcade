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
import com.retroarcade.domain.tetris.TetrisEngine
import com.retroarcade.domain.tetris.TetrominoType

class TetrisScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val engine = TetrisEngine()
    private val audio = ChiptuneAudio(game.settingsRepository)
    private val touchPoint = Vector3()

    // Dimensiones del tablero en pantalla (720x1280)
    private val cellSize = 28f
    private val boardX = 220f
    private val boardY = 460f
    private val boardW = 10 * cellSize // 280f
    private val boardH = 20 * cellSize // 560f

    // Estado de nombre del jugador al guardar récord
    private var playerName = "JUGADOR"
    private var isHighScoreSaved = false

    // Botones del Virtual Pad
    private val btnLeft = ArcadeButton(40f, 180f, 80f, 80f, "<", Color.CYAN) {
        if (engine.tryMove(-1, 0)) audio.playMove()
    }
    private val btnRight = ArcadeButton(220f, 180f, 80f, 80f, ">", Color.CYAN) {
        if (engine.tryMove(1, 0)) audio.playMove()
    }
    private val btnDown = ArcadeButton(130f, 90f, 80f, 80f, "V", Color.CYAN) {
        val yBefore = engine.currentPiece.y
        engine.softDrop()
        if (engine.currentPiece.y < yBefore) audio.playMove()
    }
    private val btnUp = ArcadeButton(130f, 270f, 80f, 80f, "^", Color.CYAN) {
        if (engine.rotateClockwiseWithKick()) audio.playRotate()
    }
    private val btnActionA = ArcadeButton(450f, 160f, 100f, 100f, "A", Color.YELLOW) {
        if (engine.rotateClockwiseWithKick()) audio.playRotate()
    }
    private val btnActionB = ArcadeButton(580f, 160f, 100f, 100f, "DROP", Color.RED) {
        engine.hardDrop()
        audio.playDrop()
    }

    // Botón de Pausa HUD
    private val btnPause = ArcadeButton(610f, 1180f, 80f, 60f, "||", Color.YELLOW) {
        engine.togglePause()
    }

    // Botón táctil para modo Touch Gestures
    private val btnTouchHardDrop = ArcadeButton(210f, 100f, 300f, 80f, "HARD DROP", Color.RED) {
        engine.hardDrop()
        audio.playDrop()
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

                pressedButton = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                val deltaX = touchPoint.x - touchDownX
                val deltaY = touchPoint.y - touchDownY

                if (engine.status == GameStatus.RUNNING && game.settingsRepository.getControlMode() == ControlMode.TOUCH_GESTURES) {
                    if (Math.abs(deltaX) > 60f && Math.abs(deltaX) > Math.abs(deltaY)) {
                        if (deltaX > 0) {
                            if (engine.tryMove(1, 0)) audio.playMove()
                        } else {
                            if (engine.tryMove(-1, 0)) audio.playMove()
                        }
                        pressedButton = null
                        return true
                    } else if (deltaY < -80f && Math.abs(deltaY) > Math.abs(deltaX)) {
                        engine.hardDrop()
                        audio.playDrop()
                        pressedButton = null
                        return true
                    } else if (Math.abs(deltaX) < 30f && Math.abs(deltaY) < 30f && touchPoint.y > 400f) {
                        if (engine.rotateClockwiseWithKick()) audio.playRotate()
                        pressedButton = null
                        return true
                    }
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
                    list.addAll(listOf(btnLeft, btnRight, btnDown, btnUp, btnActionA, btnActionB))
                } else {
                    list.add(btnTouchHardDrop)
                }
            }
            GameStatus.PAUSED -> {
                list.addAll(listOf(btnResume, btnRestart, btnQuit))
            }
            GameStatus.GAME_OVER -> {
                val isHighScore = game.scoreRepository.isHighScore(GameId.TETRIS, engine.score)
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
            game.scoreRepository.addScore(GameId.TETRIS, entry)
            isHighScoreSaved = true
            game.goToHighScores(GameId.TETRIS)
        }
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        val linesBefore = engine.totalLinesCleared
        engine.update(delta)
        if (engine.totalLinesCleared > linesBefore) {
            audio.playClear()
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

        // 2. Pozo de Tetris y Bloques
        drawGameBoard(renderer)

        // 3. Controles Inferiores
        if (engine.status == GameStatus.RUNNING) {
            drawControls(renderer)
        }

        // 4. Overlays según estado
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

        // Marcador de Nivel y Líneas en el centro
        ArcadeUIHelper.drawNeonBox(renderer, 300f, 1140f, 190f, 100f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.MAGENTA, 3f)
        ArcadeFont.drawText(renderer, "NIVEL ${engine.level}", 315f, 1205f, 2.2f, Color.YELLOW)
        ArcadeFont.drawText(renderer, "LIN ${engine.totalLinesCleared}", 315f, 1160f, 2.2f, Color.LIGHT_GRAY)

        // Previsualización de siguiente pieza (NEXT)
        ArcadeUIHelper.drawNeonBox(renderer, 500f, 1140f, 100f, 100f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.CYAN, 3f)
        ArcadeFont.drawCenteredText(renderer, "NEXT", 550f, 1222f, 1.8f, Color.YELLOW)
        drawMiniTetromino(renderer, engine.nextPiece.type, 550f, 1175f, 14f)

        // Botón Pausa
        ArcadeUIHelper.drawButton(renderer, btnPause, btnPause == pressedButton, 2.8f)

        // Récord Actual en barra fina
        val highScore = game.scoreRepository.getScores(GameId.TETRIS).firstOrNull()?.score ?: 0
        ArcadeFont.drawText(renderer, "RECORD: ${highScore.toString().padStart(6, '0')}", 35f, 1080f, 2.3f, Color.YELLOW)
    }

    private fun drawGameBoard(renderer: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(renderer, boardX - 6f, boardY - 6f, boardW + 12f, boardH + 12f, Color(0.02f, 0.03f, 0.07f, 1f), Color.CYAN, 4f)

        renderer.color = Color(0.07f, 0.09f, 0.16f, 1f)
        for (c in 1 until 10) {
            renderer.rect(boardX + c * cellSize, boardY, 1f, boardH)
        }
        for (r in 1 until 20) {
            renderer.rect(boardX, boardY + r * cellSize, boardW, 1f)
        }

        // 1. Bloques asentados en el tablero
        for (row in 0 until 20) {
            for (col in 0 until 10) {
                val colorIdx = engine.board.cells[row][col]
                if (colorIdx != 0) {
                    drawCellBlock(renderer, boardX + col * cellSize, boardY + row * cellSize, cellSize, colorIdx)
                }
            }
        }

        // 2. Ghost Piece (sombra de caída)
        if (engine.status == GameStatus.RUNNING) {
            val ghost = engine.calculateGhostPiece()
            for ((col, row) in ghost.getBlockPositions()) {
                if (row in 0 until 20) {
                    val px = boardX + col * cellSize
                    val py = boardY + row * cellSize
                    renderer.color = Color(0.2f, 0.25f, 0.35f, 0.5f)
                    renderer.rect(px + 2f, py + 2f, cellSize - 4f, cellSize - 4f)
                }
            }
        }

        // 3. Pieza activa actual
        if (engine.status == GameStatus.RUNNING) {
            val pieceColor = engine.currentPiece.type.colorIndex
            for ((col, row) in engine.currentPiece.getBlockPositions()) {
                if (row in 0 until 20) {
                    drawCellBlock(renderer, boardX + col * cellSize, boardY + row * cellSize, cellSize, pieceColor)
                }
            }
        }
    }

    private fun drawCellBlock(renderer: ShapeRenderer, px: Float, py: Float, size: Float, colorIdx: Int) {
        val baseColor = getColorByIndex(colorIdx)
        renderer.color = baseColor
        renderer.rect(px, py, size, size)

        renderer.color = baseColor.cpy().mul(0.65f)
        renderer.rect(px + 3f, py + 3f, size - 6f, size - 6f)

        renderer.color = Color.WHITE
        renderer.rect(px + 4f, py + size - 6f, 3f, 3f)
    }

    private fun drawMiniTetromino(renderer: ShapeRenderer, type: TetrominoType, cx: Float, cy: Float, blockSize: Float) {
        val offsets = type.getOffsets(0)
        val color = getColorByIndex(type.colorIndex)
        for ((dx, dy) in offsets) {
            val x = cx + dx * blockSize - blockSize / 2f
            val y = cy + dy * blockSize - blockSize / 2f
            renderer.color = color
            renderer.rect(x, y, blockSize - 2f, blockSize - 2f)
        }
    }

    private fun drawControls(renderer: ShapeRenderer) {
        val isPad = (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD)
        if (isPad) {
            ArcadeUIHelper.drawButton(renderer, btnLeft, btnLeft == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnRight, btnRight == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnDown, btnDown == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnUp, btnUp == pressedButton, 4.0f)

            ArcadeUIHelper.drawButton(renderer, btnActionA, btnActionA == pressedButton, 4.0f)
            ArcadeUIHelper.drawButton(renderer, btnActionB, btnActionB == pressedButton, 2.5f)
        } else {
            ArcadeUIHelper.drawButton(renderer, btnTouchHardDrop, btnTouchHardDrop == pressedButton, 3.2f)
            ArcadeFont.drawCenteredText(renderer, "DESLIZA LATERAL: MOVER * TAP: GIRAR", 360f, 60f, 2.0f, Color.GRAY)
        }
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

        val isHighScore = game.scoreRepository.isHighScore(GameId.TETRIS, engine.score) && engine.score > 0
        val boxH = if (isHighScore && !isHighScoreSaved) 660f else 460f
        val boxY = if (isHighScore && !isHighScoreSaved) 190f else 350f

        ArcadeUIHelper.drawNeonBox(renderer, 100f, boxY, 520f, boxH, Color(0.06f, 0.08f, 0.18f, 0.98f), Color.RED, 4f)
        ArcadeFont.drawCenteredText(renderer, "FIN DE PARTIDA", 360f, boxY + boxH - 60f, 4.0f, Color.RED)

        val scoreStr = "PUNTOS: ${engine.score.toString().padStart(6, '0')}"
        ArcadeFont.drawCenteredText(renderer, scoreStr, 360f, boxY + boxH - 120f, 2.8f, Color.YELLOW)

        val detailsStr = "LINEAS: ${engine.totalLinesCleared}   NIVEL: ${engine.level}"
        ArcadeFont.drawCenteredText(renderer, detailsStr, 360f, boxY + boxH - 165f, 2.2f, Color.WHITE)

        if (isHighScore && !isHighScoreSaved) {
            ArcadeFont.drawCenteredText(renderer, "- NUEVO RECORD TOP 10 -", 360f, boxY + boxH - 220f, 2.4f, Color.GREEN)
            ArcadeFont.drawCenteredText(renderer, "TOCA PARA EDITAR TU NOMBRE:", 360f, boxY + boxH - 260f, 1.9f, Color.LIGHT_GRAY)

            // Botón interactivo para editar nombre
            ArcadeUIHelper.drawButton(renderer, btnEditName, btnEditName == pressedButton, 2.8f)

            // Botón para guardar récord
            ArcadeUIHelper.drawButton(renderer, btnSaveScore, btnSaveScore == pressedButton, 2.8f)
        }

        ArcadeUIHelper.drawButton(renderer, btnRetry, btnRetry == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(renderer, btnGameOverMenu, btnGameOverMenu == pressedButton, 2.8f)
    }

    private fun getColorByIndex(index: Int): Color {
        return when (index) {
            1 -> Color.CYAN
            2 -> Color.YELLOW
            3 -> Color.MAGENTA
            4 -> Color.GREEN
            5 -> Color.RED
            6 -> Color.BLUE
            7 -> Color.ORANGE
            else -> Color.WHITE
        }
    }

    override fun dispose() {
        audio.dispose()
    }
}
