package com.example.memory.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.memory.model.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

enum class NetStatus { BROWSING, INVITING, INCOMING }

/**
 * Orchestrates LAN multiplayer: presence discovery, a TCP server that accepts
 * invitations, outgoing invitations, and the in-game message channel.
 *
 * All Compose-observable state is updated on the composition ([scope]) thread;
 * blocking socket work runs on [Dispatchers.IO].
 */
class NetworkManager(private val scope: CoroutineScope) {

    var myNick by mutableStateOf("")
        private set

    val peers = mutableStateListOf<PeerInfo>()

    var status by mutableStateOf(NetStatus.BROWSING)
        private set

    /** Nick of the peer who invited us, while [status] == INCOMING. */
    var incomingFrom by mutableStateOf<String?>(null)
        private set

    /** Nick of the peer we invited, while [status] == INVITING. */
    var invitingTo by mutableStateOf<String?>(null)
        private set

    /** Transient banner message, e.g. "приглашение отклонено". */
    var notice by mutableStateOf<String?>(null)
        private set

    // Event callbacks, always invoked on the composition thread.
    var onGameStart: ((deck: List<Card>, myPlayer: Int, hostNick: String, guestNick: String) -> Unit)? = null
    var onTap: ((Int) -> Unit)? = null
    var onOpponentLeft: (() -> Unit)? = null

    /** Supplies a freshly shuffled deck for a game we host. Wired by the app. */
    var deckProvider: () -> List<Card> = { emptyList() }

    private val myId: String = UUID.randomUUID().toString()
    private var tcpPort: Int = 0
    private var serverSocket: ServerSocket? = null
    private var discovery: Discovery? = null
    private val jobs = mutableListOf<Job>()

    /** Set while inviting or while an invite is pending our answer (pre-game). */
    private var pending: GameConnection? = null

    /** The live in-game channel. */
    private var active: GameConnection? = null

    private fun available(): Boolean = active == null && status == NetStatus.BROWSING

    private fun main(block: () -> Unit) {
        scope.launch { block() }
    }

    // --- Lifecycle ----------------------------------------------------------

    fun start(nick: String) {
        myNick = Protocol.sanitize(nick).ifBlank { "Игрок" }
        status = NetStatus.BROWSING
        peers.clear()
        notice = null

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(0))
        serverSocket = server
        tcpPort = server.localPort

        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                handleIncoming(socket)
            }
        }

        discovery = Discovery(
            scope = scope,
            myId = myId,
            myNick = myNick,
            myTcpPort = tcpPort,
            isAvailable = { available() },
            onSeen = { id, nick2, host, port -> main { upsertPeer(id, nick2, host, port) } },
        ).also { it.start() }

        jobs += scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                peers.removeAll { now - it.lastSeen > 5000 }
                delay(1000)
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        discovery?.stop()
        discovery = null
        pending?.close(); pending = null
        active?.close(); active = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        peers.clear()
        status = NetStatus.BROWSING
        incomingFrom = null
        invitingTo = null
    }

    private fun upsertPeer(id: String, nick: String, host: String, port: Int) {
        val now = System.currentTimeMillis()
        val i = peers.indexOfFirst { it.id == id }
        val info = PeerInfo(id, nick, host, port, now)
        if (i == -1) peers.add(info) else peers[i] = info
    }

    // --- Outgoing invitation ------------------------------------------------

    fun invite(peer: PeerInfo) {
        if (status != NetStatus.BROWSING) return
        status = NetStatus.INVITING
        invitingTo = peer.nick
        notice = null

        scope.launch(Dispatchers.IO) {
            val socket = try {
                Socket().apply { connect(InetSocketAddress(peer.host, peer.port), 4000) }
            } catch (_: Exception) {
                main { resetToBrowsing("Не удалось связаться с игроком") }
                return@launch
            }
            val conn = GameConnection(socket)
            pending = conn
            conn.send("${Protocol.HELLO}|$myNick|$myId")
            readLoop(conn, isHost = true, peerNick = peer.nick)
        }
    }

    fun cancelInvite() {
        pending?.close()
        pending = null
        resetToBrowsing(null)
    }

    // --- Incoming invitation ------------------------------------------------

    private fun handleIncoming(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val conn = GameConnection(socket)
            val first = conn.readLine()
            val parts = first?.split("|") ?: emptyList()
            if (parts.size < 3 || parts[0] != Protocol.HELLO) {
                conn.close()
                return@launch
            }
            val nick = parts[1]
            val free = available() && pending == null
            if (!free) {
                conn.send(Protocol.BUSY)
                conn.close()
                return@launch
            }
            pending = conn
            main {
                incomingFrom = nick
                status = NetStatus.INCOMING
                notice = null
            }
            // Keep reading: START arrives after we accept, or EOF if they cancel.
            readLoop(conn, isHost = false, peerNick = nick)
        }
    }

    fun acceptIncoming() {
        pending?.send(Protocol.ACCEPT)
        // Game begins when the host's START message arrives (handled in readLoop).
    }

    fun declineIncoming() {
        pending?.send(Protocol.DECLINE)
        pending?.close()
        pending = null
        resetToBrowsing(null)
    }

    // --- In-game ------------------------------------------------------------

    fun sendTap(cardId: Int) {
        active?.send("${Protocol.TAP}|$cardId")
    }

    fun leaveGame() {
        active?.send(Protocol.BYE)
        active?.close()
        active = null
        resetToBrowsing(null)
    }

    // --- Message loop (runs on an IO thread) --------------------------------

    private fun readLoop(conn: GameConnection, isHost: Boolean, peerNick: String) {
        while (true) {
            val line = conn.readLine()
            if (line == null) {
                main { onConnectionClosed(conn) }
                return
            }
            val sep = line.indexOf('|')
            val type = if (sep == -1) line else line.substring(0, sep)
            val arg = if (sep == -1) "" else line.substring(sep + 1)

            when (type) {
                Protocol.ACCEPT -> if (isHost) {
                    main { startAsHost(conn, peerNick) }
                }

                Protocol.DECLINE -> if (isHost) {
                    conn.close()
                    main { resetToBrowsing("$peerNick отклонил приглашение") }
                    return
                }

                Protocol.BUSY -> if (isHost) {
                    conn.close()
                    main { resetToBrowsing("$peerNick сейчас занят") }
                    return
                }

                Protocol.START -> if (!isHost) {
                    val p = arg.split("|", limit = 3)
                    if (p.size == 3) {
                        val hostNick = p[0]
                        val guestNick = p[1]
                        val deck = Protocol.decodeDeck(p[2])
                        active = conn
                        main {
                            status = NetStatus.BROWSING
                            incomingFrom = null
                            onGameStart?.invoke(deck, 2, hostNick, guestNick)
                        }
                    }
                }

                Protocol.TAP -> {
                    val id = arg.toIntOrNull()
                    if (id != null) main { onTap?.invoke(id) }
                }

                Protocol.BYE -> {
                    conn.close()
                    main {
                        if (active === conn) {
                            active = null
                            onOpponentLeft?.invoke()
                            resetToBrowsing("Соперник вышел из игры")
                        } else {
                            resetToBrowsing(null)
                        }
                    }
                    return
                }
            }
        }
    }

    /** Composition thread: host builds and shares the deck, then starts. */
    private fun startAsHost(conn: GameConnection, guestNick: String) {
        active = conn
        invitingTo = null
        status = NetStatus.BROWSING
        val deck = deckProvider()
        conn.send("${Protocol.START}|$myNick|$guestNick|${Protocol.encodeDeck(deck)}")
        onGameStart?.invoke(deck, 1, myNick, guestNick)
    }

    private fun onConnectionClosed(conn: GameConnection) {
        if (conn === active) {
            active = null
            onOpponentLeft?.invoke()
            resetToBrowsing("Соединение с соперником потеряно")
        } else if (conn === pending) {
            pending = null
            resetToBrowsing(null)
        }
    }

    private fun resetToBrowsing(message: String?) {
        pending = null
        incomingFrom = null
        invitingTo = null
        status = NetStatus.BROWSING
        if (message != null) notice = message
    }

    fun clearNotice() {
        notice = null
    }
}
