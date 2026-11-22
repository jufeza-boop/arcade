package com.retroarcade.domain.puzzlebobble

import com.retroarcade.core.GameStatus
import com.retroarcade.core.InputCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PuzzleBobbleEngineTest {

    private lateinit var engine: PuzzleBobbleEngine

    @BeforeEach
    fun setUp() {
        engine = PuzzleBobbleEngine(Random(9999))
    }

    @Test
    @DisplayName("Inicialización: cañón apuntando al centro (0°), estado RUNNING y burbujas en el tablero")
    fun testInitialSetup() {
        assertEquals(GameStatus.RUNNING, engine.status)
        assertEquals(0f, engine.aimAngle)
        assertNotNull(engine.currentBubble)
        assertNotNull(engine.nextBubble)
        assertFalse(engine.grid.isEmpty())
        assertNull(engine.flyingBubble)
        assertEquals(engine.initialCeilingY, engine.currentCeilingY)
    }

    @Test
    @DisplayName("Rotación del cañón restringida entre -75 y +75 grados")
    fun testCannonRotationLimits() {
        for (i in 0..40) {
            engine.handleInput(InputCommand.MoveLeft)
        }
        assertEquals(-75f, engine.aimAngle)

        for (i in 0..80) {
            engine.handleInput(InputCommand.MoveRight)
        }
        assertEquals(75f, engine.aimAngle)

        engine.handleInput(InputCommand.AimAngle(-30f))
        assertEquals(-30f, engine.aimAngle)
    }

    @Test
    @DisplayName("Disparo lanza una burbuja voladora con velocidad positiva")
    fun testShootCreatesFlyingBubble() {
        assertNull(engine.flyingBubble)

        val fired = engine.shoot()
        assertTrue(fired)
        assertNotNull(engine.flyingBubble)

        val bubble = engine.flyingBubble!!
        assertEquals(engine.cannonX, bubble.x)
        assertEquals(engine.cannonY, bubble.y)
        assertTrue(bubble.vy > 0f)

        assertFalse(engine.shoot())
    }

    @Test
    @DisplayName("Limpiar todas las burbujas activa la victoria de nivel y avanza al nivel 2")
    fun testLevelClearProgression() {
        // Vaciamos el tablero manualmente salvo un cluster de 2 burbujas del mismo color que la actual
        engine.grid.clear()
        engine.grid.setBubble(0, 0, engine.currentBubble)
        engine.grid.setBubble(0, 1, engine.currentBubble)

        // Disparamos hacia ellas para completar el trío y vaciar el tablero
        engine.setAim(-45f)
        engine.shoot()

        // Simular frames hasta que impacte
        for (i in 0..80) {
            engine.update(0.016f)
            if (engine.isLevelClearing) break
        }

        assertTrue(engine.isLevelClearing || engine.grid.isEmpty())
        assertTrue(engine.score >= 1000)

        // Avanzar el temporizador de victoria para que cargue el siguiente nivel
        engine.update(2.0f)
        assertEquals(2, engine.level)
        assertFalse(engine.grid.isEmpty()) // Nuevo nivel cargado con burbujas
    }

    @Test
    @DisplayName("El techo desciende tras X disparos y empuja las burbujas hacia abajo")
    fun testCeilingDropProgression() {
        val initialY = engine.currentCeilingY

        // Forzar descenso de techo
        engine.triggerCeilingDrop()
        assertTrue(engine.targetCeilingY < initialY)

        // Simular actualización para comprobar el descenso suave
        engine.update(0.5f)
        assertTrue(engine.currentCeilingY < initialY)
    }

    @Test
    @DisplayName("Game Over cuando las burbujas rebasan la línea de peligro inferior")
    fun testGameOverWhenDeadlineCrossed() {
        // Colocar una burbuja cerca del fondo rebasando el deadline
        engine.grid.setBubble(engine.grid.maxRows - 1, 0, BubbleColor.RED)

        // Forzar descenso del techo para empujarla más abajo
        for (i in 0..5) {
            engine.triggerCeilingDrop()
        }
        engine.update(5.0f)

        assertEquals(GameStatus.GAME_OVER, engine.status)
    }

    @Test
    @DisplayName("Reset reinicia nivel, estado, puntuación y posición del techo")
    fun testReset() {
        engine.shoot()
        engine.reset()

        assertEquals(0, engine.score)
        assertEquals(1, engine.level)
        assertEquals(GameStatus.RUNNING, engine.status)
        assertNull(engine.flyingBubble)
        assertEquals(0f, engine.aimAngle)
        assertEquals(engine.initialCeilingY, engine.currentCeilingY)
    }
}
