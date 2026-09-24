package com.threepatti.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Everything the host needs to save to resume a table later. */
@Serializable
data class HostSnapshot(
    val state: GameState,
    /** Device id to player id, so a phone that reconnects gets its seat back. Never sent to players. */
    val devices: Map<String, String> = emptyMap(),
)

/**
 * The single source of truth for a table, owned by the host device. Thread safe.
 * [onSnapshot] is called after every change so the host can save the table.
 */
class TableHost(
    snapshot: HostSnapshot,
    private val onSnapshot: (HostSnapshot) -> Unit = {},
) {
    private val lock = Any()
    private var devices: Map<String, String> = snapshot.devices
    private val undoStack = ArrayDeque<UndoEntry>()

    private val _state = MutableStateFlow(markRemotePlayersOffline(snapshot.state))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    val hostPlayerId: String = snapshot.state.players.first { it.isHost }.id

    /** Applies [action] and returns an error message when it is not allowed. */
    fun perform(action: GameAction, actor: Actor = Actor.Host): String? {
        synchronized(lock) {
            val before = _state.value
            val after = try {
                GameEngine.apply(before, action, actor)
            } catch (e: GameRuleException) {
                return e.message ?: "That move is not allowed"
            }
            val lastSeq = before.log.lastOrNull()?.seq ?: 0
            val text = after.log.firstOrNull { it.seq > lastSeq }?.text
            undoStack.addLast(UndoEntry(before, text))
            while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            publish(after)
            return null
        }
    }

    fun undo(): String? {
        synchronized(lock) {
            val previous = undoStack.removeLastOrNull() ?: return "Nothing to undo"
            publish(GameEngine.restoreForUndo(previous.state, _state.value, previous.text))
            return null
        }
    }

    /** Seats the phone with [deviceId], or gives it back its old seat. Returns the player id. */
    fun join(deviceId: String, name: String): String {
        synchronized(lock) {
            val current = _state.value
            devices[deviceId]?.takeIf { current.player(it) != null }?.let { return it }
            val (next, id) = GameEngine.addPlayer(current, name, hasDevice = true)
            devices = devices + (deviceId to id)
            publish(next)
            return id
        }
    }

    /** The seat of the phone with [deviceId], or null if it hasn't joined or was removed. */
    fun playerIdFor(deviceId: String): String? {
        synchronized(lock) {
            return devices[deviceId]?.takeIf { _state.value.player(it) != null }
        }
    }

    fun setConnected(playerId: String, connected: Boolean) {
        synchronized(lock) {
            val current = _state.value
            val next = GameEngine.setConnected(current, playerId, connected)
            if (next !== current) publish(next)
        }
    }

    fun snapshot(): HostSnapshot = synchronized(lock) { HostSnapshot(_state.value, devices) }

    private fun publish(next: GameState) {
        _state.value = next
        _canUndo.value = undoStack.isNotEmpty()
        onSnapshot(HostSnapshot(next, devices))
    }

    private fun markRemotePlayersOffline(state: GameState): GameState = state.copy(
        players = state.players.map { if (it.isHost) it.copy(connected = true) else it.copy(connected = false) },
    )

    /** The table before an action, and the log line that action wrote. */
    private class UndoEntry(val state: GameState, val text: String?)

    companion object {
        const val MAX_UNDO = 50
    }
}
