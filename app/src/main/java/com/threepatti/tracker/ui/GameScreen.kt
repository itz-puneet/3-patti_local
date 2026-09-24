package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameState
import com.threepatti.core.RoundPhase
import com.threepatti.core.net.ConnectionStatus
import kotlinx.coroutines.launch

/** Dialogs the game screen can show. Only one is open at a time. */
sealed interface GameDialog {
    data object Invite : GameDialog
    data object AddPlayer : GameDialog
    data object Settings : GameDialog
    data object Rules : GameDialog
    data object ForceShow : GameDialog
    data object CancelRound : GameDialog
    data object CloseTable : GameDialog
    data object LeaveTable : GameDialog
    data class PlayerMenu(val playerId: String) : GameDialog
    data class Chips(val playerId: String) : GameDialog
    data class Rename(val playerId: String) : GameDialog
    data class ConfirmRemove(val playerId: String) : GameDialog
    data class ConfirmPack(val playerId: String) : GameDialog
    data class ConfirmWinners(val winnerIds: List<String>) : GameDialog
}

@Composable
fun GameScreen(session: TableSession, onExit: () -> Unit) {
    val state by session.state.collectAsState()
    val myId by session.myPlayerId.collectAsState()
    val connection by session.connection.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(session) {
        session.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message) }
        }
    }

    val current = state
    val me = myId
    if (current == null || me == null) {
        ConnectingScreen(session.hostAddress, connection, onCancel = onExit)
    } else {
        GameContent(session, current, me, connection, canUndo, snackbar, onExit)
    }

    val closed = connection as? ConnectionStatus.Closed
    if (closed != null && !session.isHost) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Disconnected") },
            text = { Text(closed.reason) },
            confirmButton = { TextButton(onClick = onExit) { Text("OK") } },
        )
    }
}

@Composable
private fun GameContent(
    session: TableSession,
    state: GameState,
    myId: String,
    connection: ConnectionStatus,
    canUndo: Boolean,
    snackbar: SnackbarHostState,
    onExit: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<GameDialog?>(null) }
    val openPlayer: (String) -> Unit = { id -> if (session.isHost || id == myId) dialog = GameDialog.PlayerMenu(id) }

    Scaffold(
        topBar = {
            GameTopBar(
                state = state,
                isHost = session.isHost,
                connection = connection,
                canUndo = canUndo,
                onUndo = session::undo,
                onOpen = { dialog = it },
            )
        },
        bottomBar = {
            ActionPanel(
                state = state,
                myId = myId,
                isHost = session.isHost,
                onAction = session::submit,
                onOpen = { dialog = it },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (connection is ConnectionStatus.Reconnecting) ReconnectingBanner(connection.reason)
            TabRow(selectedTabIndex = tab) {
                listOf("Table", "Ledger", "History").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> TableTab(
                    state = state,
                    myId = myId,
                    canOpen = { session.isHost || it == myId },
                    onPlayerClick = openPlayer,
                    onInvite = if (session.isHost) {
                        { dialog = GameDialog.Invite }
                    } else {
                        null
                    },
                )
                1 -> LedgerTab(state, myId, canOpen = { session.isHost || it == myId }, onPlayerClick = openPlayer)
                else -> HistoryTab(state)
            }
        }
    }

    dialog?.let { open ->
        GameDialogs(
            dialog = open,
            state = state,
            myId = myId,
            session = session,
            onOpen = { dialog = it },
            onDismiss = { dialog = null },
            onExit = onExit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(
    state: GameState,
    isHost: Boolean,
    connection: ConnectionStatus,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpen: (GameDialog) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val round = state.round
    val subtitle = when {
        isHost -> {
            val phones = state.players.count { !it.isHost && it.hasDevice }
            val online = state.players.count { !it.isHost && it.hasDevice && it.connected }
            "You are hosting · $online of $phones phones online"
        }
        connection is ConnectionStatus.Connected -> "Hosted by ${state.hostName}"
        connection is ConnectionStatus.Reconnecting -> "Reconnecting…"
        else -> "Connecting…"
    }
    TopAppBar(
        title = {
            Column {
                Text(state.tableName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (isHost) TextButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val item: @Composable (String, Boolean, GameDialog) -> Unit = { label, enabled, target ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onOpen(target)
                        },
                    )
                }
                val betting = round != null && round.isActive && round.phase != RoundPhase.SHOWDOWN
                if (isHost) {
                    item("Invite players", true, GameDialog.Invite)
                    item("Add player without phone", true, GameDialog.AddPlayer)
                    item("Table settings", !state.isRoundActive, GameDialog.Settings)
                    item(if (state.settings.isPoker) "Go to showdown" else "Call show for everyone", betting, GameDialog.ForceShow)
                    item("Cancel ${state.roundWord} (misdeal)", state.isRoundActive, GameDialog.CancelRound)
                    item("Close table", true, GameDialog.CloseTable)
                } else {
                    item("Table rules", true, GameDialog.Rules)
                    item("Leave table", true, GameDialog.LeaveTable)
                }
            }
        },
    )
}

@Composable
private fun ReconnectingBanner(reason: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text("Lost the host. Reconnecting…", style = MaterialTheme.typography.labelLarge)
                Text(reason, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectingScreen(address: String?, status: ConnectionStatus, onCancel: () -> Unit) {
    val lastError = (status as? ConnectionStatus.Connecting)?.lastError
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                if (lastError == null) "Joining the table at ${address ?: "the host"}…" else "Still trying to reach the host…",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (lastError != null) {
                Text(lastError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Hint(
                    "Check that this phone is on the same WiFi or hotspot as the host, and that the host's table is open.",
                    center = true,
                )
            }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
