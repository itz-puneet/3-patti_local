package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import com.threepatti.core.HandStatus
import com.threepatti.core.RoundPhase
import com.threepatti.core.TableSettings
import com.threepatti.core.formatSignedMoney
import com.threepatti.core.namesOf
import com.threepatti.core.summary

@Composable
fun GameDialogs(
    dialog: GameDialog,
    state: GameState,
    myId: String,
    session: TableSession,
    onOpen: (GameDialog) -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) {
    val submit: (GameAction) -> Unit = {
        session.submit(it)
        onDismiss()
    }
    when (dialog) {
        GameDialog.Invite -> InviteDialog(session.addresses(), session.webLinks(), onDismiss)
        GameDialog.AddPlayer -> TextInputDialog(
            title = "Add a player without a phone",
            label = "Name",
            initial = "",
            confirmLabel = "Add",
            hint = "You will make their moves from this phone.",
            onConfirm = { submit(GameAction.AddPlayer(it)) },
            onDismiss = onDismiss,
        )
        GameDialog.Settings -> SettingsDialog(state.settings, onSave = { submit(GameAction.UpdateSettings(it)) }, onDismiss)
        GameDialog.Rules -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Table rules") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.settings.summary(), fontWeight = FontWeight.SemiBold)
                    RulesText(state.settings)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        GameDialog.ForceShow -> ConfirmDialog(
            title = "Call a show for everyone?",
            text = "Everyone still playing shows their cards, and you enter the winner.",
            confirmLabel = "Call show",
            onConfirm = { submit(GameAction.ForceShow) },
            onDismiss = onDismiss,
        )
        GameDialog.CancelRound -> ConfirmDialog(
            title = "Cancel this round?",
            text = "Use this for a misdeal. Every bet of this round goes back to its player.",
            confirmLabel = "Cancel round",
            destructive = true,
            onConfirm = { submit(GameAction.CancelRound) },
            onDismiss = onDismiss,
        )
        GameDialog.CloseTable -> ConfirmDialog(
            title = "Close the table?",
            text = "All players are disconnected. The table stays saved on this phone and you can resume it from the home screen.",
            confirmLabel = "Close table",
            destructive = true,
            onConfirm = {
                onDismiss()
                onExit()
            },
            onDismiss = onDismiss,
        )
        GameDialog.LeaveTable -> ConfirmDialog(
            title = "Leave the table?",
            text = "Your seat and chips stay with the host. Join again from this phone to get them back.",
            confirmLabel = "Leave",
            onConfirm = {
                onDismiss()
                onExit()
            },
            onDismiss = onDismiss,
        )
        is GameDialog.PlayerMenu -> PlayerMenuDialog(
            state = state,
            playerId = dialog.playerId,
            isHost = session.isHost,
            isMe = dialog.playerId == myId,
            onAction = submit,
            onActionKeepOpen = session::submit,
            onOpen = onOpen,
            onDismiss = onDismiss,
        )
        is GameDialog.Chips -> ChipsDialog(
            state = state,
            playerId = dialog.playerId,
            onConfirm = { amount -> submit(GameAction.AdjustChips(dialog.playerId, amount)) },
            onDismiss = onDismiss,
        )
        is GameDialog.Rename -> TextInputDialog(
            title = "Rename",
            label = "Name",
            initial = state.nameOf(dialog.playerId),
            confirmLabel = "Save",
            hint = null,
            onConfirm = { submit(GameAction.RenamePlayer(dialog.playerId, it)) },
            onDismiss = onDismiss,
        )
        is GameDialog.ConfirmRemove -> ConfirmDialog(
            title = "Remove ${state.nameOf(dialog.playerId)}?",
            text = "They leave the table and the ledger. If their phone joins again they start fresh.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { submit(GameAction.RemovePlayer(dialog.playerId)) },
            onDismiss = onDismiss,
        )
        is GameDialog.ConfirmPack -> {
            val invested = state.round?.hand(dialog.playerId)?.invested ?: 0
            val mine = dialog.playerId == myId
            ConfirmDialog(
                title = if (mine) "Pack your hand?" else "Pack ${state.nameOf(dialog.playerId)}'s hand?",
                text = "The ${state.money(invested)} already in the pot stays there.",
                confirmLabel = "Pack",
                destructive = true,
                onConfirm = { submit(GameAction.Pack(dialog.playerId)) },
                onDismiss = onDismiss,
            )
        }
        is GameDialog.ConfirmWinners -> {
            val round = state.round
            val ids = dialog.winnerIds
            val sideShow = round?.sideShow
            if (round?.phase == RoundPhase.SIDE_SHOW_COMPARE && sideShow != null && ids.size == 1) {
                val loser = if (ids[0] == sideShow.requesterId) sideShow.targetId else sideShow.requesterId
                ConfirmDialog(
                    title = "${state.nameOf(ids[0])} wins the side show?",
                    text = "${state.nameOf(loser)} packs and betting continues.",
                    confirmLabel = "Confirm",
                    onConfirm = { submit(GameAction.DeclareWinners(ids)) },
                    onDismiss = onDismiss,
                )
            } else {
                val pot = state.money(round?.pot ?: 0)
                ConfirmDialog(
                    title = if (ids.size == 1) "${state.nameOf(ids[0])} wins?" else "Split the pot?",
                    text = if (ids.size == 1) {
                        "${state.nameOf(ids[0])} gets the pot of $pot."
                    } else {
                        "${state.namesOf(ids)} share the pot of $pot."
                    },
                    confirmLabel = "Confirm",
                    onConfirm = { submit(GameAction.DeclareWinners(ids)) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PlayerMenuDialog(
    state: GameState,
    playerId: String,
    isHost: Boolean,
    isMe: Boolean,
    onAction: (GameAction) -> Unit,
    onActionKeepOpen: (GameAction) -> Unit,
    onOpen: (GameDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    val player = state.player(playerId)
    if (player == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val round = state.round
    val hand = round?.takeIf { it.isActive }?.hand(playerId)
    val currency = state.settings.currency
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(player.name + if (isMe) " (you)" else "") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Chips ${state.money(player.balance)} · bought ${state.money(player.buyIn)} · " +
                        formatSignedMoney(player.net, currency),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (isHost) {
                    MenuAction("Add or take chips") { onOpen(GameDialog.Chips(playerId)) }
                    if (hand != null && hand.status == HandStatus.BLIND) {
                        MenuAction("Mark cards as seen") { onAction(GameAction.SeeCards(playerId)) }
                    }
                    if (hand != null && hand.status != HandStatus.PACKED && round?.phase == RoundPhase.BETTING) {
                        MenuAction("Pack this hand") { onOpen(GameDialog.ConfirmPack(playerId)) }
                    }
                }
                MenuAction(if (player.sittingOut) "Sit back in" else "Sit out from next round") {
                    onAction(GameAction.SetSittingOut(playerId, !player.sittingOut))
                }
                MenuAction("Rename") { onOpen(GameDialog.Rename(playerId)) }
                if (isHost && !state.isRoundActive) {
                    MenuAction("Move seat up") { onActionKeepOpen(GameAction.MoveSeat(playerId, -1)) }
                    MenuAction("Move seat down") { onActionKeepOpen(GameAction.MoveSeat(playerId, 1)) }
                }
                if (isHost && !player.isHost) {
                    MenuAction("Remove from table", danger = true) { onOpen(GameDialog.ConfirmRemove(playerId)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun MenuAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (danger) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsDialog(state: GameState, playerId: String, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val player = state.player(playerId)
    if (player == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull() ?: 0
    val quick = listOf(50, 100, state.settings.startingBalance).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chips for ${player.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Has ${state.money(player.balance)} now.", style = MaterialTheme.typography.bodyMedium)
                Hint("Add when they buy more chips. Take when they cash out. The ledger stays correct either way.")
                NumberField("Amount", amountText, { amountText = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { value ->
                        AssistChip(onClick = { amountText = value.toString() }, label = { Text(state.money(value)) })
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onConfirm(-amount) }, enabled = amount in 1..player.balance) { Text("Take") }
                TextButton(onClick = { onConfirm(amount) }, enabled = amount > 0) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    hint: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) Hint(hint)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(20) },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsDialog(settings: TableSettings, onSave: (TableSettings) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(SettingsInput.from(settings)) }
    val error = input.error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Table settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Changes apply from the next round. Starting chips only affect players who join later.")
                SettingsFields(input, onChange = { input = it })
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { input.toSettings()?.let(onSave) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteDialog(appAddresses: List<String>, webLinks: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite players") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (appAddresses.isEmpty()) {
                    Text(
                        "No WiFi address found. Connect to WiFi or turn on your hotspot.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Hint("Everyone must be on the same WiFi, or connected to this phone's hotspot.")
                val link = webLinks.firstOrNull()
                if (link != null) {
                    Text("iPhone or phone without the app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Scan this with the camera, or open the link in the browser:", style = MaterialTheme.typography.bodyMedium)
                    QrImage(link, Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally))
                    Text(
                        link,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    webLinks.drop(1).forEach { Hint("Also: $it") }
                    Hint(
                        "Added someone without a phone earlier? If they join with the same name, " +
                            "they take over that seat and its chips.",
                    )
                    HorizontalDivider()
                }
                Text("Phones with the app", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Open the app, tap Join a table and pick this table. If it doesn't show up, type:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                appAddresses.forEach { address ->
                    Text(
                        address,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
