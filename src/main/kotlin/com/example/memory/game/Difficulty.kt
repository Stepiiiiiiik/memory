package com.example.memory.game

/** Board sizes. [pairs] × 2 = number of cards on the field. */
enum class Difficulty(
    val title: String,
    val pairs: Int,
) {
    RELAXED("Разминка", 12),   // 24 карточки
    CLASSIC("Классика", 24),   // 48 карточек
    MARATHON("Марафон", 40);   // 80 карточек

    val cardCount: Int get() = pairs * 2
}
