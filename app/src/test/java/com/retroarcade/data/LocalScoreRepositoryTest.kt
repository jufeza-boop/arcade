package com.retroarcade.data

import com.retroarcade.core.GameId
import com.retroarcade.core.ScoreEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocalScoreRepositoryTest {

    private lateinit var storage: InMemoryKeyValueStorage
    private lateinit var repository: LocalScoreRepository

    @BeforeEach
    fun setUp() {
        storage = InMemoryKeyValueStorage()
        repository = LocalScoreRepository(storage, maxScoresPerGame = 10)
    }

    @Test
    @DisplayName("Lectura de récords en un juego sin datos debe retornar una lista vacía")
    fun testGetScoresEmpty() {
        val scores = repository.getScores(GameId.TETRIS)
        assertTrue(scores.isEmpty())
    }

    @Test
    @DisplayName("Guardar y recuperar puntuaciones ordenadas descendentemente")
    fun testSaveAndGetScoresSorted() {
        repository.addScore(GameId.TETRIS, ScoreEntry("AAA", 1000, 2))
        repository.addScore(GameId.TETRIS, ScoreEntry("BBB", 5000, 5))
        repository.addScore(GameId.TETRIS, ScoreEntry("CCC", 3000, 3))

        val scores = repository.getScores(GameId.TETRIS)
        assertEquals(3, scores.size)
        assertEquals(5000, scores[0].score)
        assertEquals("BBB", scores[0].initials)
        assertEquals(3000, scores[1].score)
        assertEquals("CCC", scores[1].initials)
        assertEquals(1000, scores[2].score)
        assertEquals("AAA", scores[2].initials)
    }

    @Test
    @DisplayName("Inserción de 12 puntuaciones mantiene estrictamente las 10 mayores ordenadas")
    fun testTop10CapWith12Scores() {
        // Insertamos 12 puntuaciones: 100, 200, 300, ..., 1200 en orden desordenado
        val testScores = listOf(300, 100, 800, 1200, 500, 200, 1100, 400, 900, 700, 1000, 600)
        testScores.forEachIndexed { index, score ->
            val initials = "P${index.toString().padStart(2, '0')}".take(3)
            val added = repository.addScore(GameId.ARKANOID, ScoreEntry(initials, score, 1))
            assertTrue(added || score <= 200)
        }

        val scores = repository.getScores(GameId.ARKANOID)
        // Debe haber exactamente 10 puntuaciones
        assertEquals(10, scores.size)

        // Las puntuaciones deben ser de 1200 a 300 en orden estrictamente descendente
        val expectedScores = listOf(1200, 1100, 1000, 900, 800, 700, 600, 500, 400, 300)
        assertEquals(expectedScores, scores.map { it.score })

        // Puntuaciones 100 y 200 deben haber sido descartadas
        assertFalse(scores.any { it.score == 100 })
        assertFalse(scores.any { it.score == 200 })
    }

    @Test
    @DisplayName("Intento de insertar una puntuación inferior al Top 10 lleno retorna false y no modifica la lista")
    fun testRejectScoreBelowTop10() {
        // Llenar con 10 puntuaciones altas
        for (i in 1..10) {
            repository.addScore(GameId.SNAKE, ScoreEntry("SNK", i * 1000, i))
        }

        // Intentar agregar una puntuación menor al mínimo actual (1000)
        val added = repository.addScore(GameId.SNAKE, ScoreEntry("LOW", 500, 1))
        assertFalse(added)

        val scores = repository.getScores(GameId.SNAKE)
        assertEquals(10, scores.size)
        assertEquals(1000, scores.last().score)
    }

    @Test
    @DisplayName("Las puntuaciones se almacenan y aíslan de forma independiente por GameId")
    fun testScoresSeparationByGameId() {
        repository.addScore(GameId.TETRIS, ScoreEntry("TET", 9999, 10))
        repository.addScore(GameId.PACMAN, ScoreEntry("PAC", 8888, 8))
        repository.addScore(GameId.SPACE_INVADERS, ScoreEntry("SPV", 7777, 7))

        val tetrisScores = repository.getScores(GameId.TETRIS)
        val pacmanScores = repository.getScores(GameId.PACMAN)
        val spaceScores = repository.getScores(GameId.SPACE_INVADERS)
        val arkanoidScores = repository.getScores(GameId.ARKANOID)

        assertEquals(1, tetrisScores.size)
        assertEquals(9999, tetrisScores.first().score)

        assertEquals(1, pacmanScores.size)
        assertEquals(8888, pacmanScores.first().score)

        assertEquals(1, spaceScores.size)
        assertEquals(7777, spaceScores.first().score)

        assertTrue(arkanoidScores.isEmpty())
    }

    @Test
    @DisplayName("isHighScore determina correctamente si una puntuación califica para el Top 10")
    fun testIsHighScore() {
        // Cuando la lista está vacía, cualquier puntuación > 0 es récord
        assertTrue(repository.isHighScore(GameId.PUZZLE_BOBBLE, 100))
        assertFalse(repository.isHighScore(GameId.PUZZLE_BOBBLE, 0))

        // Llenamos con 10 récords de 100 a 1000
        for (i in 1..10) {
            repository.addScore(GameId.PUZZLE_BOBBLE, ScoreEntry("BOB", i * 100, 1))
        }

        // El mínimo actual es 100
        assertFalse(repository.isHighScore(GameId.PUZZLE_BOBBLE, 50))
        assertFalse(repository.isHighScore(GameId.PUZZLE_BOBBLE, 100))
        assertTrue(repository.isHighScore(GameId.PUZZLE_BOBBLE, 101))
    }

    @Test
    @DisplayName("clearScores vacía el ranking de un juego específico sin afectar a otros")
    fun testClearScores() {
        repository.addScore(GameId.TETRIS, ScoreEntry("TET", 1234, 1))
        repository.addScore(GameId.SNAKE, ScoreEntry("SNK", 4321, 1))

        repository.clearScores(GameId.TETRIS)

        assertTrue(repository.getScores(GameId.TETRIS).isEmpty())
        assertEquals(1, repository.getScores(GameId.SNAKE).size)
    }

    @Test
    @DisplayName("ScoreEntry valida restricciones de iniciales, score y nivel")
    fun testScoreEntryValidation() {
        // Iniciales válidas
        val valid = ScoreEntry("ABC", 100, 1)
        assertEquals("ABC", valid.initials)

        // Iniciales vacías o muy largas
        assertThrows<IllegalArgumentException> { ScoreEntry("", 100, 1) }
        assertThrows<IllegalArgumentException> { ScoreEntry("   ", 100, 1) }
        assertThrows<IllegalArgumentException> { ScoreEntry("ABCD", 100, 1) }

        // Puntuación negativa
        assertThrows<IllegalArgumentException> { ScoreEntry("ABC", -1, 1) }

        // Nivel menor a 1
        assertThrows<IllegalArgumentException> { ScoreEntry("ABC", 100, 0) }
    }
}
