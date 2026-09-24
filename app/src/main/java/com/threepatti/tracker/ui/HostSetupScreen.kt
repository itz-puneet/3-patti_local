package com.threepatti.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.threepatti.core.TableSettings

/** Settings as typed in text fields. Empty optional fields mean "no limit". */
data class SettingsInput(
    val startingBalance: String,
    val boot: String,
    val chaalLimit: String,
    val potLimit: String,
    val blindLimit: String,
    val currency: String,
) {
    fun toSettings(): TableSettings? {
        val start = startingBalance.toIntOrNull() ?: return null
        val bootAmount = boot.toIntOrNull() ?: return null
        return TableSettings(
            startingBalance = start,
            bootAmount = bootAmount,
            maxSeenBet = chaalLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
            potLimit = potLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
            maxBlindTurns = blindLimit.ifBlank { "0" }.toIntOrNull() ?: return null,
            currency = currency.trim().ifEmpty { "₹" },
        )
    }

    val error: String?
        get() {
            val settings = toSettings() ?: return "Fill in the starting chips and the boot"
            return settings.validationError()
        }

    companion object {
        fun from(settings: TableSettings) = SettingsInput(
            startingBalance = settings.startingBalance.toString(),
            boot = settings.bootAmount.toString(),
            chaalLimit = settings.maxSeenBet.toString(),
            potLimit = settings.potLimit.toString(),
            blindLimit = settings.maxBlindTurns.toString(),
            currency = settings.currency,
        )
    }
}

@Composable
fun SettingsFields(input: SettingsInput, onChange: (SettingsInput) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            OutlinedTextField(
                value = input.currency,
                onValueChange = { onChange(input.copy(currency = it.take(4))) },
                label = { Text("Currency") },
                singleLine = true,
                supportingText = { Text("Shown before amounts") },
                modifier = Modifier.weight(1f),
            )
        }
    }
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
            SectionTitle("Chips and limits")
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
                    RulesText(TableSettings())
                }
            }
        }
    }
}

/** Short explanation of the betting rules the app follows. */
@Composable
fun RulesText(settings: TableSettings) {
    fun m(amount: Int) = com.threepatti.core.formatMoney(amount, settings.currency)
    val lines = listOf(
        "Every round starts with a boot of ${m(settings.bootAmount)} from each player. The stake starts at the boot.",
        "Blind players (who haven't looked) bet the stake. Seen players bet twice the stake (chaal).",
        "Raise doubles the stake for everyone after you.",
        "Side show: a seen player can ask the previous seen player to compare cards privately. The weaker hand packs.",
        "Show: when 2 players are left, either can pay one bet to show. The host enters who won.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Hint("• $it") }
    }
}
