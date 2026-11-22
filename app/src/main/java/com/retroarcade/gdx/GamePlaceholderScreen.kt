package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.ControlMode
import com.retroarcade.core.GameId

class GamePlaceholderScreen(
    private val game: RetroArcadeGame,
    private val gameId: GameId
) : ScreenAdapter() {

    private val touchPoint = Vector3()
    private var isBackPressed = false
    private val btnBack = ArcadeButton(160f, 150f, 400f, 85f, "VOLVER AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    override fun show() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                isBackPressed = btnBack.contains(touchPoint.x, touchPoint.y)
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
                game.viewport.unproject(touchPoint)
                if (isBackPressed && btnBack.contains(touchPoint.x, touchPoint.y)) {
                    btnBack.onClick()
                }
                isBackPressed = false
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

        // 1. Cabecera con Nombre del Juego
        ArcadeUIHelper.drawNeonBox(renderer, 50f, 1100f, 620f, 120f, Color(0.07f, 0.09f, 0.18f, 0.95f), Color.GREEN, 3f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, gameId.displayName, 360f, 1160f, 4.5f, Color.GREEN)

        // 2. Tarjeta Central con Icono Grande
        val cardX = 100f
        val cardY = 320f
        val cardW = 520f
        val cardH = 720f
        ArcadeUIHelper.drawNeonBox(renderer, cardX, cardY, cardW, cardH, Color(0.03f, 0.05f, 0.12f, 0.95f), Color.CYAN, 3f)

        // Icono procedural
        ArcadeIcons.drawIcon(renderer, gameId, 360f, 750f, 260f)

        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, "CABINA PREPARADA", 360f, 520f, 3.2f, Color.YELLOW)

        val controlMode = game.settingsRepository.getControlMode()
        val modeStr = if (controlMode == ControlMode.VIRTUAL_PAD) "PAD VIRTUAL" else "GESTOS TACTILES"
        ArcadeFont.drawCenteredText(renderer, "CONTROL: $modeStr", 360f, 450f, 2.4f, Color.CYAN)
        ArcadeFont.drawCenteredText(renderer, "PROXIMA FASE: MOTOR DE JUEGO", 360f, 390f, 2.0f, Color.LIGHT_GRAY)

        // 3. Botón Volver
        ArcadeUIHelper.drawButton(renderer, btnBack, isBackPressed, 2.8f)

        renderer.end()
    }
}
