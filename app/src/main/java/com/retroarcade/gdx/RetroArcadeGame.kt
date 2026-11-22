package com.retroarcade.gdx

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreRepository
import com.retroarcade.core.SettingsRepository
import com.retroarcade.data.InMemoryKeyValueStorage
import com.retroarcade.data.LocalScoreRepository
import com.retroarcade.data.LocalSettingsRepository

class RetroArcadeGame : Game() {

    companion object {
        const val VIRTUAL_WIDTH = 720f
        const val VIRTUAL_HEIGHT = 1280f
    }

    lateinit var camera: OrthographicCamera
    lateinit var viewport: FitViewport
    lateinit var shapeRenderer: ShapeRenderer

    lateinit var scoreRepository: ScoreRepository
    lateinit var settingsRepository: SettingsRepository

    override fun create() {
        try {
            camera = OrthographicCamera()
            viewport = FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera)
            shapeRenderer = ShapeRenderer()

            val storage = try {
                val prefs = Gdx.app.getPreferences("retro_arcade_prefs")
                GdxPreferencesStorage(prefs)
            } catch (t: Throwable) {
                InMemoryKeyValueStorage()
            }

            scoreRepository = LocalScoreRepository(storage)
            settingsRepository = LocalSettingsRepository(storage)

            goToMainMenu()
        } catch (t: Throwable) {
            Gdx.app.error("RetroArcade", "Error fatal en create()", t)
        }
    }

    fun goToMainMenu() {
        setScreen(MainMenuScreen(this))
    }

    fun goToHighScores(initialGame: GameId = GameId.TETRIS) {
        setScreen(HighScoresScreen(this, initialGame))
    }

    fun goToSettings() {
        setScreen(SettingsScreen(this))
    }

    fun goToGame(gameId: GameId) {
        setScreen(GamePlaceholderScreen(this, gameId))
    }

    override fun render() {
        try {
            super.render()
        } catch (t: Throwable) {
            ScreenUtils.clear(0.15f, 0.02f, 0.02f, 1f)
            try {
                viewport.apply()
                shapeRenderer.projectionMatrix = camera.combined
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                shapeRenderer.color = Color.RED
                shapeRenderer.rect(40f, 600f, 640f, 100f)
                ArcadeFont.drawCenteredText(shapeRenderer, "ERROR EN RENDER", 360f, 650f, 3.5f, Color.WHITE)
                shapeRenderer.end()
            } catch (_: Throwable) {}
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        super.resize(width, height)
    }

    override fun dispose() {
        super.dispose()
        shapeRenderer.dispose()
    }
}
