package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.retroarcade.core.ControlMode
import com.retroarcade.presentation.SettingsPresenter

class SettingsScreen(private val game: RetroArcadeGame) : ScreenAdapter() {

    private val presenter = SettingsPresenter(game.settingsRepository)
    private val buttons = mutableListOf<ArcadeButton>()
    private var pressedButton: ArcadeButton? = null
    private val touchPoint = Vector3()

    private lateinit var btnToggleControl: ArcadeButton
    private lateinit var btnToggleSound: ArcadeButton
    private val btnBack = ArcadeButton(160f, 120f, 400f, 80f, "VOLVER AL MENU", Color.CYAN) {
        game.goToMainMenu()
    }

    override fun show() {
        updateButtons()

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

    private fun updateButtons() {
        val mode = presenter.getControlMode()
        val modeLabel = if (mode == ControlMode.VIRTUAL_PAD) "MODO: PAD VIRTUAL" else "MODO: GESTOS"
        val modeColor = if (mode == ControlMode.VIRTUAL_PAD) Color.CYAN else Color.MAGENTA

        btnToggleControl = ArcadeButton(100f, 820f, 520f, 90f, modeLabel, modeColor) {
            presenter.toggleControlMode()
            updateButtons()
        }

        val muted = presenter.isMuted()
        val soundLabel = if (muted) "SONIDO: SILENCIADO" else "SONIDO: ACTIVADO"
        val soundColor = if (muted) Color.RED else Color.GREEN

        btnToggleSound = ArcadeButton(100f, 520f, 520f, 90f, soundLabel, soundColor) {
            presenter.toggleMuted()
            updateButtons()
        }

        buttons.clear()
        buttons.addAll(listOf(btnToggleControl, btnToggleSound, btnBack))
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
        ArcadeUIHelper.drawNeonBox(renderer, 50f, 1120f, 620f, 100f, Color(0.07f, 0.09f, 0.18f, 0.95f), Color.YELLOW, 3f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, "AJUSTES", 360f, 1170f, 4.5f, Color.YELLOW)

        // 2. Sección Controles
        ArcadeUIHelper.drawNeonBox(renderer, 50f, 740f, 620f, 320f, Color(0.03f, 0.05f, 0.12f, 0.95f), Color.CYAN, 2f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, "ESQUEMA DE CONTROLES", 360f, 1000f, 3.0f, Color.WHITE)

        val desc = if (presenter.getControlMode() == ControlMode.VIRTUAL_PAD) {
            "CRUCETA Y BOTONES A/B ABAJO"
        } else {
            "GESTOS TACTILES DIRECTOS"
        }
        ArcadeFont.drawCenteredText(renderer, desc, 360f, 765f, 2.1f, Color.LIGHT_GRAY)

        // 3. Sección Audio
        ArcadeUIHelper.drawNeonBox(renderer, 50f, 440f, 620f, 250f, Color(0.03f, 0.05f, 0.12f, 0.95f), Color.GREEN, 2f)
        renderer.set(ShapeRenderer.ShapeType.Filled)
        ArcadeFont.drawCenteredText(renderer, "AUDIO CHIPTUNE", 360f, 645f, 3.0f, Color.WHITE)

        val soundDesc = if (presenter.isMuted()) "EFECTOS SILENCIADOS" else "EFECTOS SONOROS ACTIVADOS"
        ArcadeFont.drawCenteredText(renderer, soundDesc, 360f, 470f, 2.1f, Color.LIGHT_GRAY)

        // 4. Botones
        for (btn in buttons) {
            ArcadeUIHelper.drawButton(renderer, btn, btn == pressedButton, 2.8f)
        }

        renderer.end()
    }
}
