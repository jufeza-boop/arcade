package com.retroarcade.core

import kotlinx.serialization.Serializable

@Serializable
data class ScoreEntry(
    val initials: String,
    val score: Int,
    val level: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<ScoreEntry> {

    init {
        val trimmed = initials.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 3) {
            "Las iniciales deben tener entre 1 y 3 caracteres"
        }
        require(score >= 0) {
            "La puntuación no puede ser negativa"
        }
        require(level >= 1) {
            "El nivel debe ser mayor o igual a 1"
        }
    }

    override fun compareTo(other: ScoreEntry): Int {
        val scoreDiff = other.score.compareTo(this.score)
        if (scoreDiff != 0) return scoreDiff
        return other.timestamp.compareTo(this.timestamp)
    }
}
