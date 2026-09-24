package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import com.threepatti.core.Player
import com.threepatti.core.Round
import com.threepatti.core.RoundPhase
import com.threepatti.core.Rules
import com.threepatti.core.SeatOptions
import com.threepatti.core.winnerText

/**
 * The buttons at the bottom of the game screen. Players act for their own seat. The host also acts for
 * players without a phone or whose phone is offline, and can take over anyone's turn if needed.
 */
@Composable
fun ActionPanel(
    state: GameState,
    myId: String,
    isHost: Boolean,
    onAction: (GameAction) -> Unit,
    onOpen: (GameDialog) -> Unit,
) {
    val round = state.round
    val waitingOnId = when (round?.phase) {
        RoundPhase.BETTING -> round?.turnId
        RoundPhase.SIDE_SHOW_REQUESTED -> round?.sideShow?.targetId
        else -> null
    }
    val waitingOn = state.player(waitingOnId)?.takeIf { it.id != myId }
    var takeOver by remember(round?.number, waitingOnId) { mutableStateOf(false) }
    val hostMustPlay = isHost && waitingOn != null && (!waitingOn.hasDevice || !waitingOn.connected)
    val seatId = if (isHost && waitingOn != null && (hostMustPlay || takeOver)) waitingOn.id else myId
    val takeOverOffer = if (isHost && waitingOn != null && !hostMustPlay) waitingOn else null

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                round == null || round.phase == RoundPhase.FINISHED -> BetweenRounds(state, isHost, onAction)
                round.phase == RoundPhase.BETTING -> Betting(
                    state, round, seatId, myId, takeOverOffer, takeOver, { takeOver = !takeOver }, onAction, onOpen,
                )
                round.phase == RoundPhase.SIDE_SHOW_REQUESTED -> SideShowRequested(
                    state, round, seatId, myId, takeOverOffer, takeOver, { takeOver = !takeOver }, onAction,
                )
                round.phase == RoundPhase.SIDE_SHOW_COMPARE -> SideShowCompare(state, round, isHost, onOpen)
                else -> Showdown(state, round, isHost, onOpen)
            }
        }
    }
}

@Composable
private fun BetweenRounds(state: GameState, isHost: Boolean, onAction: (GameAction) -> Unit) {
    val round = state.round
    val boot = state.settings.bootAmount
    if (round != null) {
        Text(
            winnerText(state, round),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (isHost) {
        val ready = state.players.count { !it.sittingOut && it.balance >= boot }
        Button(
            onClick = { onAction(GameAction.StartRound) },
            enabled = ready >= 2,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (round == null) "Start round" else "Start next round") }
        Hint(
            if (ready < 2) {
                "Need at least 2 players with ${state.money(boot)} or more"
            } else {
                "Deal the cards, then start. Boot of ${state.money(boot)} from $ready players"
            },
            center = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            "Waiting for ${state.hostName} to start the ${if (round == null) "" else "next "}round",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Betting(
    state: GameState,
    round: Round,
    seatId: String,
    myId: String,
    takeOverOffer: Player?,
    takingOver: Boolean,
    onToggleTakeOver: () -> Unit,
    onAction: (GameAction) -> Unit,
    onOpen: (GameDialog) -> Unit,
) {
    val options = Rules.seatOptions(state, seatId)
    val forMe = seatId == myId
    val seatName = state.nameOf(seatId)
    val headline = when {
        !forMe -> "Playing for $seatName"
        options.isTurn -> "Your turn"
        else -> "Waiting for ${state.nameOf(round.turnId)}"
    }
    Header(headline, seatSummary(state, options), takeOverOffer, takingOver, onToggleTakeOver)

    when {
        !options.inRound -> Hint(if (forMe) "You are not in this round. You'll be dealt in next round." else "$seatName is not in this round.")
        options.isPacked -> Hint(if (forMe) "You packed. Wait for the next round." else "$seatName packed.")
        options.isTurn -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(
                    label = options.callLabel,
                    amount = state.money(options.callAmount),
                    enabled = options.callError == null,
                    onClick = { onAction(GameAction.Bet(seatId)) },
                )
                BigButton(
                    label = "Raise",
                    amount = state.money(options.raiseAmount),
                    enabled = options.raiseError == null,
                    secondary = true,
                    onClick = { onAction(GameAction.Bet(seatId, raise = true)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.canSee) SmallButton("See cards") { onAction(GameAction.SeeCards(seatId)) }
                if (options.showAvailable) {
                    SmallButton("Show ${state.money(options.showAmount)}", enabled = options.showError == null) {
                        onAction(GameAction.Show(seatId))
                    }
                }
                if (options.sideShowAvailable) {
                    SmallButton("Side show", enabled = options.sideShowError == null) {
                        onAction(GameAction.RequestSideShow(seatId))
                    }
                }
                SmallButton("Pack", danger = true) { onOpen(GameDialog.ConfirmPack(seatId)) }
            }
            val note = options.callError
                ?: options.raiseError?.takeIf { !it.contains("needs") }
                ?: if (options.sideShowAvailable) {
                    options.sideShowError ?: "Side show: pay ${state.money(options.sideShowAmount)} and compare cards " +
                        "with ${state.nameOf(options.sideShowTargetId)}"
                } else {
                    null
                }
            if (note != null) Hint(note)
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.canSee) SmallButton("See cards") { onAction(GameAction.SeeCards(seatId)) }
            SmallButton("Pack", danger = true) { onOpen(GameDialog.ConfirmPack(seatId)) }
        }
    }
}

@Composable
private fun SideShowRequested(
    state: GameState,
    round: Round,
    seatId: String,
    myId: String,
    takeOverOffer: Player?,
    takingOver: Boolean,
    onToggleTakeOver: () -> Unit,
    onAction: (GameAction) -> Unit,
) {
    val sideShow = round.sideShow ?: return
    val requester = state.nameOf(sideShow.requesterId)
    val target = state.nameOf(sideShow.targetId)
    if (seatId == sideShow.targetId) {
        Header(
            if (seatId == myId) "$requester asks you for a side show" else "$requester asks $target for a side show",
            null,
            takeOverOffer,
            takingOver,
            onToggleTakeOver,
        )
        Hint("If accepted, both compare cards privately and the weaker hand packs. The host enters the result.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Refuse") { onAction(GameAction.AnswerSideShow(seatId, accept = false)) }
            BigButton(
                label = "Accept",
                amount = null,
                enabled = true,
                onClick = { onAction(GameAction.AnswerSideShow(seatId, accept = true)) },
            )
        }
    } else {
        Header("Waiting for $target to answer $requester's side show", null, takeOverOffer, takingOver, onToggleTakeOver)
    }
}

@Composable
private fun SideShowCompare(state: GameState, round: Round, isHost: Boolean, onOpen: (GameDialog) -> Unit) {
    val sideShow = round.sideShow ?: return
    val pair = listOf(sideShow.requesterId, sideShow.targetId)
    Text(
        "${state.nameOf(pair[0])} and ${state.nameOf(pair[1])} are comparing cards",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (isHost) {
        Hint("Who has the better hand? The other one packs.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { id ->
                BigButton(
                    label = state.nameOf(id),
                    amount = null,
                    enabled = true,
                    onClick = { onOpen(GameDialog.ConfirmWinners(listOf(id))) },
                )
            }
        }
    } else {
        Hint("Waiting for the host to enter who won the side show")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Showdown(state: GameState, round: Round, isHost: Boolean, onOpen: (GameDialog) -> Unit) {
    Text(
        "Show: " + round.showdownIds.joinToString(" vs ") { state.nameOf(it) },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (!isHost) {
        Hint("Cards are on the table. Waiting for the host to enter the winner.")
        return
    }
    var selected by remember(round.number, round.showdownIds) { mutableStateOf(setOf<String>()) }
    Hint("Tap the winner. Tap more than one only if hands are exactly equal to split the pot.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        round.showdownIds.forEach { id ->
            val isSelected = id in selected
            FilterChip(
                selected = isSelected,
                onClick = { selected = if (isSelected) selected - id else selected + id },
                label = { Text(state.nameOf(id)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
    val winners = round.showdownIds.filter { it in selected }
    Button(
        onClick = { onOpen(GameDialog.ConfirmWinners(winners)) },
        enabled = winners.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            when (winners.size) {
                0 -> "Pick the winner"
                1 -> "Give ${state.money(round.pot)} to ${state.nameOf(winners[0])}"
                else -> "Split ${state.money(round.pot)} between ${winners.size}"
            },
        )
    }
}

@Composable
private fun Header(
    title: String,
    detail: String?,
    takeOverOffer: Player?,
    takingOver: Boolean,
    onToggleTakeOver: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) Hint(detail)
        }
        if (takeOverOffer != null) {
            TextButton(onClick = onToggleTakeOver) {
                Text(if (takingOver) "Back to me" else "Play for ${takeOverOffer.name}", maxLines = 1)
            }
        }
    }
}

private fun seatSummary(state: GameState, options: SeatOptions): String? {
    if (!options.inRound) return null
    val player = state.player(options.playerId) ?: return null
    val status = when {
        options.isPacked -> "packed"
        options.isBlind -> "blind"
        else -> "seen"
    }
    return "${state.money(player.balance)} left · $status"
}

@Composable
private fun RowScope.BigButton(
    label: String,
    amount: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        colors = if (secondary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (amount != null) Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RowScope.SmallButton(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = if (danger) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
