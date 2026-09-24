package com.threepatti.tracker.ui

import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import com.threepatti.core.net.ConnectionStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What the game screen needs from a table, whether this phone is the host or a player. */
interface TableSession {
    val isHost: Boolean

    /** Latest table. Null until a player's phone has received it from the host. */
    val state: StateFlow<GameState?>
    val myPlayerId: StateFlow<String?>
    val connection: StateFlow<ConnectionStatus>
    val canUndo: StateFlow<Boolean>

    /** What undo would reverse next (host only), shown before the host confirms. */
    val nextUndo: StateFlow<String?>

    /** Short messages to show the user, such as a refused move. */
    val messages: SharedFlow<String>

    /** Address this phone connects to (players only). */
    val hostAddress: String?

    /** Addresses players can type to reach this table (host only). */
    fun addresses(): List<String>

    /** Links that open the table in a browser, for phones without the app such as iPhones (host only). */
    fun webLinks(): List<String>

    fun submit(action: GameAction)

    fun undo()
}
