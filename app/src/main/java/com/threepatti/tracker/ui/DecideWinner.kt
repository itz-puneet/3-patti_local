package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threepatti.core.Card
import com.threepatti.core.GameRuleException
import com.threepatti.core.HandRules
import com.threepatti.core.Suit
import com.threepatti.core.TeenPatti
import com.threepatti.core.Variant
import com.threepatti.core.Verdict
import com.threepatti.core.rankLabel

/** One hand on the Decide winner screen: a player in the show, or "Hand 3" when used on its own. */
data class HandSeat(val key: String, val label: String)

private const val MAX_HANDS = 6
private val RedSuit = Color(0xFFC62828)
private val BlackSuit = Color(0xFF1B1B1B)
private val JokerMark = Color(0xFFC58B00)

@Composable
fun DecideWinnerDialog(
    title: String,
    seats: List<HandSeat>,
    sideShowAsker: String?,
    canAddHands: Boolean,
    declareText: (winners: List<String>) -> String,
    onDeclare: ((winners: List<String>) -> Unit)?,
    onDismiss: () -> Unit,
    initialCards: Map<String, List<Card?>> = emptyMap(),
    initialVariant: Variant = Variant.CLASSIC,
    initialJokerRanks: Set<Int> = emptySet(),
) {
    FullScreenDialog(onDismissRequest = onDismiss) {
        DecideWinnerContent(
            title, seats, sideShowAsker, canAddHands, declareText, onDeclare, onDismiss,
            initialCards, initialVariant, initialJokerRanks,
        )
    }
}

/**
 * Players enter their 3 cards and the app ranks the hands, for when the table can't agree who won a
 * show or side show. With [onDeclare] (the host, during a show) the result can be entered straight away.
 * It starts with the cards players entered themselves, where this phone can see them, and the table's variant.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    initialVariant: Variant = Variant.CLASSIC,
    initialJokerRanks: Set<Int> = emptySet(),
) {
    var variant by remember { mutableStateOf(initialVariant) }
    var jokerRanks by remember { mutableStateOf(initialJokerRanks) }
    val rules = HandRules.of(variant, jokerRanks)
    var hands by remember { mutableStateOf(seats) }
    var cards by remember { mutableStateOf(seats.associate { it.key to (initialCards[it.key] ?: List(3) { null }) }) }
    // Where the next card goes: a hand and a card in it. Null once every card is in.
    var slot by remember { mutableStateOf(firstEmpty(seats, cards)) }
    val complete = hands.all { seat -> cards.getValue(seat.key).all { it != null } }
    // Jokers are tried as every card, so work the hands out only when something changes.
    val verdict: Result<Verdict<String>>? = remember(hands, cards, rules) {
        if (complete) {
            runCatching { TeenPatti.decide(hands.associate { it.key to cards.getValue(it.key).filterNotNull() }, sideShowAsker, rules) }
        } else {
            null
        }
    }
    // Each hand is named as soon as its own 3 cards are in, before the other hands are complete.
    val named = remember(cards, rules) {
        cards.mapValues { (_, list) ->
            list.takeIf { all -> all.all { it != null } }?.let { runCatching { TeenPatti.best(it.filterNotNull(), rules) }.getOrNull() }
        }
    }
    val winners = verdict?.getOrNull()?.winners.orEmpty()

    fun pick(card: Card) {
        // A card that's already in a hand comes back out, so a wrong tap is undone by tapping it again.
        val owner = hands.indexOfFirst { card in cards.getValue(it.key) }
        if (owner >= 0) {
            val key = hands[owner].key
            val index = cards.getValue(key).indexOf(card)
            cards = cards + (key to cards.getValue(key).toMutableList().also { it[index] = null })
            slot = owner to index
            return
        }
        val (hand, index) = slot ?: return
        val key = hands[hand].key
        cards = cards + (key to cards.getValue(key).toMutableList().also { it[index] = card })
        slot = firstEmpty(hands, cards)
    }
    // Keep the hand the next card goes into in sight above the cards.
    val nextHand = remember { BringIntoViewRequester() }
    LaunchedEffect(slot?.first) { if (slot != null) nextHand.bringIntoView() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Hint("Tap each hand's 3 cards below. The app ranks the hands by 3 Patti rules.")
            VariantPicker(variant = variant, jokerRanks = jokerRanks, onVariant = { variant = it }, onJokerRanks = { jokerRanks = it })
            hands.forEachIndexed { index, seat ->
                val list = cards.getValue(seat.key)
                val hand = named[seat.key]
                val won = seat.key in winners
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = if (won) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth().then(
                        if (slot?.first == index) Modifier.bringIntoViewRequester(nextHand) else Modifier,
                    ),
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
                                CardSlot(card, wild = card != null && card.rank in rules.wildRanks, selected = slot == index to i) {
                                    slot = index to i
                                }
                            }
                        }
                        if (canAddHands && hands.size > 2) {
                            IconButton(onClick = {
                                hands = hands - seat
                                cards = cards - seat.key
                                slot = firstEmpty(hands, cards)
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
                    slot = firstEmpty(hands, cards)
                }) { Text("Add a hand") }
            }
            VerdictCard(hands, verdict, rules.lowestWins)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = slot
                if (next == null) {
                    // Every card is in: the cards make way for the hands and the verdict.
                    Hint("Every card is in. Tap a card above to change it.", center = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Hint("Tap ${hands[next.first].label}'s ${ordinal(next.second)} card", center = true, modifier = Modifier.fillMaxWidth())
                    CardGrid(
                        picked = cards.values.flatten().filterNotNull().toSet(),
                        wildRanks = rules.wildRanks,
                        onTap = ::pick,
                        cellHeight = 40.dp,
                    )
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

/**
 * All 52 cards, a row for each rank with its 4 suits: aces to 8s on the left, 7s to 2s on the right. One
 * tap enters a card. Cards already entered ([picked]) are highlighted; tapping one takes it back out.
 */
@Composable
internal fun CardGrid(
    picked: Set<Card>,
    wildRanks: Set<Int>,
    onTap: (Card) -> Unit,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 44.dp,
) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(14 downTo 8, 7 downTo 2).forEach { ranks ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ranks.forEach { rank ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Suit.entries.forEach { suit ->
                            val card = Card(rank, suit)
                            GridCard(card, card in picked, rank in wildRanks, Modifier.weight(1f).height(cellHeight)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTap(card)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCard(card: Card, picked: Boolean, wild: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    // A lighter red on a dark screen, so hearts and diamonds stay easy to read.
    val red = if (colors.surface.luminance() < 0.5f) Color(0xFFFF8A80) else RedSuit
    val ink = when {
        picked -> colors.onSecondary
        card.suit.red -> red
        else -> colors.onSurface
    }
    Box(
        modifier
            .clip(shape)
            .background(if (picked) colors.secondary else colors.surface)
            .border(1.dp, if (picked) colors.secondary else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = spokenCard(card) + if (picked) ", entered" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(rankLabel(card.rank) + card.suit.symbol, color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
        if (wild) {
            Text(
                "★",
                color = if (picked) colors.onSecondary else JokerMark,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 2.dp),
            )
        }
    }
}

/** Such as "Ace of spades", for screen readers. */
private fun spokenCard(card: Card): String {
    val rank = when (card.rank) {
        14 -> "Ace"
        13 -> "King"
        12 -> "Queen"
        11 -> "Jack"
        else -> card.rank.toString()
    }
    return "$rank of ${card.suit.name.lowercase()}"
}

/** "1st", "2nd" or "3rd" card of a hand, from its index. */
internal fun ordinal(index: Int): String = when (index) {
    0 -> "1st"
    1 -> "2nd"
    else -> "3rd"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariantPicker(
    variant: Variant,
    jokerRanks: Set<Int>,
    onVariant: (Variant) -> Unit,
    onJokerRanks: (Set<Int>) -> Unit,
) {
    // Chips wrap on narrow screens, such as inside the settings dialog.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Variant.entries.forEach { v ->
            FilterChip(selected = v == variant, onClick = { onVariant(v) }, label = { Text(v.label, maxLines = 1) })
        }
    }
    Hint(
        when {
            variant == Variant.JOKER && jokerRanks.isEmpty() -> "${variant.description}. Pick the joker rank:"
            variant == Variant.JOKER -> "${variant.description} (★):"
            variant == Variant.AK47 -> "${variant.description} (★)."
            else -> "${variant.description}."
        },
    )
    if (variant == Variant.JOKER) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            (14 downTo 2).forEach { r ->
                PickerKey(rankLabel(r), selected = r in jokerRanks, enabled = true, color = null) {
                    onJokerRanks(if (r in jokerRanks) jokerRanks - r else jokerRanks + r)
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
private fun VerdictCard(seats: List<HandSeat>, verdict: Result<Verdict<String>>?, lowestWins: Boolean) {
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
                        when {
                            names.size > 1 -> "${names.joinToString(" and ")} tie"
                            lowestWins -> "${names[0]} wins with the lowest hand"
                            else -> "${names[0]} wins"
                        },
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
internal fun CardSlot(card: Card?, wild: Boolean, selected: Boolean, onClick: () -> Unit) {
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
            if (wild) {
                Text("★", color = JokerMark, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp))
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

/** A small face-up card, for cards shown on the table and in the action panel. Sized in dp so it never clips. */
@Composable
internal fun MiniCard(card: Card, wild: Boolean = false, width: Dp = 22.dp) {
    val ink = if (card.suit.red) RedSuit else BlackSuit
    val text = with(LocalDensity.current) { (width * 0.5f).toSp() }
    Box(
        Modifier
            .size(width = width, height = width * 1.38f)
            .background(Color.White, RoundedCornerShape(width * 0.18f))
            .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(width * 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rankLabel(card.rank), color = ink, fontWeight = FontWeight.Bold, fontSize = text, lineHeight = text)
            Text(card.suit.symbol, color = ink, fontSize = text, lineHeight = text)
        }
        if (wild) {
            Text(
                "★",
                color = JokerMark,
                fontSize = text * 0.7f,
                lineHeight = text * 0.7f,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 1.dp),
            )
        }
    }
}

/** A hand of [cards] as small cards side by side, with jokers starred. */
@Composable
internal fun MiniHand(cards: List<Card>, rules: HandRules, modifier: Modifier = Modifier, width: Dp = 22.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cards.forEach { MiniCard(it, wild = it.rank in rules.wildRanks, width = width) }
    }
}
