package com.retroarcade.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

data class ArcadeButton(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val label: String,
    val color: Color = Color.CYAN,
    val onClick: () -> Unit
) {
    fun contains(tx: Float, ty: Float): Boolean {
        return tx in x..(x + width) && ty in y..(y + height)
    }
}

object ArcadeUIHelper {

    /**
     * Dibuja una caja neón exclusivamente con ShapeType.Filled (sin llamadas a ShapeType.Line).
     * Dibuja un rectángulo exterior con el color del borde y un rectángulo interior con el fondo.
     */
    fun drawNeonBox(
        renderer: ShapeRenderer,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fillColor: Color = Color(0.06f, 0.08f, 0.15f, 0.95f),
        borderColor: Color = Color.CYAN,
        borderWidth: Float = 4f
    ) {
        // Rectángulo exterior del color del borde neón
        renderer.color = borderColor
        renderer.rect(x, y, w, h)

        // Rectángulo interior con el fondo oscuro
        renderer.color = fillColor
        renderer.rect(
            x + borderWidth,
            y + borderWidth,
            w - borderWidth * 2f,
            h - borderWidth * 2f
        )
    }

    fun drawButton(
        renderer: ShapeRenderer,
        button: ArcadeButton,
        isPressed: Boolean = false,
        pixelSize: Float = 3f
    ) {
        val bg = if (isPressed) button.color.cpy().mul(0.35f) else Color(0.08f, 0.1f, 0.2f, 0.95f)
        drawNeonBox(renderer, button.x, button.y, button.width, button.height, bg, button.color, 4f)

        val textY = button.y + button.height / 2f
        val textColor = if (isPressed) Color.WHITE else button.color
        ArcadeFont.drawCenteredText(renderer, button.label, button.x + button.width / 2f, textY, pixelSize, textColor)
    }
}
