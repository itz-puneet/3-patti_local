package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
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
import com.threepatti.core.formatMoney
import com.threepatti.core.formatSignedMoney
import com.threepatti.core.summary

@Composable
fun TableTab(state: GameState, myId: String, canOpen: (String) -> Boolean, onPlayerClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
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
        if (state.players.size < 2) {
            item(key = "alone") {
                Hint(
                    "Waiting for players. Others open the app on the same WiFi and tap Join a table. " +
                        "You can also add someone without a phone from the menu.",
                    center = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

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
            if (round == null) {
                Text("No round running", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Everyone starts with ${state.money(state.settings.startingBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(state.settings.summary(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            } else {
                Text(
                    if (round.isActive) "ROUND ${round.number}  ·  POT" else "ROUND ${round.number} FINISHED",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    state.money(round.pot),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (round.isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StakeLabel("Blind", state.money(round.stake))
                        StakeLabel("Chaal", state.money(round.stake * 2))
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    describeRound(state, round),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text("Dealer: ${state.nameOf(round.dealerId)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
    val currency = state.settings.currency
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
                    StatusPill("Dealer", colors.secondary, colors.onSecondary)
                }
                if (player.isHost) StatusPill("Host", colors.surfaceVariant, colors.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (label, tone) = playerStatus(state, player)
                val (container, content) = toneColors(tone)
                StatusPill(label, container, content)
                if (round != null && round.isActive && hand != null) {
                    Hint("in pot ${formatMoney(hand.invested, currency)}")
                }
            }
            when {
                !player.hasDevice -> Hint("No phone · host plays for them")
                !player.isHost && !player.connected -> Text(
                    "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(player.balance, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatSignedMoney(player.net, currency),
                style = MaterialTheme.typography.labelMedium,
                color = colors.forNet(player.net),
            )
        }
    }
}
