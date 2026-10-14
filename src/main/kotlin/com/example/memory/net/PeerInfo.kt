package com.example.memory.net

/** A player discovered on the local network and currently available to play. */
data class PeerInfo(
    val id: String,
    val nick: String,
    val host: String,
    val port: Int,
    val lastSeen: Long,
)
