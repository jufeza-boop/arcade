package com.retroarcade.presentation

import com.retroarcade.core.ControlMode
import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreEntry
import com.retroarcade.data.InMemoryKeyValueStorage
import com.retroarcade.data.LocalScoreRepository
import com.retroarcade.data.LocalSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MainMenuPresenterTest {

    private lateinit var scoreRepo: LocalScoreRepository
    private lateinit var settingsRepo: LocalSettingsRepository
    private lateinit var presenter: MainMenuPresenter

    @BeforeEach
    fun setUp() {
        val storage = InMemoryKeyValueStorage()
        scoreRepo = LocalScoreRepository(storage)
        settingsRepo = LocalSettingsRepository(storage)
        presenter = MainMenuPresenter(scoreRepo, settingsRepo)
    }

    @Test
    @DisplayName("El juego seleccionado por defecto debe ser TETRIS")
    fun testDefaultSelectedGame() {
        assertEquals(GameId.TETRIS, presenter.selectedGame)
    }

    @Test
    @DisplayName("Navegación circular hacia adelante y atrás en la lista de juegos")
    fun testCycleGames() {
        // Inicial: TETRIS (índice 0)
        presenter.selectNextGame()
        assertEquals(GameId.PUZZLE_BOBBLE, presenter.selectedGame)

        presenter.selectNextGame()
        assertEquals(GameId.ARKANOID, presenter.selectedGame)

        // Retroceder
        presenter.selectPreviousGame()
        assertEquals(GameId.PUZZLE_BOBBLE, presenter.selectedGame)

        presenter.selectPreviousGame()
        assertEquals(GameId.TETRIS, presenter.selectedGame)

        // Retroceder desde el primero debe dar la vuelta al último (SNAKE)
        presenter.selectPreviousGame()
        assertEquals(GameId.SNAKE, presenter.selectedGame)

        // Avanzar desde el último debe dar la vuelta al primero (TETRIS)
        presenter.selectNextGame()
        assertEquals(GameId.TETRIS, presenter.selectedGame)
    }

    @Test
    @DisplayName("Selección directa de un juego")
    fun testSelectGameDirectly() {
        presenter.selectGame(GameId.PACMAN)
        assertEquals(GameId.PACMAN, presenter.selectedGame)
    }

    @Test
    @DisplayName("Obtener récord actual del juego seleccionado")
    fun testGetHighScore() {
        assertEquals(0, presenter.getHighScore(GameId.TETRIS))

        scoreRepo.addScore(GameId.TETRIS, ScoreEntry("AAA", 15000, 8))
        scoreRepo.addScore(GameId.TETRIS, ScoreEntry("BBB", 8000, 4))

        assertEquals(15000, presenter.getHighScore(GameId.TETRIS))
    }

    @Test
    @DisplayName("Reflejar ajustes de modo de control y silenciado")
    fun testSettingsReflection() {
        assertEquals(ControlMode.VIRTUAL_PAD, presenter.getControlMode())
        assertFalse(presenter.isMuted())

        settingsRepo.setControlMode(ControlMode.TOUCH_GESTURES)
        settingsRepo.setMuted(true)

        assertEquals(ControlMode.TOUCH_GESTURES, presenter.getControlMode())
        assertTrue(presenter.isMuted())
    }
}
