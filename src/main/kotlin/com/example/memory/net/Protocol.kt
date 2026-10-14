package com.example.memory.net

import com.example.memory.model.Card

/**
 * Tiny line-based wire protocol. Messages are single '\n'-terminated lines of
 * the form TYPE|arg|arg... Deck payloads use ',' between cards and ':' inside
 * a card, so they never contain '|' or '\n'.
 */
object Protocol {

    // UDP multicast discovery.
    const val MULTICAST_GROUP = "239.255.42.99"
    const val DISCOVERY_PORT = 45777
    const val PRESENCE_PREFIX = "MM" // MM|id|nick|tcpPort

    // TCP message types.
    const val HELLO = "HELLO"     // HELLO|nick|id        (inviter -> invitee)
    const val ACCEPT = "ACCEPT"   // invitee -> inviter
    const val DECLINE = "DECLINE" // invitee -> inviter
    const val BUSY = "BUSY"       // invitee -> inviter (already engaged)
    const val START = "START"     // START|hostNick|guestNick|deck   (host -> guest)
    const val TAP = "TAP"         // TAP|cardId  (either direction)
    const val BYE = "BYE"         // graceful disconnect

    /** Strip characters that would break the line protocol. */
    fun sanitize(text: String): String =
        text.replace("|", " ").replace("\n", " ").replace("\r", " ").trim()

    fun encodeDeck(cards: List<Card>): String =
        cards.joinToString(",") { "${it.pairId}:${it.emoji}" }

    fun decodeDeck(payload: String): List<Card> =
        payload.split(",").mapIndexed { idx, token ->
            val sep = token.indexOf(':')
            val pairId = token.substring(0, sep).toInt()
            val emoji = token.substring(sep + 1)
            Card(id = idx, pairId = pairId, emoji = emoji)
        }
}
