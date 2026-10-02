package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.threepatti.core.Card
import com.threepatti.core.GameRuleException
import com.threepatti.core.Suit
import com.threepatti.core.TeenPatti
import com.threepatti.core.Verdict
import com.threepatti.core.rankLabel

/** One hand on the Decide winner screen: a player in the show, or "Hand 3" when used on its own. */
data class HandSeat(val key: String, val label: String)

private const val MAX_HANDS = 6
private val RedSuit = Color(0xFFC62828)
private val BlackSuit = Color(0xFF1B1B1B)

@Composable
fun DecideWinnerDialog(
    title: String,
    seats: List<HandSeat>,
    sideShowAsker: String?,
    canAddHands: Boolean,
    declareText: (winners: List<String>) -> String,
    onDeclare: ((winners: List<String>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            DecideWinnerContent(title, seats, sideShowAsker, canAddHands, declareText, onDeclare, onDismiss)
        }
    }
}

/**
 * Players enter their 3 cards and the app ranks the hands, for when the table can't agree who won a
 * show or side show. With [onDeclare] (the host, during a show) the result can be entered straight away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecideWinnerContent(
    title: String,
    seats: List<HandSeat>,
    sideShowAsker: String?,
    canAddHands: Boolean,
    declareText: (winners: List<String>) -> String,
    onDeclare: ((winners: List<String>) -> Unit)?,
    onClose: () -> Unit,
    initialCards: Map<String, List<Card?>> = emptyMap(),
) {
    var hands by remember { mutableStateOf(seats) }
    var cards by remember { mutableStateOf(seats.associate { it.key to (initialCards[it.key] ?: List(3) { null }) }) }
    var slot by remember { mutableStateOf(firstEmpty(seats, cards) ?: (0 to 0)) }
    var rank by remember { mutableStateOf<Int?>(null) }
    var suit by remember { mutableStateOf<Suit?>(null) }

    val selectedKey = hands.getOrNull(slot.first)?.key
    // Cards on the table, except the one in the selected slot, which can be swapped.
    val used = cards.flatMap { (key, list) -> list.filterIndexed { i, card -> card != null && !(key == selectedKey && i == slot.second) } }
        .filterNotNull().toSet()
    val complete = hands.all { seat -> cards.getValue(seat.key).all { it != null } }
    val verdict: Result<Verdict<String>>? = if (complete) {
        runCatching { TeenPatti.decide(hands.associate { it.key to cards.getValue(it.key).filterNotNull() }, sideShowAsker) }
    } else {
        null
    }
    val winners = verdict?.getOrNull()?.winners.orEmpty()

    fun place(card: Card) {
        val key = selectedKey ?: return
        cards = cards + (key to cards.getValue(key).toMutableList().also { it[slot.second] = card })
        rank = null
        suit = null
        firstEmpty(hands, cards)?.let { slot = it }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Hint("Tap a card, then pick its rank and suit. The app ranks the hands by 3 Patti rules.")
            hands.forEachIndexed { index, seat ->
                val list = cards.getValue(seat.key)
                // Named as soon as its own 3 cards are in, before the other hands are complete.
                val hand = list.takeIf { cards -> cards.all { it != null } }
                    ?.let { runCatching { TeenPatti.evaluate(it.filterNotNull()) }.getOrNull() }
                val won = seat.key in winners
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = if (won) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(seat.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (won) StatusPill("Wins", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            }
                            Text(
                                hand?.name ?: if (list.all { it != null }) "" else "${list.count { it == null }} to enter",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hand != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (hand != null) FontWeight.SemiBold else null,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEachIndexed { i, card ->
                                CardSlot(card, selected = slot == index to i, onClick = {
                                    slot = index to i
                                    rank = null
                                    suit = null
                                })
                            }
                        }
                        if (canAddHands && hands.size > 2) {
                            IconButton(onClick = {
                                hands = hands - seat
                                cards = cards - seat.key
                                slot = firstEmpty(hands, cards) ?: (0 to 0)
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${seat.label}") }
                        }
                    }
                }
            }
            if (canAddHands && hands.size < MAX_HANDS) {
                TextButton(onClick = {
                    val number = (hands.mapNotNull { it.key.removePrefix("hand").toIntOrNull() }.maxOrNull() ?: 0) + 1
                    val seat = HandSeat("hand$number", "Hand $number")
                    hands = hands + seat
                    cards = cards + (seat.key to List(3) { null })
                    slot = firstEmpty(hands, cards) ?: (hands.lastIndex to 0)
                }) { Text("Add a hand") }
            }
            VerdictCard(hands, verdict)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    (14 downTo 2).forEach { r ->
                        val free = Suit.entries.any { Card(r, it) !in used } && (suit == null || Card(r, suit!!) !in used)
                        PickerKey(rankLabel(r), selected = rank == r, enabled = free, color = null) {
                            val chosenSuit = suit
                            if (chosenSuit != null) place(Card(r, chosenSuit)) else rank = if (rank == r) null else r
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Suit.entries.forEach { s ->
                        val free = rank?.let { Card(it, s) !in used } ?: true
                        PickerKey(s.symbol, selected = suit == s, enabled = free, color = if (s.red) RedSuit else BlackSuit, wide = true) {
                            val chosenRank = rank
                            if (chosenRank != null) place(Card(chosenRank, s)) else suit = if (suit == s) null else s
                        }
                    }
                    TextButton(
                        onClick = {
                            val key = selectedKey ?: return@TextButton
                            cards = cards + (key to cards.getValue(key).toMutableList().also { it[slot.second] = null })
                            rank = null
                            suit = null
                        },
                        enabled = selectedKey != null && cards[selectedKey]?.getOrNull(slot.second) != null,
                    ) { Text("Clear") }
                }
                if (onDeclare != null) {
                    Button(
                        onClick = { onDeclare(winners) },
                        enabled = winners.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (winners.isEmpty()) "Enter every card first" else declareText(winners)) }
                }
            }
        }
    }
}

private fun firstEmpty(seats: List<HandSeat>, cards: Map<String, List<Card?>>): Pair<Int, Int>? {
    seats.forEachIndexed { index, seat ->
        val i = cards[seat.key]?.indexOfFirst { it == null } ?: -1
        if (i >= 0) return index to i
    }
    return null
}

@Composable
private fun VerdictCard(seats: List<HandSeat>, verdict: Result<Verdict<String>>?) {
    val colors = MaterialTheme.colorScheme
    val result = verdict?.getOrNull()
    val error = (verdict?.exceptionOrNull() as? GameRuleException)?.message
    val (container, content) = when {
        error != null -> colors.errorContainer to colors.onErrorContainer
        result != null -> colors.primary to colors.onPrimary
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                error != null -> Text(error, fontWeight = FontWeight.SemiBold)
                result != null -> {
                    val names = result.winners.map { key -> seats.first { it.key == key }.label }
                    Text(
                        if (names.size == 1) "${names[0]} wins" else "${names.joinToString(" and ")} tie",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(result.hands.getValue(result.winners.first()).name, style = MaterialTheme.typography.bodyMedium)
                    result.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                else -> Text("Enter every card to see who wins.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CardSlot(card: Card?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width = 40.dp, height = 56.dp)
            .background(if (card != null) Color.White else colors.surfaceVariant, RoundedCornerShape(6.dp))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) colors.secondary else colors.outlineVariant,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (card == null) {
            Text("?", color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val ink = if (card.suit.red) RedSuit else BlackSuit
                Text(rankLabel(card.rank), color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 17.sp)
                Text(card.suit.symbol, color = ink, fontSize = 16.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun PickerKey(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    color: Color?,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width = if (wide) 52.dp else 40.dp, height = 44.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .background(if (selected) colors.secondaryContainer else colors.surface, RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.secondary else colors.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color?.let { if (it == BlackSuit) colors.onSurface else it } ?: colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = if (wide) 22.sp else 16.sp,
        )
    }
}
