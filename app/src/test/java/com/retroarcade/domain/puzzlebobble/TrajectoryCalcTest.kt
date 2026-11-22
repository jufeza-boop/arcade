package com.retroarcade.domain.puzzlebobble

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TrajectoryCalcTest {

    @Test
    @DisplayName("Restricción del ángulo de apuntado en el rango [-75, 75]")
    fun testClampAngle() {
        assertEquals(-75f, TrajectoryCalc.clampAngle(-90f))
        assertEquals(-75f, TrajectoryCalc.clampAngle(-75f))
        assertEquals(0f, TrajectoryCalc.clampAngle(0f))
        assertEquals(45f, TrajectoryCalc.clampAngle(45f))
        assertEquals(75f, TrajectoryCalc.clampAngle(75f))
        assertEquals(75f, TrajectoryCalc.clampAngle(100f))
    }

    @Test
    @DisplayName("Dirección trigonométrica a 0 grados es vertical pura (0, 1)")
    fun testVerticalDirection() {
        val (dx, dy) = TrajectoryCalc.calculateDirection(0f)
        assertEquals(0f, dx, 0.001f)
        assertEquals(1f, dy, 0.001f)
    }

    @Test
    @DisplayName("Direcciones hacia la izquierda tienen dx negativo y hacia la derecha dx positivo")
    fun testDirectionSigns() {
        val (leftDx, leftDy) = TrajectoryCalc.calculateDirection(-45f)
        assertTrue(leftDx < 0f)
        assertTrue(leftDy > 0f)

        val (rightDx, rightDy) = TrajectoryCalc.calculateDirection(45f)
        assertTrue(rightDx > 0f)
        assertTrue(rightDy > 0f)
    }

    @Test
    @DisplayName("Línea guía de trayectoria rebota en paredes laterales")
    fun testAimGuideBounces() {
        // Disparando en diagonal derecha hacia la pared (leftWall=100, rightWall=300)
        val points = TrajectoryCalc.calculateAimGuide(
            startX = 200f,
            startY = 100f,
            angleDegrees = 60f,
            leftWall = 100f,
            rightWall = 300f,
            topCeiling = 800f
        )

        assertTrue(points.size > 5)
        // Todos los puntos deben permanecer dentro de los límites de las paredes
        for ((x, _) in points) {
            assertTrue(x >= 100f)
            assertTrue(x <= 300f)
        }
    }
}
