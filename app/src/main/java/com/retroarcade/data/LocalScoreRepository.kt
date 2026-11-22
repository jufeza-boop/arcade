package com.retroarcade.data

import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreEntry
import com.retroarcade.core.ScoreRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalScoreRepository(
    private val storage: KeyValueStorage,
    private val maxScoresPerGame: Int = MAX_SCORES
) : ScoreRepository {

    companion object {
        const val MAX_SCORES = 10
        private const val KEY_PREFIX = "scores_"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getScores(gameId: GameId): List<ScoreEntry> {
        val raw = storage.getString(storageKey(gameId), "")
        if (raw.isBlank()) return emptyList()

        return try {
            val list = json.decodeFromString<List<ScoreEntry>>(raw)
            list.sorted().take(maxScoresPerGame)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun addScore(gameId: GameId, entry: ScoreEntry): Boolean {
        val currentScores = getScores(gameId).toMutableList()

        // Si ya hay 10 y la nueva puntuación es menor o igual a la última, no califica
        if (currentScores.size >= maxScoresPerGame && entry.score <= currentScores.last().score) {
            return false
        }

        currentScores.add(entry)
        val sortedTop = currentScores.sorted().take(maxScoresPerGame)

        val serialized = json.encodeToString(sortedTop)
        storage.putString(storageKey(gameId), serialized)
        return true
    }

    override fun isHighScore(gameId: GameId, score: Int): Boolean {
        if (score <= 0) return false
        val currentScores = getScores(gameId)
        if (currentScores.size < maxScoresPerGame) return true
        return score > currentScores.last().score
    }

    override fun clearScores(gameId: GameId) {
        storage.remove(storageKey(gameId))
    }

    private fun storageKey(gameId: GameId): String = "$KEY_PREFIX${gameId.name.lowercase()}"
}
