package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.ControlMode
import com.retroarcade.presentation.MainMenuPresenter

class MainMenuScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val presenter = MainMenuPresenter(game.scoreRepository, game.settingsRepository)
    private val buttons = mutableListOf<ArcadeButton>()
    private var pressedButton: ArcadeButton? = null
    private val touchPoint = Vector3()
    private var blinkTimer = 0f

    private val btnPrev = ArcadeButton(50f, 620f, 80f, 80f, "<", Color.CYAN) {
        presenter.selectPreviousGame()
    }
    private val btnNext = ArcadeButton(590f, 620f, 80f, 80f, ">", Color.CYAN) {
        presenter.selectNextGame()
    }
    private val btnPlay = ArcadeButton(160f, 320f, 400f, 85f, "JUGAR", Color.GREEN) {
        game.goToGame(presenter.selectedGame)
    }
    private val btnHighScores = ArcadeButton(70f, 180f, 270f, 75f, "RECORDS", Color.MAGENTA) {
        game.goToHighScores(presenter.selectedGame)
    }
    private val btnSettings = ArcadeButton(380f, 180f, 270f, 75f, "AJUSTES", Color.YELLOW) {
        game.goToSettings()
    }

    override fun show() {
        buttons.clear()
        buttons.addAll(listOf(btnPrev, btnNext, btnPlay, btnHighScores, btnSettings))

        Gdx.input.inputProcessor = object : InputAdapter() {
            private var touchDownX = 0f

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                touchDownX = touchPoint.x

                pressedButton = buttons.find { it.contains(touchPoint.x, touchPoint.y) }
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

                val deltaX = touchPoint.x - touchDownX
                if (Math.abs(deltaX) > 80f) {
                    if (deltaX > 0) presenter.selectPreviousGame() else presenter.selectNextGame()
                    pressedButton = null
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

    override fun hide() {
        Gdx.input.inputProcessor = null
    }

    override fun render(delta: Float) {
        blinkTimer += delta
        ScreenUtils.clear(0.04f, 0.04f, 0.08f, 1f)

        game.viewport.apply()
        val renderer = game.shapeRenderer
        renderer.projectionMatrix = game.camera.combined

        // Todo el renderizado se realiza en un único begin / end continuo
        renderer.begin(ShapeRenderer.ShapeType.Filled)

        // 1. Cabecera Arcade Neón
        ArcadeUIHelper.drawNeonBox(renderer, 40f, 1100f, 640f, 130f, Color(0.07f, 0.09f, 0.18f, 0.95f), Color.CYAN, 4f)
        ArcadeFont.drawCenteredText(renderer, "RETRO ARCADE", 360f, 1180f, 5.2f, Color.CYAN)

        val subColor = if ((blinkTimer % 1.2f) > 0.4f) Color.YELLOW else Color.GRAY
        ArcadeFont.drawCenteredText(renderer, "- MAQUINA RECREATIVA OFFLINE -", 360f, 1125f, 2.3f, subColor)

        // 2. Tarjeta del Juego Seleccionado
        val cardX = 150f
        val cardY = 440f
        val cardW = 420f
        val cardH = 610f
        ArcadeUIHelper.drawNeonBox(renderer, cardX, cardY, cardW, cardH, Color(0.05f, 0.07f, 0.14f, 0.95f), Color.MAGENTA, 4f)

        // Icono procedural
        ArcadeIcons.drawIcon(renderer, presenter.selectedGame, 360f, 800f, 190f)

        // Nombre del juego
        ArcadeFont.drawCenteredText(renderer, presenter.selectedGame.displayName, 360f, 640f, 3.8f, Color.WHITE)

        // Récord actual
        val highScore = presenter.getHighScore(presenter.selectedGame)
        val recordText = "RECORD: ${highScore.toString().padStart(6, '0')}"
        ArcadeFont.drawCenteredText(renderer, recordText, 360f, 570f, 2.8f, Color.YELLOW)

        // Puntos indicadores de carrusel (1 a 6)
        for (i in presenter.games.indices) {
            val dotX = 270f + i * 36f
            val dotY = 500f
            if (i == presenter.selectedIndex) {
                renderer.color = Color.CYAN
                renderer.circle(dotX, dotY, 9f)
            } else {
                renderer.color = Color.DARK_GRAY
                renderer.circle(dotX, dotY, 5f)
            }
        }

        // 3. Botones Interactivos
        for (btn in buttons) {
            val isPressed = (btn == pressedButton)
            val pSize = if (btn == btnPlay) 4.5f else if (btn == btnPrev || btn == btnNext) 4.0f else 2.6f
            ArcadeUIHelper.drawButton(renderer, btn, isPressed, pSize)
        }

        // 4. Barra de Estado Inferior
        val modeStr = if (presenter.getControlMode() == ControlMode.VIRTUAL_PAD) "PAD VIRTUAL" else "GESTOS TACTILES"
        val soundStr = if (presenter.isMuted()) "MUTED" else "SOUND ON"
        ArcadeFont.drawCenteredText(renderer, "[$modeStr]   *   [$soundStr]", 360f, 80f, 2.2f, Color.GRAY)

        renderer.end()
    }
}
