package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threepatti.core.AnteStyle
import com.threepatti.core.BetLimit
import com.threepatti.core.GameType
import com.threepatti.core.TableSettings

/** Settings as typed in text fields. Empty optional fields mean "no limit". */
data class SettingsInput(
    val game: GameType,
    val startingBalance: String,
    val boot: String,
    val chaalLimit: String,
    val potLimit: String,
    val blindLimit: String,
    val smallBlind: String,
    val bigBlind: String,
    val betLimit: BetLimit,
    val ante: String,
    val anteStyle: AnteStyle,
    val currency: String,
) {
    fun toSettings(): TableSettings? {
        val defaults = TableSettings()
        val start = startingBalance.toIntOrNull() ?: return null
        val common = defaults.copy(game = game, startingBalance = start, currency = currency.trim().ifEmpty { "₹" })
        return if (game == GameType.POKER) {
            common.copy(
                smallBlind = smallBlind.toIntOrNull() ?: return null,
                bigBlind = bigBlind.toIntOrNull() ?: return null,
                betLimit = betLimit,
                ante = ante.ifBlank { "0" }.toIntOrNull() ?: return null,
                anteStyle = anteStyle,
            )
        } else {
            common.copy(
                bootAmount = boot.toIntOrNull() ?: return null,
                maxSeenBet = chaalLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
                potLimit = potLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
                maxBlindTurns = blindLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
            )
        }
    }

    val error: String?
        get() {
            val settings = toSettings()
                ?: return if (game == GameType.POKER) "Fill in the starting chips and the blinds" else "Fill in the starting chips and the boot"
            return settings.validationError()
        }

    companion object {
        fun from(settings: TableSettings) = SettingsInput(
            game = settings.game,
            startingBalance = settings.startingBalance.toString(),
            boot = settings.bootAmount.toString(),
            chaalLimit = settings.maxSeenBet.toString(),
            potLimit = settings.potLimit.toString(),
            blindLimit = settings.maxBlindTurns.toString(),
            smallBlind = settings.smallBlind.toString(),
            bigBlind = settings.bigBlind.toString(),
            betLimit = settings.betLimit,
            ante = settings.ante.toString(),
            anteStyle = settings.anteStyle,
            currency = settings.currency,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun SettingsFields(input: SettingsInput, onChange: (SettingsInput) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (input.game == GameType.POKER) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    "Small blind", input.smallBlind, { onChange(input.copy(smallBlind = it)) },
                    Modifier.weight(1f), supportingText = "First seat after dealer",
                )
                NumberField(
                    "Big blind", input.bigBlind, { onChange(input.copy(bigBlind = it)) },
                    Modifier.weight(1f), supportingText = "Second seat after dealer",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    "Ante", input.ante, { onChange(input.copy(ante = it)) },
                    Modifier.weight(1f), supportingText = "Per player, 0 = none",
                )
                NumberField(
                    "Starting chips", input.startingBalance, { onChange(input.copy(startingBalance = it)) },
                    Modifier.weight(1f), supportingText = "Each player gets",
                )
            }
            if ((input.ante.toIntOrNull() ?: 0) > 0) {
                Text("Who pays the ante", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Choice(
                    listOf(AnteStyle.EVERYONE to "Everyone", AnteStyle.BIG_BLIND to "Big blind"),
                    input.anteStyle,
                ) { onChange(input.copy(anteStyle = it)) }
                Hint(
                    if (input.anteStyle == AnteStyle.EVERYONE) {
                        "Every player puts in the ante each hand."
                    } else {
                        "The big blind pays the ante for the whole table, so there's only one to collect."
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text("Betting limit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            Choice(
                listOf(BetLimit.NO_LIMIT to "No limit", BetLimit.POT_LIMIT to "Pot limit"),
                input.betLimit,
            ) { onChange(input.copy(betLimit = it)) }
            Hint(
                if (input.betLimit == BetLimit.NO_LIMIT) {
                    "Bet any amount up to all your chips. Usual for Texas Hold'em."
                } else {
                    "The most anyone can bet or raise is the size of the pot. Usual for Omaha."
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CurrencyField(input, onChange, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    "Starting chips", input.startingBalance, { onChange(input.copy(startingBalance = it)) },
                    Modifier.weight(1f), supportingText = "Each player gets",
                )
                NumberField(
                    "Boot", input.boot, { onChange(input.copy(boot = it)) },
                    Modifier.weight(1f), supportingText = "Everyone pays per round",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    "Chaal limit", input.chaalLimit, { onChange(input.copy(chaalLimit = it)) },
                    Modifier.weight(1f), supportingText = "Max seen bet, 0 = none",
                )
                NumberField(
                    "Pot limit", input.potLimit, { onChange(input.copy(potLimit = it)) },
                    Modifier.weight(1f), supportingText = "Forces a show, 0 = none",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    "Blind turns", input.blindLimit, { onChange(input.copy(blindLimit = it)) },
                    Modifier.weight(1f), supportingText = "Max blind bets, 0 = none",
                )
                CurrencyField(input, onChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CurrencyField(input: SettingsInput, onChange: (SettingsInput) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = input.currency,
        onValueChange = { onChange(input.copy(currency = it.take(4))) },
        label = { Text("Currency") },
        singleLine = true,
        supportingText = { Text("Shown before amounts") },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostSetupScreen(
    defaultTableName: String,
    replacesTable: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onOpen: (tableName: String, settings: TableSettings) -> Unit,
) {
    var tableName by remember { mutableStateOf(defaultTableName) }
    var input by remember { mutableStateOf(SettingsInput.from(TableSettings())) }
    var showErrors by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Host a table") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = tableName,
                onValueChange = { tableName = it.take(40) },
                label = { Text("Table name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Game")
            Choice(
                listOf(GameType.TEEN_PATTI to "3 Patti", GameType.POKER to "Poker"),
                input.game,
            ) { input = input.copy(game = it) }
            Hint("Chosen once for this table. Open a new table to play the other game.")
            SectionTitle(if (input.game == GameType.POKER) "Blinds and chips" else "Chips and limits")
            SettingsFields(input, onChange = { input = it })
            val error = input.error
            if (showErrors && error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (replacesTable != null) {
                Hint("Opening a new table replaces the unfinished table \"$replacesTable\" saved on this phone.")
            }
            Button(
                onClick = {
                    val settings = input.toSettings()
                    if (settings == null || settings.validationError() != null) {
                        showErrors = true
                    } else {
                        onOpen(tableName, settings)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Open table") }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How betting works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    RulesText(input.toSettings() ?: TableSettings(game = input.game))
                }
            }
        }
    }
}

/** Short explanation of the betting rules the app follows. */
@Composable
fun RulesText(settings: TableSettings) {
    fun m(amount: Int) = com.threepatti.core.formatMoney(amount, settings.currency)
    val lines = if (settings.isPoker) {
        listOf(
            "The two players after the dealer post the blinds, ${m(settings.smallBlind)} and ${m(settings.bigBlind)}. " +
                "The dealer button moves one seat every hand.",
            *listOfNotNull(
                when {
                    settings.ante <= 0 -> null
                    settings.anteStyle == AnteStyle.BIG_BLIND ->
                        "The big blind also pays an ante of ${m(settings.ante)} for every player. Antes go into the pot " +
                            "but don't count as a bet."
                    else -> "Everyone antes ${m(settings.ante)} before the blinds. Antes go into the pot but don't count as a bet."
                },
            ).toTypedArray(),
            "Betting goes round four times: before the flop, after the flop, on the turn and on the river.",
            "Check, call, bet or raise. A raise must be at least as big as the last bet or raise.",
            if (settings.betLimit == BetLimit.POT_LIMIT) {
                "Pot limit: the most you can bet or raise is the size of the pot."
            } else {
                "No limit: you can bet any amount up to all your chips."
            },
            "An all-in player can only win what they matched from each other player. The app works out the side pots.",
            "At showdown the host taps who won each pot, main pot first.",
        )
    } else {
        listOf(
            "Every round starts with a boot of ${m(settings.bootAmount)} from each player. The stake starts at the boot.",
            "Blind players (who haven't looked) bet the stake. Seen players bet twice the stake (chaal).",
            "Raise doubles the stake for everyone after you.",
            "Side show: a seen player can ask the previous seen player to compare cards privately. The weaker hand packs.",
            "Show: when 2 players are left, either can pay one bet to show. The host enters who won.",
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Hint("• $it") }
    }
}
