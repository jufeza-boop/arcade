package com.retroarcade.presentation

import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreEntry
import com.retroarcade.core.ScoreRepository

class HighScoresPresenter(
    private val scoreRepository: ScoreRepository,
    initialGame: GameId = GameId.TETRIS
) {
    val games: List<GameId> = GameId.entries.toList()
    var selectedIndex: Int = games.indexOf(initialGame).coerceAtLeast(0)
        private set

    val selectedGame: GameId
        get() = games[selectedIndex]

    fun selectNextGame() {
        selectedIndex = (selectedIndex + 1) % games.size
    }

    fun selectPreviousGame() {
        selectedIndex = if (selectedIndex - 1 < 0) games.size - 1 else selectedIndex - 1
    }

    fun selectGame(gameId: GameId) {
        val index = games.indexOf(gameId)
        if (index != -1) {
            selectedIndex = index
        }
    }

    fun getCurrentScores(): List<ScoreEntry> {
        return scoreRepository.getScores(selectedGame)
    }

    fun clearCurrentScores() {
        scoreRepository.clearScores(selectedGame)
    }
}
