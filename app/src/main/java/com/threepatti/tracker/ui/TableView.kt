package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threepatti.core.GameState
import com.threepatti.core.HandStatus
import com.threepatti.core.Player
import com.threepatti.core.Round
import com.threepatti.core.RoundPhase
import com.threepatti.core.describeRound
import com.threepatti.core.nextDealText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Rail = Color(0xFF4A2F1B)
private val RailEdge = Color(0xFF2E1C10)
private val FeltCentre = Color(0xFF237A51)
private val FeltEdge = Color(0xFF15553A)
private val OnFelt = Color(0xFFF2FAF4)

/**
 * The players seated round a card table in turn order, with the pot in the middle. You sit at the
 * bottom and play passes clockwise, so the player after you is on your left. The table shrinks to
 * [fitHeight] when the seats still have room, so your own seat stays above the buttons.
 */
@Composable
fun TableView(
    state: GameState,
    myId: String,
    canOpen: (String) -> Boolean,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fitHeight: Dp = Dp.Infinity,
) {
    val seats = remember(state.players, myId) { fromMySeat(state.players, myId) }
    val compact = seats.size > 8
    val seatWidth = if (compact) 80.dp else 92.dp
    val seatHeight = if (compact) 106.dp else 114.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        val ideal = width * when {
            seats.size <= 4 -> 1.05f
            seats.size <= 6 -> 1.2f
            seats.size <= 8 -> 1.35f
            seats.size <= 10 -> 1.55f
            else -> 1.75f
        }
        val least = when {
            seats.size <= 4 -> 330.dp
            seats.size <= 6 -> 380.dp
            seats.size <= 8 -> 420.dp
            seats.size <= 10 -> 500.dp
            else -> 580.dp
        }.coerceAtMost(ideal)
        val height = ideal.coerceAtMost(fitHeight).coerceAtLeast(least)
        // Seat centres sit on the rail; the rail is drawn just outside them.
        val rx = (width - seatWidth) / 2
        val ry = (height - seatHeight) / 2
        val spots = remember(seats.size, rx, ry) { ovalSpots(seats.size, rx.value, ry.value) }
        Box(Modifier.fillMaxWidth().height(height)) {
            Felt(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = width / 2 - rx - 10.dp, vertical = height / 2 - ry - 10.dp),
            )
            TableCentre(
                state,
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = (rx * 2 - seatWidth - 16.dp).coerceAtLeast(120.dp)),
            )
            seats.forEachIndexed { index, player ->
                val (x, y) = spots[index]
                Seat(
                    state = state,
                    player = player,
                    isMe = player.id == myId,
                    onClick = if (canOpen(player.id)) ({ onPlayerClick(player.id) }) else null,
                    compact = compact,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x.dp, y.dp)
                        .size(seatWidth, seatHeight),
                )
            }
        }
    }
}

/** Seat order starting from [myId], so that seat is drawn at the bottom. */
private fun fromMySeat(players: List<Player>, myId: String): List<Player> {
    val mine = players.indexOfFirst { it.id == myId }
    return if (mine <= 0) players else players.drop(mine) + players.take(mine)
}

/**
 * [count] points spread evenly along an oval with radii [rx] and [ry], starting at the bottom and
 * going clockwise on screen. Spacing is by distance along the edge, so seats don't bunch up.
 */
private fun ovalSpots(count: Int, rx: Float, ry: Float): List<Pair<Float, Float>> {
    if (count == 0) return emptyList()
    val steps = 720
    val points = (0..steps).map { i ->
        val t = PI / 2 + 2 * PI * i / steps
        (rx * cos(t)).toFloat() to (ry * sin(t)).toFloat()
    }
    val along = FloatArray(points.size)
    for (i in 1 until points.size) {
        val (x0, y0) = points[i - 1]
        val (x1, y1) = points[i]
        along[i] = along[i - 1] + hypot(x1 - x0, y1 - y0)
    }
    val total = along.last()
    var i = 0
    return (0 until count).map { seat ->
        val target = total * seat / count
        while (i < points.lastIndex && along[i] < target) i++
        points[i]
    }
}

@Composable
private fun Felt(modifier: Modifier) {
    Canvas(modifier) {
        val rail = 14.dp.toPx()
        drawOval(RailEdge, topLeft = Offset.Zero, size = size)
        drawOval(Rail, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()))
        val felt = Size(size.width - rail * 2, size.height - rail * 2)
        drawOval(
            Brush.radialGradient(
                listOf(FeltCentre, FeltEdge),
                center = center,
                radius = maxOf(felt.width, felt.height) / 2,
            ),
            topLeft = Offset(rail, rail),
            size = felt,
        )
        // A faint line inside the felt, like the betting line on a real table.
        val inset = rail + 16.dp.toPx()
        drawOval(
            OnFelt.copy(alpha = 0.12f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableCentre(state: GameState, modifier: Modifier) {
    val round = state.round
    val dim = OnFelt.copy(alpha = 0.78f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (round == null) {
            Text(
                if (state.settings.isPoker) "No hand being played" else "No round running",
                color = OnFelt,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Everyone starts with ${state.money(state.settings.startingBalance)}",
                color = dim,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            NextDeal(state)
            return@Column
        }
        val word = state.roundWord.uppercase()
        Text(
            if (round.isActive) "$word ${round.number} · POT" else "$word ${round.number} FINISHED",
            color = dim,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
        )
        val pot = state.money(round.pot)
        Text(
            pot,
            color = OnFelt,
            fontSize = when {
                pot.length <= 5 -> 34.sp
                pot.length <= 7 -> 28.sp
                else -> 24.sp
            },
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        if (round.isActive) {
            val stakes = if (state.settings.isPoker) {
                listOfNotNull(
                    "Blinds ${state.money(state.settings.smallBlind)}/${state.money(state.settings.bigBlind)}",
                    if (state.settings.ante > 0) "Ante ${state.money(state.settings.ante)}" else null,
                    if (round.phase == RoundPhase.BETTING) "Bet ${state.money(round.currentBet)}" else null,
                )
            } else {
                listOf("Blind ${state.money(round.stake)}", "Chaal ${state.money(round.stake * 2)}")
            }
            // Each stake wraps as a whole, so a line never ends in a separator.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                stakes.forEach { Text(it, color = dim, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            }
        }
        Text(
            describeRound(state, round),
            color = OnFelt,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!round.isActive) NextDeal(state)
    }
}

@Composable
private fun NextDeal(state: GameState) {
    val text = nextDealText(state) ?: return
    Text(
        text,
        color = OnFelt,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private fun Round.waitingOn(playerId: String): Boolean =
    isActive && (
        (phase == RoundPhase.BETTING && turnId == playerId) ||
            (phase == RoundPhase.SIDE_SHOW_REQUESTED && sideShow?.targetId == playerId)
        )

/** The seat's status, with the chips they have put in this round when it's running. */
private fun seatStatus(state: GameState, player: Player): Pair<String, PillTone> {
    val (label, tone) = playerStatus(state, player)
    val round = state.round?.takeIf { it.isActive } ?: return label to tone
    val hand = round.hand(player.id)?.takeIf { it.status != HandStatus.PACKED } ?: return label to tone
    return when {
        !state.settings.isPoker -> "$label · ${state.money(hand.invested)}" to tone
        hand.streetBet > 0 -> (if (hand.allIn) "All-in ${state.money(hand.streetBet)}" else "Bet ${state.money(hand.streetBet)}") to tone
        else -> label to tone
    }
}

@Composable
private fun Seat(
    state: GameState,
    player: Player,
    isMe: Boolean,
    onClick: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val round = state.round
    val hand = round?.hand(player.id)
    val turn = round?.waitingOn(player.id) == true
    val won = round != null && !round.isActive && player.id in round.winnerIds
    val out = when {
        round?.isActive == true -> hand == null || hand.status == HandStatus.PACKED
        else -> player.sittingOut
    }
    val avatar: Dp = if (compact) 36.dp else 42.dp
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .alpha(if (out) 0.5f else 1f)
                .then(
                    if (onClick != null) {
                        // Not clipped to the seat, so a long status label is never cut off.
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(avatar + 8.dp), contentAlignment = Alignment.Center) {
                if (turn) Box(Modifier.size(avatar + 8.dp).background(colors.secondary.copy(alpha = 0.35f), CircleShape))
                Box(
                    Modifier
                        .size(avatar)
                        .background(if (turn) colors.secondary else if (isMe) colors.primary else colors.surfaceContainerHighest, CircleShape)
                        .border(2.dp, if (turn) colors.secondaryContainer else if (won) colors.primary else RailEdge.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        player.name.firstOrNull()?.uppercase() ?: "?",
                        color = if (turn) colors.onSecondary else if (isMe) colors.onPrimary else colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 15.sp else 17.sp,
                    )
                }
                if (round?.isActive == true && round.dealerId == player.id) {
                    Marker("D", Color.White, Color(0xFF1B1B1B), Modifier.align(Alignment.BottomEnd))
                }
                if (round?.isActive == true && state.settings.isPoker) {
                    val blind = when (player.id) {
                        round.smallBlindId -> "SB"
                        round.bigBlindId -> "BB"
                        else -> null
                    }
                    if (blind != null) Marker(blind, colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.align(Alignment.BottomStart))
                }
                if (!player.isHost && player.hasDevice && !player.connected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .background(colors.error, CircleShape)
                            .border(2.dp, colors.surface, CircleShape),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isMe) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (isMe) colors.onPrimaryContainer else colors.onSurface,
                border = when {
                    turn -> BorderStroke(2.dp, colors.secondary)
                    won -> BorderStroke(2.dp, colors.primary)
                    else -> null
                },
                shadowElevation = 2.dp,
                modifier = Modifier.padding(top = 2.dp).widthIn(max = 92.dp),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isMe) "You" else player.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.money(player.balance),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) colors.onPrimaryContainer else colors.primary,
                        maxLines = 1,
                    )
                }
            }
            val (label, tone) = seatStatus(state, player)
            val (container, content) = toneColors(tone)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .wrapContentWidth(unbounded = true)
                    .background(container, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/** A small round marker on the avatar, such as the dealer button. */
@Composable
private fun Marker(text: String, container: Color, content: Color, modifier: Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(container, CircleShape)
            .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = if (text.length > 1) 8.sp else 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
    }
}
