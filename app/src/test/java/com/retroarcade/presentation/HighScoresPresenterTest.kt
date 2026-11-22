package com.retroarcade.presentation

import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreEntry
import com.retroarcade.data.InMemoryKeyValueStorage
import com.retroarcade.data.LocalScoreRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HighScoresPresenterTest {

    private lateinit var scoreRepo: LocalScoreRepository
    private lateinit var presenter: HighScoresPresenter

    @BeforeEach
    fun setUp() {
        val storage = InMemoryKeyValueStorage()
        scoreRepo = LocalScoreRepository(storage)
        presenter = HighScoresPresenter(scoreRepo, initialGame = GameId.TETRIS)
    }

    @Test
    @DisplayName("Cambio de juego seleccionado en la pantalla de récords")
    fun testChangeGame() {
        assertEquals(GameId.TETRIS, presenter.selectedGame)
        presenter.selectNextGame()
        assertEquals(GameId.PUZZLE_BOBBLE, presenter.selectedGame)

        presenter.selectGame(GameId.SPACE_INVADERS)
        assertEquals(GameId.SPACE_INVADERS, presenter.selectedGame)
    }

    @Test
    @DisplayName("Obtención de lista Top 10 para el juego actual")
    fun testGetScoresForSelectedGame() {
        assertTrue(presenter.getCurrentScores().isEmpty())

        scoreRepo.addScore(GameId.TETRIS, ScoreEntry("TET", 5000, 3))
        scoreRepo.addScore(GameId.SNAKE, ScoreEntry("SNK", 2000, 1))

        assertEquals(1, presenter.getCurrentScores().size)
        assertEquals(5000, presenter.getCurrentScores().first().score)

        presenter.selectGame(GameId.SNAKE)
        assertEquals(1, presenter.getCurrentScores().size)
        assertEquals(2000, presenter.getCurrentScores().first().score)
    }

    @Test
    @DisplayName("Limpiar puntuaciones del juego seleccionado")
    fun testClearScores() {
        scoreRepo.addScore(GameId.TETRIS, ScoreEntry("TET", 5000, 3))
        presenter.clearCurrentScores()
        assertTrue(presenter.getCurrentScores().isEmpty())
    }
}
