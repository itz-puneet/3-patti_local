package com.threepatti.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threepatti.core.GameState
import com.threepatti.core.HandStatus
import com.threepatti.core.Player
import com.threepatti.core.QrCode

/** Three fanned playing cards, used as the app's logo. */
@Composable
fun CardFan(modifier: Modifier = Modifier) {
    val cards = listOf(Triple(-16f, "K", "♣"), Triple(0f, "Q", "♦"), Triple(16f, "A", "♠"))
    Box(modifier.size(width = 150.dp, height = 104.dp), contentAlignment = Alignment.Center) {
        cards.forEachIndexed { index, (angle, rank, suit) ->
            PlayingCard(
                rank = rank,
                suit = suit,
                modifier = Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = angle
                    translationX = (index - 1) * 26.dp.toPx()
                    translationY = if (index == 1) -4.dp.toPx() else 0f
                },
            )
        }
    }
}

@Composable
private fun PlayingCard(rank: String, suit: String, modifier: Modifier = Modifier) {
    val red = suit == "♥" || suit == "♦"
    val ink = if (red) Color(0xFFC62828) else Color(0xFF1B1B1B)
    Surface(
        modifier = modifier.size(width = 56.dp, height = 80.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color(0x22000000)),
    ) {
        Box(Modifier.padding(6.dp)) {
            Column(Modifier.align(Alignment.TopStart), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rank, color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 15.sp)
                Text(suit, color = ink, fontSize = 12.sp, lineHeight = 12.sp)
            }
            Text(suit, color = ink, fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** A QR code, always black on white so phone cameras can read it in dark mode too. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { QrCode.encode(text) }
    Canvas(modifier.aspectRatio(1f).background(Color.White, RoundedCornerShape(8.dp))) {
        // Keep a quiet zone of 4 modules around the code, as scanners expect.
        val cell = size.minDimension / (qr.size + 8)
        val start = cell * 4
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) {
                    drawRect(
                        Color.Black,
                        topLeft = Offset(start + x * cell, start + y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun Initial(name: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(38.dp)
            .background(if (highlighted) colors.primary else colors.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = if (highlighted) colors.onPrimary else colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(7)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
fun NumberedSteps(steps: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

enum class PillTone { Primary, Secondary, Tertiary, Muted, Error }

/** Short status for a player's row, such as Blind, Seen or Packed. */
fun playerStatus(state: GameState, player: Player): Pair<String, PillTone> {
    val round = state.round
    val hand = round?.hand(player.id)
    val poker = state.settings.isPoker
    val folded = if (poker) "Folded" else "Packed"
    return when {
        round != null && round.isActive && hand != null -> when {
            hand.status == HandStatus.PACKED -> folded to PillTone.Muted
            hand.allIn -> "All-in" to PillTone.Error
            hand.status == HandStatus.BLIND -> "Blind" to PillTone.Secondary
            hand.status == HandStatus.SEEN -> "Seen" to PillTone.Tertiary
            else -> "In" to PillTone.Tertiary
        }
        round != null && !round.isActive && player.id in round.winnerIds -> "Won" to PillTone.Primary
        round != null && !round.isActive && hand != null ->
            (if (hand.status == HandStatus.PACKED) folded else "Lost") to PillTone.Muted
        player.sittingOut -> "Sitting out" to PillTone.Muted
        round != null && round.isActive -> "Next ${state.roundWord}" to PillTone.Muted
        poker && player.balance == 0 -> "Needs chips" to PillTone.Error
        !poker && player.balance < state.settings.bootAmount -> "Needs chips" to PillTone.Error
        else -> "Ready" to PillTone.Muted
    }
}

@Composable
fun toneColors(tone: PillTone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        PillTone.Primary -> c.primary to c.onPrimary
        PillTone.Secondary -> c.secondaryContainer to c.onSecondaryContainer
        PillTone.Tertiary -> c.tertiaryContainer to c.onTertiaryContainer
        PillTone.Muted -> c.surfaceVariant to c.onSurfaceVariant
        PillTone.Error -> c.errorContainer to c.onErrorContainer
    }
}
