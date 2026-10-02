package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threepatti.core.GameState
import com.threepatti.core.Player
import com.threepatti.core.RoundPhase
import com.threepatti.core.Rules
import com.threepatti.core.describeRound
import com.threepatti.core.enteredCards
import com.threepatti.core.formatChips
import com.threepatti.core.handName
import com.threepatti.core.handRules
import com.threepatti.core.formatSignedChips
import com.threepatti.core.nextDealText
import com.threepatti.core.summary

@Composable
fun TableTab(
    state: GameState,
    myId: String,
    canOpen: (String) -> Boolean,
    onPlayerClick: (String) -> Unit,
    onInvite: (() -> Unit)?,
    tableView: Boolean,
    onTableViewChange: (Boolean) -> Unit,
) {
    BoxWithConstraints {
        // What's left for the table under the Table/List switch, above the action buttons.
        val tableRoom = maxHeight - 76.dp
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "view") { ViewSwitch(tableView, onTableViewChange) }
            if (tableView) {
                item(key = "table") { TableView(state, myId, canOpen, onPlayerClick, fitHeight = tableRoom) }
            } else {
                item(key = "pot") { PotCard(state) }
                items(state.players, key = { it.id }) { player ->
                    PlayerRow(
                        state = state,
                        player = player,
                        isMe = player.id == myId,
                        clickable = canOpen(player.id),
                        onClick = { onPlayerClick(player.id) },
                    )
                }
            }
            if (onInvite != null && state.round == null) {
                item(key = "invite") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Invite players", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Players with the app tap Join a table. iPhones scan a QR code and play in the browser.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = onInvite,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                ),
                            ) { Text("Show QR code and address") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitch(tableView: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("Table", "List").forEachIndexed { index, label ->
            SegmentedButton(
                selected = tableView == (index == 0),
                onClick = { onChange(index == 0) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(label) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PotCard(state: GameState) {
    val round = state.round
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val word = state.roundWord.uppercase()
            if (round == null) {
                Text(
                    if (state.settings.isPoker) "No hand being played" else "No round running",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Everyone starts with ${state.chipCount(state.settings.startingBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(state.settings.summary(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                nextDealText(state)?.let { NextDealLine(it) }
            } else {
                Text(
                    if (round.isActive) "$word ${round.number}  ·  POT" else "$word ${round.number} FINISHED",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    state.chips(round.pot),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (round.isActive && state.settings.isPoker) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                        StakeLabel("Blinds", "${state.chips(state.settings.smallBlind)}/${state.chips(state.settings.bigBlind)}")
                        if (state.settings.ante > 0) StakeLabel("Ante", state.chips(state.settings.ante))
                        if (round.phase == RoundPhase.BETTING) StakeLabel("Bet", state.chips(round.currentBet))
                    }
                } else if (round.isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StakeLabel("Blind", state.chips(round.stake))
                        StakeLabel("Chaal", state.chips(round.stake * 2))
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    describeRound(state, round),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (round.isActive) {
                    Text("Dealer: ${state.nameOf(round.dealerId)}", style = MaterialTheme.typography.bodySmall)
                } else {
                    nextDealText(state)?.let { NextDealLine(it) }
                }
            }
        }
    }
}

@Composable
private fun NextDealLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StakeLabel(label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.bodyMedium)
        Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerRow(state: GameState, player: Player, isMe: Boolean, clickable: Boolean, onClick: () -> Unit) {
    val round = state.round
    val colors = MaterialTheme.colorScheme
    val waitingOnPlayer = round != null && round.isActive && (
        (round.phase == RoundPhase.BETTING && round.turnId == player.id) ||
            (round.phase == RoundPhase.SIDE_SHOW_REQUESTED && round.sideShow?.targetId == player.id)
        )
    val cardColors = CardDefaults.cardColors(
        containerColor = if (waitingOnPlayer) colors.secondaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
    )
    val border = if (waitingOnPlayer) BorderStroke(2.dp, colors.secondary) else null
    val content: @Composable ColumnScope.() -> Unit = { PlayerRowContent(state, player, isMe, waitingOnPlayer) }
    if (clickable) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = cardColors, border = border, content = content)
    } else {
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, border = border, content = content)
    }
}

@Composable
private fun PlayerRowContent(state: GameState, player: Player, isMe: Boolean, highlighted: Boolean) {
    val round = state.round
    val hand = round?.hand(player.id)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Initial(player.name, highlighted = highlighted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    player.name + if (isMe) " (you)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (round != null && round.isActive && round.dealerId == player.id) {
                    StatusPill(if (state.settings.isPoker) "D" else "Dealer", colors.secondary, colors.onSecondary)
                }
                if (round != null && round.isActive && state.settings.isPoker) {
                    if (round.smallBlindId == player.id) StatusPill("SB", colors.tertiaryContainer, colors.onTertiaryContainer)
                    if (round.bigBlindId == player.id) StatusPill("BB", colors.tertiaryContainer, colors.onTertiaryContainer)
                }
                if (player.isHost) StatusPill("Host", colors.surfaceVariant, colors.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (label, tone) = playerStatus(state, player)
                val (container, content) = toneColors(tone)
                StatusPill(label, container, content)
                if (round != null && round.isActive && hand != null && hand.invested > 0) {
                    Hint(
                        if (state.settings.isPoker && hand.streetBet > 0) {
                            "bet ${formatChips(hand.streetBet)} · in pot ${formatChips(hand.invested)}"
                        } else {
                            "in pot ${formatChips(hand.invested)}"
                        },
                    )
                }
            }
            val shown = round?.takeIf { player.id in Rules.showingCards(it) }?.let { r -> r.hand(player.id) }
            shown?.enteredCards()?.let { cards ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniHand(cards, state.settings.handRules)
                    state.handName(shown)?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                }
            }
            when {
                !player.hasDevice -> Hint("No phone · host plays for them")
                !player.isHost && !player.connected -> Text(
                    if (player.onBrowser) "Offline · in browser" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
                player.onBrowser -> Hint("In browser")
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatChips(player.balance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatSignedChips(player.net),
                style = MaterialTheme.typography.labelMedium,
                color = colors.forNet(player.net),
            )
        }
    }
}
