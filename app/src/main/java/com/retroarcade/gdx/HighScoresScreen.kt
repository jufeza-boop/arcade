package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.GameId
import com.retroarcade.presentation.HighScoresPresenter

class HighScoresScreen(
    private val game: RetroArcadeGame,
    initialGame: GameId = GameId.TETRIS
) : ScreenAdapter() {

    private val presenter = HighScoresPresenter(game.scoreRepository, initialGame)
    private val buttons = mutableListOf<ArcadeButton>()
    private var pressedButton: ArcadeButton? = null
    private val touchPoint = Vector3()

    private val btnPrev = ArcadeButton(50f, 1020f, 80f, 70f, "<", Color.CYAN) {
        presenter.selectPreviousGame()
    }
    private val btnNext = ArcadeButton(590f, 1020f, 80f, 70f, ">", Color.CYAN) {
        presenter.selectNextGame()
    }
    private val btnBack = ArcadeButton(160f, 80f, 400f, 80f, "VOLVER AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    override fun show() {
        buttons.clear()
        buttons.addAll(listOf(btnPrev, btnNext, btnBack))

        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                pressedButton = buttons.find { it.contains(touchPoint.x, touchPoint.y) }
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)

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
        ScreenUtils.clear(0.04f, 0.04f, 0.08f, 1f)

        game.viewport.apply()
        val renderer = game.shapeRenderer
        renderer.projectionMatrix = game.camera.combined

        renderer.begin(ShapeRenderer.ShapeType.Filled)

        // 1. Título
        ArcadeUIHelper.drawNeonBox(renderer, 50f, 1140f, 620f, 90f, Color(0.07f, 0.09f, 0.18f, 0.95f), Color.MAGENTA, 3f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, "TABLA DE RECORDS", 360f, 1185f, 4.0f, Color.MAGENTA)

        // 2. Selector de Juego
        ArcadeUIHelper.drawNeonBox(renderer, 150f, 1020f, 420f, 70f, Color(0.05f, 0.07f, 0.14f, 0.95f), Color.CYAN, 2f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, presenter.selectedGame.displayName, 360f, 1055f, 3.2f, Color.CYAN)

        // Botones < y >
        ArcadeUIHelper.drawButton(renderer, btnPrev, btnPrev == pressedButton, 4.0f)
        ArcadeUIHelper.drawButton(renderer, btnNext, btnNext == pressedButton, 4.0f)

        // 3. Tabla Top 10
        val tableX = 50f
        val tableY = 200f
        val tableW = 620f
        val tableH = 780f
        ArcadeUIHelper.drawNeonBox(renderer, tableX, tableY, tableW, tableH, Color(0.03f, 0.05f, 0.1f, 0.95f), Color.CYAN, 2f)

        val scores = presenter.getCurrentScores()
        renderer.set(ShapeRenderer.ShapeType.Filled)

        if (scores.isEmpty()) {
            ArcadeFont.drawCenteredText(renderer, "SIN RECORDS REGISTRADOS", 360f, 620f, 2.8f, Color.YELLOW)
            ArcadeFont.drawCenteredText(renderer, "JUEGA UNA PARTIDA PARA ENTRAR AL TOP 10", 360f, 570f, 2.0f, Color.GRAY)
        } else {
            // Cabecera de columnas
            ArcadeFont.drawText(renderer, "POS", 70f, 920f, 2.2f, Color.YELLOW)
            ArcadeFont.drawText(renderer, "NOMBRE", 150f, 920f, 2.2f, Color.YELLOW)
            ArcadeFont.drawText(renderer, "PUNTOS", 380f, 920f, 2.2f, Color.YELLOW)
            ArcadeFont.drawText(renderer, "NV", 560f, 920f, 2.2f, Color.YELLOW)

            scores.forEachIndexed { index, entry ->
                val rowY = 850f - (index * 62f)
                val posStr = "#${index + 1}"
                val color = when (index) {
                    0 -> Color.GOLD
                    1 -> Color.LIGHT_GRAY
                    2 -> Color(0.85f, 0.55f, 0.25f, 1f)
                    else -> Color.WHITE
                }

                ArcadeFont.drawText(renderer, posStr, 70f, rowY, 2.4f, color)
                ArcadeFont.drawText(renderer, entry.initials.take(8), 150f, rowY, 2.4f, color)
                ArcadeFont.drawText(renderer, entry.score.toString().padStart(7, '0'), 380f, rowY, 2.4f, color)
                ArcadeFont.drawText(renderer, "NV ${entry.level}", 560f, rowY, 2.4f, color)
            }
        }

        // 4. Botón Volver
        ArcadeUIHelper.drawButton(renderer, btnBack, btnBack == pressedButton, 2.8f)

        renderer.end()
    }
}
