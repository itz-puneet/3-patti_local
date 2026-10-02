package com.threepatti.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameState
import com.threepatti.core.Settlement
import com.threepatti.core.formatChipCount
import com.threepatti.core.formatChips
import com.threepatti.core.formatSignedChips

@Composable
fun LedgerTab(state: GameState, myId: String, canOpen: (String) -> Boolean, onPlayerClick: (String) -> Unit) {
    val transfers = remember(state.players) { Settlement.transfers(state.players) }
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "table") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val header = MaterialTheme.typography.labelMedium
                    LedgerRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Cell("Player", header, colors.onSurfaceVariant, name = true)
                        Cell("Took", header, colors.onSurfaceVariant)
                        Cell("Chips", header, colors.onSurfaceVariant)
                        Cell("Net", header, colors.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    state.players.forEach { player ->
                        val rowModifier = if (canOpen(player.id)) Modifier.clickable { onPlayerClick(player.id) } else Modifier
                        LedgerRow(rowModifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            val body = MaterialTheme.typography.bodyMedium
                            Cell(player.name + if (player.id == myId) " (you)" else "", body, colors.onSurface, name = true)
                            Cell(formatChips(player.buyIn), body, colors.onSurfaceVariant)
                            Cell(formatChips(player.balance), body, colors.onSurface)
                            Cell(
                                formatSignedChips(player.net),
                                body.copy(fontWeight = FontWeight.Bold),
                                colors.forNet(player.net),
                            )
                        }
                    }
                    HorizontalDivider()
                    LedgerRow(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        val total = MaterialTheme.typography.labelLarge
                        Cell("Total", total, colors.onSurface, name = true)
                        Cell(formatChips(state.players.sumOf { it.buyIn }), total, colors.onSurfaceVariant)
                        Cell(formatChips(state.players.sumOf { it.balance }), total, colors.onSurface)
                        Cell("", total, colors.onSurface)
                    }
                }
            }
        }
        val round = state.round
        if (round != null && round.isActive) {
            item(key = "pot-note") {
                Hint("${state.chipCount(round.pot)} are in the pot of the running round. Even out after it ends.")
            }
        }
        item(key = "settle-title") { SectionTitle("Even out") }
        if (transfers.isEmpty()) {
            item(key = "even") { Hint("Everyone is even.") }
        }
        items(transfers, key = { "${it.fromId}-${it.toId}" }) { transfer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.nameOf(transfer.fromId), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "gives",
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Text(
                        state.nameOf(transfer.toId),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatChipCount(transfer.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item(key = "explain") {
            Hint(
                "Took = starting chips plus extra chips taken, minus chips given back. Net = chips now minus took. " +
                    "To finish, hand over chips as listed under Even out and everyone ends even.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LedgerRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun RowScope.Cell(text: String, style: TextStyle, color: Color, name: Boolean = false) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (name) TextAlign.Start else TextAlign.End,
        modifier = Modifier.weight(if (name) 1.5f else 1f),
    )
}
