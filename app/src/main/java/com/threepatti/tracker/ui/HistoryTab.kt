package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameState
import com.threepatti.core.RoundResult
import com.threepatti.core.formatSignedMoney

@Composable
fun HistoryTab(state: GameState) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "rounds-title") { SectionTitle("Rounds") }
        if (state.results.isEmpty()) {
            item(key = "no-rounds") { Hint("No rounds finished yet.") }
        }
        items(state.results.asReversed(), key = { "round-${it.number}" }) { result -> RoundCard(state, result) }
        item(key = "log-title") { SectionTitle("Table log", Modifier.padding(top = 8.dp)) }
        items(state.log.asReversed(), key = { "log-${it.seq}" }) { entry ->
            Text(
                entry.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RoundCard(state: GameState, result: RoundResult) {
    val colors = MaterialTheme.colorScheme
    val currency = state.settings.currency
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    "Round ${result.number}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("Pot ${state.money(result.pot)}", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                (if (result.winnerNames.size == 1) "Winner: " else "Split between: ") + result.winnerNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            val changes = buildAnnotatedString {
                result.changes.entries.sortedByDescending { it.value }.forEachIndexed { index, (id, change) ->
                    if (index > 0) append("   ")
                    append(state.nameOf(id) + " ")
                    withStyle(SpanStyle(color = colors.forNet(change), fontWeight = FontWeight.SemiBold)) {
                        append(formatSignedMoney(change, currency))
                    }
                }
            }
            Text(changes, style = MaterialTheme.typography.bodySmall)
        }
    }
}
