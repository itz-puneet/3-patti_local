package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameState
import com.threepatti.core.LogKind
import com.threepatti.core.RoundResult
import com.threepatti.core.formatSignedMoney

@Composable
fun HistoryTab(state: GameState) {
    val word = state.roundWord.replaceFirstChar { it.uppercase() }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.undoCount > 0) {
            item(key = "undo-count") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        "The host used undo ${state.undoCount} ${if (state.undoCount == 1) "time" else "times"}. " +
                            "Undone moves stay below, crossed out.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        item(key = "rounds-title") { SectionTitle("${word}s") }
        if (state.results.isEmpty()) {
            item(key = "no-rounds") { Hint("No ${state.roundWord}s finished yet.") }
        }
        // An undone result and its replacement share a number, so the position is the key.
        val results = state.results.withIndex().reversed()
        items(results, key = { "round-${it.index}" }) { (_, result) -> RoundCard(state, result, word) }
        item(key = "log-title") { SectionTitle("Table log", Modifier.padding(top = 8.dp)) }
        itemsIndexed(state.log.asReversed(), key = { _, entry -> "log-${entry.seq}" }) { _, entry ->
            val colors = MaterialTheme.colorScheme
            Text(
                if (entry.undone) "${entry.text} (undone)" else entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    entry.kind == LogKind.UNDO -> colors.error
                    entry.undone -> colors.outline
                    else -> colors.onSurfaceVariant
                },
                fontWeight = if (entry.kind == LogKind.UNDO) FontWeight.SemiBold else null,
                textDecoration = if (entry.undone) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RoundCard(state: GameState, result: RoundResult, word: String) {
    val colors = MaterialTheme.colorScheme
    val currency = state.settings.currency
    val strike = if (result.undone) TextDecoration.LineThrough else null
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$word ${result.number}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (result.undone) {
                    StatusPill("Undone by host", colors.errorContainer, colors.onErrorContainer, Modifier.padding(end = 8.dp))
                }
                Text("Pot ${state.money(result.pot)}", style = MaterialTheme.typography.labelLarge, textDecoration = strike)
            }
            Text(
                (if (result.winnerNames.size == 1) "Winner: " else "Split between: ") + result.winnerNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.undone) colors.outline else colors.primary,
                fontWeight = FontWeight.SemiBold,
                textDecoration = strike,
            )
            val changes = buildAnnotatedString {
                result.changes.entries.sortedByDescending { it.value }.forEachIndexed { index, (id, change) ->
                    if (index > 0) append("   ")
                    append(state.nameOf(id) + " ")
                    val color = if (result.undone) colors.outline else colors.forNet(change)
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                        append(formatSignedMoney(change, currency))
                    }
                }
            }
            Text(changes, style = MaterialTheme.typography.bodySmall, textDecoration = strike)
            if (result.undone) Hint("Not counted: the chips went back to how they were before this result.")
        }
    }
}
