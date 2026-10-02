package com.threepatti.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.threepatti.core.Card
import com.threepatti.core.GameState
import com.threepatti.core.HandRules
import com.threepatti.core.Round
import com.threepatti.core.Rules
import com.threepatti.core.TeenPatti
import com.threepatti.core.Variant
import com.threepatti.core.enteredCards
import com.threepatti.core.handName
import com.threepatti.core.handRules
import com.threepatti.core.namesOf

// 3 Patti players can enter their own cards. They stay on their phone until a side show or show,
// when the app works out who won and the host confirms.

/** Who this phone types cards in for: its own seat, and at the host's, players without a phone. */
internal fun GameState.entersCardsFor(playerId: String, myId: String, isHost: Boolean): Boolean =
    playerId == myId || (isHost && player(playerId)?.hasDevice == false)

/**
 * The card picker for a player's own 3 cards, full screen: one tap per card. Only that player's phone
 * shows them until a side show or show.
 */
@Composable
fun MyCardsDialog(
    title: String,
    hint: String,
    initial: List<Card>?,
    rules: HandRules,
    dismissLabel: String,
    onSave: (List<Card>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            MyCardsContent(title, hint, initial, rules, dismissLabel, onSave, onDismiss)
        }
    }
}

@Composable
fun MyCardsContent(
    title: String,
    hint: String,
    initial: List<Card>?,
    rules: HandRules,
    dismissLabel: String,
    onSave: (List<Card>) -> Unit,
    onDismiss: () -> Unit,
) {
    var cards by remember { mutableStateOf<List<Card?>>(initial ?: List(3) { null }) }
    // The card the next tap fills. Null once all 3 are in.
    var slot by remember { mutableStateOf(cards.indexOfFirst { it == null }.takeIf { it >= 0 }) }
    val complete = cards.all { it != null }
    val hand = remember(cards, rules) {
        if (complete) runCatching { TeenPatti.best(cards.filterNotNull(), rules) }.getOrNull() else null
    }
    fun pick(card: Card) {
        // Tapping a card that's in already takes it back out.
        val at = cards.indexOf(card)
        if (at >= 0) {
            cards = cards.toMutableList().also { it[at] = null }
            slot = at
            return
        }
        val index = slot ?: return
        cards = cards.toMutableList().also { it[index] = card }
        slot = cards.indexOfFirst { it == null }.takeIf { it >= 0 }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = dismissLabel) }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            // The cards sit at the bottom, within reach of a thumb; on a small screen everything scrolls.
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Hint(hint)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        cards.forEachIndexed { i, card ->
                            CardSlot(card, wild = card != null && card.rank in rules.wildRanks, selected = slot == i) { slot = i }
                        }
                    }
                    Text(
                        hand?.name ?: slot?.let { "Tap the ${ordinal(it)} card" }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hand != null) FontWeight.Bold else null,
                        color = if (hand != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CardGrid(
                    picked = cards.filterNotNull().toSet(),
                    wildRanks = rules.wildRanks,
                    onTap = ::pick,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text(dismissLabel) }
                Button(
                    onClick = { onSave(cards.filterNotNull()) },
                    enabled = complete,
                    modifier = Modifier.weight(2f).height(52.dp),
                ) { Text(if (complete) "Save" else "Tap ${cards.count { it == null }} more") }
            }
        }
    }
}

/**
 * The seat's entered cards, small, or a button to enter them once seen. Tapping the cards changes them
 * until they've been shown, when they lock.
 */
@Composable
internal fun SeatCards(state: GameState, round: Round, seatId: String, myId: String, isHost: Boolean, onOpen: (GameDialog) -> Unit) {
    if (state.settings.isPoker || !state.entersCardsFor(seatId, myId, isHost)) return
    val hand = round.hand(seatId) ?: return
    val canEnter = Rules.enterCardsError(state, seatId) == null
    val cards = hand.enteredCards()
    val open = { onOpen(GameDialog.EnterCards(seatId, round.number)) }
    when {
        cards != null -> Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = canEnter, onClick = open)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MiniHand(cards, state.settings.handRules)
            val icon = when {
                canEnter -> Icons.Filled.Edit to "Change cards"
                hand.cardsShown -> Icons.Filled.Lock to "Shown, so they can't change"
                else -> null
            }
            if (icon != null) {
                Icon(
                    icon.first,
                    contentDescription = icon.second,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        canEnter -> TextButton(onClick = open) { Text("Enter cards") }
    }
}

/** A button to enter the cards of each player in this side show or show that this phone types in for. */
@Composable
internal fun EnterCardsPrompt(state: GameState, round: Round, myId: String, isHost: Boolean, onOpen: (GameDialog) -> Unit) {
    if (state.settings.isPoker) return
    Rules.comparedIds(round)
        .filter { id ->
            state.entersCardsFor(id, myId, isHost) && round.hand(id)?.cards.isNullOrEmpty() && Rules.enterCardsError(state, id) == null
        }
        .forEach { id ->
            OutlinedButton(onClick = { onOpen(GameDialog.EnterCards(id, round.number)) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (id == myId) "Enter your cards so the app can check" else "Enter ${state.nameOf(id)}'s cards")
            }
        }
}

/**
 * What the entered cards say about the side show or pot being decided: the hand of each player this
 * phone can see, and who wins once everyone in it has entered their cards.
 */
@Composable
internal fun SuggestionBox(state: GameState, round: Round, myId: String) {
    if (state.settings.isPoker) return
    val named = Rules.comparedIds(round).mapNotNull { id -> round.hand(id)?.let { state.handName(it) }?.let { id to it } }
    val winners = round.suggestedWinnerIds
    val note = round.suggestionNote
    val awaiting = round.awaitingCardsFrom
    if (named.isEmpty() && winners.isEmpty() && note == null && awaiting.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (winners.isNotEmpty()) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (winners.isNotEmpty()) colors.onPrimaryContainer else colors.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (winners.isNotEmpty()) {
                val mine = winners == listOf(myId)
                val who = if (mine) "you win" else "${state.nameOf(winners[0])} wins"
                Text(
                    when {
                        winners.size > 1 -> "The cards say ${state.namesOf(winners)} tie"
                        state.settings.variant == Variant.MUFLIS -> "The cards say $who with the lowest hand"
                        else -> "The cards say $who"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            named.forEach { (id, name) ->
                Text(
                    "${if (id == myId) "You" else state.nameOf(id)}: $name",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (id in winners) FontWeight.SemiBold else null,
                )
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
            if (awaiting.isNotEmpty()) {
                val names = awaiting.map { if (it == myId) "you" else state.nameOf(it) }
                val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString() + " and " + names.last()
                Text("Waiting for cards from $list", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The host's one tap to confirm what the cards say, with a way to pick the winner by hand instead. While
 * some players' cards are still [awaited] the button waits for them. Without either, the manual choice
 * shows straight away.
 */
@Composable
internal fun ConfirmOrPick(
    confirmText: String?,
    awaited: Boolean,
    resetKey: Any?,
    onConfirm: () -> Unit,
    manual: @Composable () -> Unit,
) {
    var picking by remember(resetKey) { mutableStateOf(false) }
    if ((confirmText == null && !awaited) || picking) {
        manual()
        return
    }
    Button(onClick = onConfirm, enabled = confirmText != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(confirmText ?: "Waiting for cards")
    }
    TextButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick the winner yourself") }
}

/** "Give 40 chips to Ravi" or "Split 40 chips between Ravi and Meena". */
internal fun giveText(state: GameState, amount: Int, winners: List<String>): String =
    if (winners.size == 1) {
        "Give ${state.chipCount(amount)} to ${state.nameOf(winners[0])}"
    } else {
        "Split ${state.chipCount(amount)} between ${state.namesOf(winners)}"
    }
