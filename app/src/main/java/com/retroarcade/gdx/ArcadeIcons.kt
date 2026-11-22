package com.retroarcade.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.retroarcade.core.GameId

object ArcadeIcons {

    fun drawIcon(renderer: ShapeRenderer, gameId: GameId, cx: Float, cy: Float, size: Float) {
        when (gameId) {
            GameId.TETRIS -> drawTetrisIcon(renderer, cx, cy, size)
            GameId.PUZZLE_BOBBLE -> drawPuzzleBobbleIcon(renderer, cx, cy, size)
            GameId.ARKANOID -> drawArkanoidIcon(renderer, cx, cy, size)
            GameId.SPACE_INVADERS -> drawSpaceInvadersIcon(renderer, cx, cy, size)
            GameId.PACMAN -> drawPacmanIcon(renderer, cx, cy, size)
            GameId.SNAKE -> drawSnakeIcon(renderer, cx, cy, size)
        }
    }

    private fun drawTetrisIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val blockSize = size / 3.5f
        val half = blockSize / 2f
        renderer.set(ShapeRenderer.ShapeType.Filled)

        renderer.color = Color.CYAN
        renderer.rect(cx - blockSize * 1.5f, cy - half, blockSize - 2f, blockSize - 2f)
        renderer.rect(cx - half, cy - half, blockSize - 2f, blockSize - 2f)
        renderer.rect(cx + half, cy - half, blockSize - 2f, blockSize - 2f)

        renderer.color = Color.MAGENTA
        renderer.rect(cx - half, cy + half, blockSize - 2f, blockSize - 2f)
    }

    private fun drawPuzzleBobbleIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val r = size / 4.2f
        renderer.set(ShapeRenderer.ShapeType.Filled)

        renderer.color = Color.CYAN
        renderer.circle(cx - r, cy - r * 0.7f, r)

        renderer.color = Color.YELLOW
        renderer.circle(cx + r, cy - r * 0.7f, r)

        renderer.color = Color.PINK
        renderer.circle(cx, cy + r * 0.9f, r)
    }

    private fun drawArkanoidIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val w = size * 0.9f
        val bw = w / 3.2f
        val bh = size / 6f
        renderer.set(ShapeRenderer.ShapeType.Filled)

        // Ladrillos superiores
        renderer.color = Color.RED
        renderer.rect(cx - w / 2f, cy + size / 4f, bw - 2f, bh - 2f)
        renderer.color = Color.YELLOW
        renderer.rect(cx - w / 2f + bw, cy + size / 4f, bw - 2f, bh - 2f)
        renderer.color = Color.GREEN
        renderer.rect(cx - w / 2f + bw * 2f, cy + size / 4f, bw - 2f, bh - 2f)

        // Bola
        renderer.color = Color.WHITE
        renderer.circle(cx + size / 6f, cy, size / 10f)

        // Pala inferior
        renderer.color = Color.CYAN
        renderer.rect(cx - w / 3f, cy - size / 3f, w * 0.66f, size / 8f)
    }

    private fun drawSpaceInvadersIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val p = size / 10f
        renderer.set(ShapeRenderer.ShapeType.Filled)
        renderer.color = Color.GREEN

        val pixels = listOf(
            -2 to 3, 2 to 3,
            -1 to 2, 1 to 2,
            -3 to 1, -2 to 1, -1 to 1, 0 to 1, 1 to 1, 2 to 1, 3 to 1,
            -3 to 0, -2 to 0, 0 to 0, 2 to 0, 3 to 0,
            -3 to -1, -2 to -1, -1 to -1, 0 to -1, 1 to -1, 2 to -1, 3 to -1,
            -3 to -2, 3 to -2,
            -2 to -3, 2 to -3
        )
        for ((px, py) in pixels) {
            renderer.rect(cx + px * p, cy + py * p, p - 1f, p - 1f)
        }
    }

    private fun drawPacmanIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val r = size / 2.2f
        renderer.set(ShapeRenderer.ShapeType.Filled)

        // Cuerpo de Pac-Man (círculo amarillo completo)
        renderer.color = Color.YELLOW
        renderer.circle(cx - r * 0.2f, cy, r)

        // Boca abierta (triángulo oscuro del color de fondo)
        val bgDark = Color(0.04f, 0.04f, 0.08f, 1f)
        renderer.color = bgDark
        renderer.triangle(
            cx - r * 0.2f, cy,
            cx + r * 1.0f, cy + r * 0.55f,
            cx + r * 1.0f, cy - r * 0.55f
        )

        // Punto de poder blanco
        renderer.color = Color.WHITE
        renderer.circle(cx + r * 0.8f, cy, size / 9f)
    }

    private fun drawSnakeIcon(renderer: ShapeRenderer, cx: Float, cy: Float, size: Float) {
        val s = size / 4f
        renderer.set(ShapeRenderer.ShapeType.Filled)

        renderer.color = Color.GREEN
        renderer.rect(cx - s * 1.5f, cy - s, s - 2f, s - 2f)
        renderer.rect(cx - s * 0.5f, cy - s, s - 2f, s - 2f)
        renderer.rect(cx - s * 0.5f, cy, s - 2f, s - 2f)
        renderer.rect(cx + s * 0.5f, cy, s - 2f, s - 2f)

        // Manzana roja
        renderer.color = Color.RED
        renderer.circle(cx + s * 1.2f, cy + s * 1.1f, s * 0.4f)
    }
}
