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
import com.retroarcade.domain.arkanoid.ArkanoidEngine
import com.retroarcade.domain.arkanoid.Brick
import com.retroarcade.domain.arkanoid.PowerUpType

class ArkanoidScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val engine = ArkanoidEngine()
    private val audio = ChiptuneAudio(game.settingsRepository)
    private val touchPoint = Vector3()

    private var playerName = "JUGADOR"
    private var isHighScoreSaved = false
    private var blinkTimer = 0f

    // Punteros activos para movimiento fluido continuo al mantener pulsado
    private val activePointers = mutableMapOf<Int, ArcadeButton>()

    // ── Virtual Pad (estilo Space Invaders: botones grandes y cómodos) ──────
    private val btnLeft = ArcadeButton(30f, 75f, 170f, 100f, "<<", Color.CYAN) {}
    private val btnRight = ArcadeButton(520f, 75f, 170f, 100f, ">>", Color.CYAN) {}
    private val btnLaunch = ArcadeButton(225f, 75f, 270f, 100f, "LANZAR", Color.GREEN) {
        engine.launchBall()
    }

    // ── Pausa / Game Overlay ─────────────────────────────────────────────────
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
    private var livesBeforeUpdate = 3

    override fun show() {
        updateGameOverButtons()
        Gdx.input.inputProcessor = object : InputAdapter() {
            private var lastDragX = 0f

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                lastDragX = touchPoint.x
                val hitBtn = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                if (hitBtn != null) {
                    activePointers[pointer] = hitBtn
                    pressedButton = hitBtn
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (engine.status != GameStatus.RUNNING) return false
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                if (activePointers.containsKey(pointer)) {
                    val overBtn = getActiveButtons().find { it.contains(touchPoint.x, touchPoint.y) }
                    if (overBtn === btnLeft || overBtn === btnRight) {
                        activePointers[pointer] = overBtn
                        pressedButton = overBtn
                    } else if (overBtn == null) {
                        activePointers.remove(pointer)
                        pressedButton = null
                    }
                    return true
                }
                val mode = game.settingsRepository.getControlMode()
                if (mode == ControlMode.TOUCH_GESTURES) {
                    // Mover la pala directamente a la coordenada X
                    engine.movePaddle(touchPoint.x)
                    engine.launchBall()
                }
                lastDragX = touchPoint.x
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                val btn = activePointers.remove(pointer)
                if (btn != null && btn !== btnLeft && btn !== btnRight && btn.contains(touchPoint.x, touchPoint.y)) {
                    btn.onClick()
                }
                if (pressedButton === btn) pressedButton = null
                return true
            }
        }
    }

    override fun hide() {
        Gdx.input.inputProcessor = null
        activePointers.clear()
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
                    GameId.ARKANOID,
                    ScoreEntry(initials = playerName, score = engine.score, level = engine.level)
                )
                isHighScoreSaved = true
                game.goToHighScores(GameId.ARKANOID)
            }
        }
    }

    private fun getActiveButtons(): List<ArcadeButton> {
        val list = mutableListOf<ArcadeButton>()
        when (engine.status) {
            GameStatus.RUNNING -> {
                list.add(btnPause)
                if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
                    list.addAll(listOf(btnLeft, btnRight, btnLaunch))
                }
            }
            GameStatus.PAUSED -> list.addAll(listOf(btnResume, btnRestart, btnQuit))
            GameStatus.GAME_OVER -> {
                val isHighScore = game.scoreRepository.isHighScore(GameId.ARKANOID, engine.score)
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
        if (engine.status == GameStatus.RUNNING &&
            game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD
        ) {
            val movingLeft = activePointers.values.any { it === btnLeft }
            val movingRight = activePointers.values.any { it === btnRight }
            if (movingLeft && !movingRight) {
                engine.movePaddleBy(-480f * delta)
            } else if (movingRight && !movingLeft) {
                engine.movePaddleBy(480f * delta)
            }
        }
        livesBeforeUpdate = engine.lives
        engine.update(delta)

        if (engine.lives < livesBeforeUpdate) audio.playDrop()

        if (engine.status == GameStatus.GAME_OVER && !playedGameOverSound) {
            audio.playGameOver()
            playedGameOverSound = true
            updateGameOverButtons()
        } else if (engine.status != GameStatus.GAME_OVER) {
            playedGameOverSound = false
        }

        ScreenUtils.clear(0.03f, 0.03f, 0.06f, 1f)
        game.viewport.apply()
        val r = game.shapeRenderer
        r.projectionMatrix = game.camera.combined
        r.begin(ShapeRenderer.ShapeType.Filled)

        drawHUD(r)
        drawGameBoard(r)
        if (engine.status == GameStatus.RUNNING) drawControls(r)

        when (engine.status) {
            GameStatus.PAUSED -> drawPauseOverlay(r)
            GameStatus.GAME_OVER -> drawGameOverOverlay(r)
            else -> {}
        }

        r.end()
    }

    // ── HUD ─────────────────────────────────────────────────────────────────
    private fun drawHUD(r: ShapeRenderer) {
        ArcadeUIHelper.drawNeonBox(r, 30f, 1150f, 240f, 95f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.CYAN, 3f)
        ArcadeFont.drawText(r, "SCORE", 50f, 1210f, 2.1f, Color.CYAN)
        ArcadeFont.drawText(r, engine.score.toString().padStart(6, '0'), 50f, 1168f, 3.0f, Color.WHITE)

        ArcadeUIHelper.drawNeonBox(r, 285f, 1150f, 160f, 95f, Color(0.05f, 0.07f, 0.15f, 0.95f), Color.MAGENTA, 3f)
        ArcadeFont.drawText(r, "NIV ${engine.level}", 300f, 1210f, 2.1f, Color.YELLOW)
        ArcadeFont.drawText(r, "BOLAS: ${engine.lives}", 300f, 1168f, 2.1f, Color.LIGHT_GRAY)

        // Vidas como círculos
        for (i in 0 until engine.lives) {
            r.color = Color.ORANGE
            r.circle(475f + i * 30f, 1198f, 10f)
        }

        ArcadeUIHelper.drawButton(r, btnPause, btnPause == pressedButton, 2.8f)

        val highScore = game.scoreRepository.getScores(GameId.ARKANOID).firstOrNull()?.score ?: 0
        ArcadeFont.drawText(r, "RECORD: ${highScore.toString().padStart(6, '0')}", 35f, 1115f, 2.2f, Color.YELLOW)
    }

    // ── Tablero de Juego ─────────────────────────────────────────────────────
    private fun drawGameBoard(r: ShapeRenderer) {
        // Marco del área de juego
        ArcadeUIHelper.drawNeonBox(r, 4f, 190f, 712f, 940f, Color(0.02f, 0.03f, 0.07f, 1f), Color.CYAN, 3f)

        // Ladrillos
        for (brick in engine.bricks) {
            drawBrick(r, brick)
        }

        // Power-ups cayendo
        for (pu in engine.powerUps) {
            val puColor = when (pu.type) {
                PowerUpType.WIDE_PADDLE -> Color.GREEN
                PowerUpType.MULTIBALL -> Color.YELLOW
                PowerUpType.LASER -> Color.RED
            }
            r.color = puColor
            r.rect(pu.x - 18f, pu.y - 10f, 36f, 20f)
            val label = when (pu.type) {
                PowerUpType.WIDE_PADDLE -> "W"
                PowerUpType.MULTIBALL -> "M"
                PowerUpType.LASER -> "L"
            }
            ArcadeFont.drawCenteredText(r, label, pu.x, pu.y + 4f, 2.2f, Color.BLACK)
        }

        // Pala
        val px = engine.paddleX - engine.paddleWidth / 2f
        val py = engine.paddleY
        r.color = Color(0.3f, 0.7f, 1f, 1f)
        r.rect(px, py, engine.paddleWidth, engine.paddleHeight)
        // Brillo central de la pala
        r.color = Color(0.6f, 0.9f, 1f, 0.9f)
        r.rect(px + engine.paddleWidth * 0.3f, py + 4f, engine.paddleWidth * 0.4f, 6f)

        // Bola
        r.color = Color.WHITE
        r.circle(engine.ballX, engine.ballY, engine.ballRadius)
        // Brillo en la bola
        r.color = Color(0.9f, 0.9f, 1f, 0.5f)
        r.circle(engine.ballX - 3f, engine.ballY + 3f, engine.ballRadius * 0.4f)
    }

    private fun drawBrick(r: ShapeRenderer, brick: Brick) {
        val hpRatio = brick.hp.toFloat() / brick.maxHp.toFloat()
        val baseColor = when (brick.maxHp) {
            3 -> Color(0.9f, 0.2f, 0.2f, 1f)   // rojo — más resistente
            2 -> Color(0.9f, 0.6f, 0.1f, 1f)   // naranja
            else -> Color(0.2f, 0.8f, 0.2f, 1f) // verde — frágil
        }
        // Oscurecer según daño recibido
        val displayColor = baseColor.cpy().mul(0.4f + 0.6f * hpRatio)
        r.color = displayColor
        r.rect(brick.x + 2f, brick.y + 2f, brick.width - 4f, brick.height - 4f)

        // Borde neón
        r.color = displayColor.cpy().add(0.3f, 0.3f, 0.3f, 0f)
        r.rect(brick.x, brick.y + brick.height - 2f, brick.width, 2f)
        r.rect(brick.x, brick.y, 2f, brick.height)
    }

    // ── Controles ────────────────────────────────────────────────────────────
    private fun drawControls(r: ShapeRenderer) {
        if (game.settingsRepository.getControlMode() == ControlMode.VIRTUAL_PAD) {
            ArcadeUIHelper.drawButton(r, btnLeft, activePointers.values.any { it === btnLeft }, 3.5f)
            ArcadeUIHelper.drawButton(r, btnRight, activePointers.values.any { it === btnRight }, 3.5f)
            if (!engine.ballInPlay) {
                ArcadeUIHelper.drawButton(r, btnLaunch, activePointers.values.any { it === btnLaunch }, 2.8f)
            }
        } else {
            ArcadeFont.drawCenteredText(r, "DESLIZA PARA MOVER LA PALA", 360f, 108f, 2.0f, Color.GRAY)
        }
    }

    // ── Overlays ─────────────────────────────────────────────────────────────
    private fun drawPauseOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.75f)
        r.rect(0f, 0f, 720f, 1280f)
        ArcadeUIHelper.drawNeonBox(r, 150f, 430f, 420f, 420f, Color(0.06f, 0.08f, 0.18f, 0.98f), Color.YELLOW, 4f)
        ArcadeFont.drawCenteredText(r, "PAUSA", 360f, 790f, 4.5f, Color.YELLOW)
        ArcadeUIHelper.drawButton(r, btnResume, btnResume == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnRestart, btnRestart == pressedButton, 2.8f)
        ArcadeUIHelper.drawButton(r, btnQuit, btnQuit == pressedButton, 2.8f)
    }

    private fun drawGameOverOverlay(r: ShapeRenderer) {
        r.color = Color(0f, 0f, 0f, 0.85f)
        r.rect(0f, 0f, 720f, 1280f)

        val isHighScore = game.scoreRepository.isHighScore(GameId.ARKANOID, engine.score) && engine.score > 0
        val boxH = if (isHighScore && !isHighScoreSaved) 660f else 460f
        val boxY = if (isHighScore && !isHighScoreSaved) 190f else 350f

        ArcadeUIHelper.drawNeonBox(r, 100f, boxY, 520f, boxH, Color(0.06f, 0.08f, 0.18f, 0.98f), Color.RED, 4f)
        ArcadeFont.drawCenteredText(r, "FIN DE PARTIDA", 360f, boxY + boxH - 60f, 4.0f, Color.RED)
        ArcadeFont.drawCenteredText(r, "PUNTOS: ${engine.score.toString().padStart(6, '0')}", 360f, boxY + boxH - 120f, 2.8f, Color.YELLOW)
        ArcadeFont.drawCenteredText(r, "NIVEL: ${engine.level}", 360f, boxY + boxH - 165f, 2.4f, Color.WHITE)

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
