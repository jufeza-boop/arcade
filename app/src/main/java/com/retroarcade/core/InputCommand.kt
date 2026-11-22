package com.retroarcade.core

sealed interface InputCommand {
    // Comandos discretos de cruceta y botones (VIRTUAL_PAD)
    data object MoveLeft : InputCommand
    data object MoveRight : InputCommand
    data object MoveUp : InputCommand
    data object MoveDown : InputCommand
    data object ActionA : InputCommand
    data object ActionB : InputCommand
    data object Pause : InputCommand

    // Comandos continuos / gestuales (TOUCH_GESTURES)
    data class AimAngle(val degrees: Float) : InputCommand
    data class DragPosition(val x: Float, val y: Float) : InputCommand
    data class Swipe(val direction: SwipeDirection) : InputCommand
    data object Tap : InputCommand
    data object Release : InputCommand
}

enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}
