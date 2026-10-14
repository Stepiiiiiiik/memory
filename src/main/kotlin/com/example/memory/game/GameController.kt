package com.example.memory.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.memory.model.Card
import com.example.memory.model.EmojiDeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GameMode(val title: String) {
    SOLO("Один игрок"),
    VERSUS("Два игрока"),
}

/**
 * Holds all game state and rules. Not a ViewModel — on desktop there is no
 * Android lifecycle, so it takes the composition's [CoroutineScope] for the
 * flip-back delay and the timer. Navigation lives one level up in MemoryApp.
 *
 * Online play reuses the two-player logic: both peers run identical,
 * deterministic logic over the same shuffled deck, and only the player whose
 * turn it is may tap. Each accepted local tap is mirrored to the peer via
 * [onLocalTap]; incoming taps arrive through [onRemoteTap].
 */
class GameController(private val scope: CoroutineScope) {

    var mode by mutableStateOf(GameMode.SOLO)
        private set

    var difficulty by mutableStateOf(Difficulty.MARATHON)
        private set

    /** Current board. Observed directly by Compose. */
    val cards = mutableStateListOf<Card>()

    var moves by mutableIntStateOf(0)
        private set

    var matchedPairs by mutableIntStateOf(0)
        private set

    /** Total pairs on the current board (derived from the deck, not difficulty). */
    var totalPairs by mutableIntStateOf(0)
        private set

    var elapsedSeconds by mutableIntStateOf(0)
        private set

    var isWon by mutableStateOf(false)
        private set

    // --- Two-player / online state -----------------------------------------
    var currentPlayer by mutableIntStateOf(1)
        private set

    var player1Pairs by mutableIntStateOf(0)
        private set

    var player2Pairs by mutableIntStateOf(0)
        private set

    var player1Name by mutableStateOf("Игрок 1")
        private set

    var player2Name by mutableStateOf("Игрок 2")
        private set

    /** True for a networked game. */
    var isOnline by mutableStateOf(false)
        private set

    /** Which player this client controls online: 1 or 2. 0 for local games. */
    var myPlayer by mutableIntStateOf(0)
        private set

    /** Called with a card id whenever this client makes an accepted move. */
    var onLocalTap: ((Int) -> Unit)? = null

    val winner: Int
        get() = when {
            player1Pairs > player2Pairs -> 1
            player2Pairs > player1Pairs -> 2
            else -> 0
        }

    /** True when it is this client's turn (always true for local games). */
    val isMyTurn: Boolean
        get() = !isOnline || currentPlayer == myPlayer

    private val selected = mutableListOf<Int>()
    private var locked = false
    private var timerJob: Job? = null

    fun selectMode(m: GameMode) {
        mode = m
    }

    fun selectDifficulty(d: Difficulty) {
        difficulty = d
    }

    /** Builds a shuffled deck whose card ids equal their position (0..2N-1). */
    fun buildDeck(pairs: Int): List<Card> {
        val chosen = EmojiDeck.emojis.shuffled().take(pairs)
        val flat = chosen.flatMap { listOf(it, it) }.shuffled()
        val pairIdByEmoji = chosen.withIndex().associate { (i, e) -> e to i }
        return flat.mapIndexed { idx, emoji ->
            Card(id = idx, pairId = pairIdByEmoji.getValue(emoji), emoji = emoji)
        }
    }

    /** Start a local SOLO or VERSUS game using the current [difficulty]. */
    fun startGame() {
        isOnline = false
        myPlayer = 0
        player1Name = "Игрок 1"
        player2Name = "Игрок 2"
        setupBoard(buildDeck(difficulty.pairs))
    }

    /** Start a networked game from a shared deck. */
    fun startOnlineGame(
        deck: List<Card>,
        myPlayer: Int,
        hostName: String,
        guestName: String,
    ) {
        isOnline = true
        mode = GameMode.VERSUS
        this.myPlayer = myPlayer
        player1Name = hostName
        player2Name = guestName
        setupBoard(deck)
    }

    private fun setupBoard(deck: List<Card>) {
        cards.clear()
        cards.addAll(deck)
        totalPairs = deck.size / 2
        moves = 0
        matchedPairs = 0
        elapsedSeconds = 0
        isWon = false
        currentPlayer = 1
        player1Pairs = 0
        player2Pairs = 0
        selected.clear()
        locked = false
        startTimer()
    }

    /** A tap made by the local user. */
    fun onCardClick(cardId: Int) {
        if (locked) return
        if (isOnline && currentPlayer != myPlayer) return
        val card = cards.firstOrNull { it.id == cardId } ?: return
        if (card.isFaceUp || card.isMatched) return

        if (isOnline) onLocalTap?.invoke(cardId)
        applyTap(cardId)
    }

    /** A tap received from the networked opponent. */
    fun onRemoteTap(cardId: Int) {
        val card = cards.firstOrNull { it.id == cardId } ?: return
        if (card.isFaceUp || card.isMatched) return
        applyTap(cardId)
    }

    /** Core, deterministic rule logic shared by local and remote taps. */
    private fun applyTap(cardId: Int) {
        val index = cards.indexOfFirst { it.id == cardId }
        if (index == -1) return
        cards[index] = cards[index].copy(isFaceUp = true)
        selected.add(cardId)

        if (selected.size == 2) {
            moves++
            val first = cards.first { it.id == selected[0] }
            val second = cards.first { it.id == selected[1] }

            if (first.pairId == second.pairId) {
                setMatched(first.id)
                setMatched(second.id)
                selected.clear()
                matchedPairs++
                if (mode == GameMode.VERSUS) {
                    if (currentPlayer == 1) player1Pairs++ else player2Pairs++
                }
                if (matchedPairs == totalPairs) onWin()
            } else {
                locked = true
                val a = first.id
                val b = second.id
                scope.launch {
                    delay(800)
                    setFaceDown(a)
                    setFaceDown(b)
                    selected.clear()
                    if (mode == GameMode.VERSUS) {
                        currentPlayer = if (currentPlayer == 1) 2 else 1
                    }
                    locked = false
                }
            }
        }
    }

    private fun setMatched(id: Int) {
        val i = cards.indexOfFirst { it.id == id }
        if (i != -1) cards[i] = cards[i].copy(isMatched = true, isFaceUp = true)
    }

    private fun setFaceDown(id: Int) {
        val i = cards.indexOfFirst { it.id == id }
        if (i != -1) cards[i] = cards[i].copy(isFaceUp = false)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                if (!isWon) elapsedSeconds++
            }
        }
    }

    private fun onWin() {
        isWon = true
        timerJob?.cancel()
    }

    fun restart() = startGame()

    /** Stop timers; called when leaving a game screen. */
    fun stop() {
        timerJob?.cancel()
    }
}
