package com.retroarcade.presentation

import com.retroarcade.core.ControlMode
import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreRepository
import com.retroarcade.core.SettingsRepository

class MainMenuPresenter(
    private val scoreRepository: ScoreRepository,
    private val settingsRepository: SettingsRepository
) {
    val games: List<GameId> = GameId.entries.toList()
    var selectedIndex: Int = 0
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

    fun getHighScore(gameId: GameId): Int {
        return scoreRepository.getScores(gameId).firstOrNull()?.score ?: 0
    }

    fun getControlMode(): ControlMode {
        return settingsRepository.getControlMode()
    }

    fun isMuted(): Boolean {
        return settingsRepository.isMuted()
    }
}
