package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import com.threepatti.core.Player
import com.threepatti.core.PokerOptions
import com.threepatti.core.PokerRules
import com.threepatti.core.Round
import com.threepatti.core.namesOf
import kotlin.math.roundToInt

@Composable
internal fun PokerBetting(
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
    val o = PokerRules.options(state, seatId)
    val forMe = seatId == myId
    val seatName = state.nameOf(seatId)
    val headline = when {
        !forMe -> "Playing for $seatName"
        o.isTurn -> "Your turn"
        else -> "Waiting for ${state.nameOf(round.turnId)}"
    }
    val detail = if (o.inHand) {
        "${state.money(o.stack)} left" + if (o.streetBet > 0) " · bet ${state.money(o.streetBet)}" else ""
    } else {
        null
    }
    Header(headline, detail, takeOverOffer, takingOver, onToggleTakeOver)
    when {
        !o.inHand -> Hint(if (forMe) "You are not in this hand. You'll be dealt in next hand." else "$seatName is not in this hand.")
        o.folded -> Hint(if (forMe) "You folded. Wait for the next hand." else "$seatName folded.")
        o.allIn -> Hint(if (forMe) "You are all-in. Wait for the showdown." else "$seatName is all-in.")
        o.isTurn -> {
            var raising by remember(round.number, round.street, seatId, o.minRaiseTo) { mutableStateOf(false) }
            if (raising && o.canRaise) {
                RaiseControls(state, o, onCancel = { raising = false }) { onAction(GameAction.RaiseTo(seatId, it)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Fold", danger = true) { onOpen(GameDialog.ConfirmPack(seatId)) }
                    if (o.canCheck) {
                        BigButton(label = "Check", amount = null, enabled = true, onClick = { onAction(GameAction.Check(seatId)) })
                    }
                    if (o.canCall) {
                        BigButton(
                            label = if (o.callIsAllIn) "Call all-in" else "Call",
                            amount = state.money(o.toCall),
                            enabled = true,
                            onClick = { onAction(GameAction.Call(seatId)) },
                        )
                    }
                    if (o.canRaise) {
                        BigButton(
                            label = if (o.isBet) "Bet" else "Raise",
                            amount = "${state.money(o.minRaiseTo)}+".takeIf { o.maxRaiseTo > o.minRaiseTo }
                                ?: state.money(o.minRaiseTo),
                            enabled = true,
                            secondary = true,
                            onClick = { raising = true },
                        )
                    }
                }
            }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Fold", danger = true) { onOpen(GameDialog.ConfirmPack(seatId)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaiseControls(state: GameState, o: PokerOptions, onCancel: () -> Unit, onRaise: (Int) -> Unit) {
    val min = o.minRaiseTo
    val max = o.maxRaiseTo
    val allInTo = o.streetBet + o.stack
    var amount by remember(min, max) { mutableIntStateOf(min) }
    val step = state.settings.bigBlind.coerceAtLeast(1)
    val verb = if (o.isBet) "Bet" else "Raise to"

    if (max > min) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { amount = (amount - step).coerceAtLeast(min) },
                enabled = amount > min,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(48.dp),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Slider(
                value = amount.toFloat(),
                onValueChange = { value ->
                    // Snap to whole big blinds, but always allow the exact minimum and maximum.
                    val snapped = min + (((value - min) / step).roundToInt() * step)
                    amount = if (value >= max - step / 2f) max else snapped.coerceIn(min, max)
                },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { amount = (amount + step).coerceAtMost(max) },
                enabled = amount < max,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(48.dp),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        val quick = listOf(
            "Min" to min,
            "½ pot" to o.halfPotRaiseTo,
            "Pot" to o.potRaiseTo,
            (if (max == allInTo) "All-in" else "Max") to max,
        ).distinctBy { it.second }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEach { (label, value) ->
                FilterChip(selected = amount == value, onClick = { amount = value }, label = { Text(label) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton("Back", onClick = onCancel)
        Button(
            onClick = { onRaise(amount) },
            modifier = Modifier.weight(2f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Text(
                "$verb ${state.money(amount)}" + if (amount == allInTo) " all-in" else "",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PokerShowdown(state: GameState, round: Round, isHost: Boolean, onOpen: (GameDialog) -> Unit) {
    val index = round.potWinners.size
    val pot = round.pots.getOrNull(index) ?: return
    val label = PokerRules.potLabel(round, index)
    Text(
        "Showdown · $label ${state.money(pot.amount)}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (!isHost) {
        Hint("Cards are on the table. Waiting for the host to enter who won${if (round.pots.size > 1) " each pot" else ""}.")
        return
    }
    var selected by remember(round.number, index) { mutableStateOf(setOf<String>()) }
    Hint(
        if (round.pots.size > 1) {
            "Only ${state.namesOf(pot.eligibleIds)} can win this pot. Tap the best hand among them."
        } else {
            "Tap the winner. Tap more than one only if hands are exactly equal to split the pot."
        },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pot.eligibleIds.forEach { id ->
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
    val winners = pot.eligibleIds.filter { it in selected }
    Button(
        onClick = { onOpen(GameDialog.ConfirmWinners(winners)) },
        enabled = winners.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(
            when (winners.size) {
                0 -> "Pick the winner"
                1 -> "Give ${state.money(pot.amount)} to ${state.nameOf(winners[0])}"
                else -> "Split ${state.money(pot.amount)} between ${winners.size}"
            },
        )
    }
}
