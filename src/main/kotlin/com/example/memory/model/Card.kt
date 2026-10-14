package com.example.memory.model

/**
 * One card on the board.
 *
 * @param id      unique per physical card (two cards of a pair have different ids)
 * @param pairId  shared by the two cards that form a matching pair
 * @param emoji   the symbol drawn on the face
 */
data class Card(
    val id: Int,
    val pairId: Int,
    val emoji: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false,
)
