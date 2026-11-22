package com.retroarcade.domain.puzzlebobble

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cálculos trigonométricos de la trayectoria de disparo y rebotes en paredes laterales.
 */
object TrajectoryCalc {

    const val MIN_ANGLE_DEG = -75f
    const val MAX_ANGLE_DEG = 75f

    fun clampAngle(angleDegrees: Float): Float {
        return angleDegrees.coerceIn(MIN_ANGLE_DEG, MAX_ANGLE_DEG)
    }

    /**
     * Calcula el vector unitario de dirección según el ángulo en grados.
     * 0° apunta verticalmente hacia arriba (dx = 0, dy = 1).
     * Ángulos negativos apuntan hacia la izquierda, positivos hacia la derecha.
     */
    fun calculateDirection(angleDegrees: Float): Pair<Float, Float> {
        val clamped = clampAngle(angleDegrees)
        // Convertimos a ángulo estándar trigonométrico: 90° - aimAngle
        val rad = (90f - clamped) * (PI.toFloat() / 180f)
        val dx = cos(rad)
        val dy = sin(rad)
        return dx to dy
    }

    /**
     * Simula la trayectoria de la línea guía de apuntado con rebotes en paredes laterales.
     * Retorna una lista de puntos (x, y) de la trayectoria.
     */
    fun calculateAimGuide(
        startX: Float,
        startY: Float,
        angleDegrees: Float,
        leftWall: Float,
        rightWall: Float,
        topCeiling: Float,
        stepDistance: Float = 25f,
        maxPoints: Int = 30
    ): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        var curX = startX
        var curY = startY
        var (dirX, dirY) = calculateDirection(angleDegrees)

        points.add(curX to curY)

        for (i in 1 until maxPoints) {
            curX += dirX * stepDistance
            curY += dirY * stepDistance

            // Rebote en pared izquierda
            if (curX <= leftWall) {
                curX = leftWall + (leftWall - curX)
                dirX = -dirX
            }
            // Rebote en pared derecha
            else if (curX >= rightWall) {
                curX = rightWall - (curX - rightWall)
                dirX = -dirX
            }

            points.add(curX to curY)

            // Llegó al techo
            if (curY >= topCeiling) break
        }

        return points
    }
}
