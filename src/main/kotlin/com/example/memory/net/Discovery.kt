package com.example.memory.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

/**
 * LAN presence discovery over UDP multicast. Each running app periodically
 * announces itself to a multicast group and listens for others' announcements.
 * Multicast (rather than broadcast) lets several instances on one machine all
 * receive each other's packets, which also makes local two-window testing work.
 *
 * @param onSeen called (on an IO thread) each time a peer announcement arrives
 */
class Discovery(
    private val scope: CoroutineScope,
    private val myId: String,
    private val myNick: String,
    private val myTcpPort: Int,
    private val isAvailable: () -> Boolean,
    private val onSeen: (id: String, nick: String, host: String, port: Int) -> Unit,
) {
    private var socket: MulticastSocket? = null
    private val jobs = mutableListOf<Job>()

    fun start() {
        val group = InetAddress.getByName(Protocol.MULTICAST_GROUP)
        val sock = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(Protocol.DISCOVERY_PORT))
            @Suppress("DEPRECATION")
            joinGroup(group)
        }
        socket = sock

        // Announce presence while available.
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isAvailable()) {
                    val msg = "${Protocol.PRESENCE_PREFIX}|$myId|$myNick|$myTcpPort"
                    val bytes = msg.toByteArray(Charsets.UTF_8)
                    runCatching {
                        sock.send(DatagramPacket(bytes, bytes.size, group, Protocol.DISCOVERY_PORT))
                    }
                }
                delay(1500)
            }
        }

        // Listen for others.
        jobs += scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1024)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(packet)
                } catch (_: Exception) {
                    ensureActive()
                    continue
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val parts = text.split("|")
                if (parts.size == 4 && parts[0] == Protocol.PRESENCE_PREFIX) {
                    val id = parts[1]
                    if (id == myId) continue // ignore ourselves
                    val nick = parts[2]
                    val port = parts[3].toIntOrNull() ?: continue
                    val host = packet.address?.hostAddress ?: continue
                    onSeen(id, nick, host, port)
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { socket?.close() }
        socket = null
    }
}
