package com.retroarcade.core

interface ScoreRepository {
    fun getScores(gameId: GameId): List<ScoreEntry>
    fun addScore(gameId: GameId, entry: ScoreEntry): Boolean
    fun isHighScore(gameId: GameId, score: Int): Boolean
    fun clearScores(gameId: GameId)
}
