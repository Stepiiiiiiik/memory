package com.example.memory.net

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * Thin wrapper around a TCP [Socket] that reads and writes newline-terminated
 * UTF-8 messages. All failures are swallowed into null / no-ops so the caller
 * can treat a dead connection uniformly.
 */
class GameConnection(private val socket: Socket) {

    private val writer: BufferedWriter =
        BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader: BufferedReader =
        BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

    val remoteHost: String get() = socket.inetAddress?.hostAddress ?: "?"

    fun send(message: String) {
        try {
            synchronized(writer) {
                writer.write(message)
                writer.write("\n")
                writer.flush()
            }
        } catch (_: Exception) {
            // Connection gone; the read loop will observe EOF and clean up.
        }
    }

    /** Blocks until a line arrives; returns null when the peer disconnects. */
    fun readLine(): String? = try {
        reader.readLine()
    } catch (_: Exception) {
        null
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
