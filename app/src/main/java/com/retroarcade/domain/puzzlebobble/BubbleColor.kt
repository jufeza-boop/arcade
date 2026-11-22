package com.retroarcade.domain.puzzlebobble

enum class BubbleColor(val id: Int) {
    NONE(0),
    CYAN(1),
    YELLOW(2),
    MAGENTA(3),
    GREEN(4),
    RED(5),
    BLUE(6);

    companion object {
        val PLAYABLE_COLORS = entries.filter { it != NONE }
    }
}
