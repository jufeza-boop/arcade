package com.retroarcade.core

enum class GameStatus {
    RUNNING,
    PAUSED,
    GAME_OVER
}

data class EngineState(
    val score: Int = 0,
    val level: Int = 1,
    val lives: Int = 3,
    val status: GameStatus = GameStatus.RUNNING
)

interface GameEngine {
    val gameId: GameId
    val state: EngineState

    val score: Int get() = state.score
    val level: Int get() = state.level
    val lives: Int get() = state.lives
    val status: GameStatus get() = state.status

    fun update(deltaSeconds: Float)
    fun handleInput(command: InputCommand)
    fun reset()
}
