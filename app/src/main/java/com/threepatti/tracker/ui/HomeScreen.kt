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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class SavedTableInfo(val tableName: String, val players: Int, val rounds: Int)

@Composable
fun HomeScreen(
    name: String,
    onNameChange: (String) -> Unit,
    savedTable: SavedTableInfo?,
    message: String?,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onResume: () -> Unit,
    onDiscardSaved: () -> Unit,
) {
    var nameMissing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val requireName: (() -> Unit) -> Unit = { action ->
        if (name.isBlank()) {
            nameMissing = true
        } else {
            action()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            CardFan()
            Spacer(Modifier.height(16.dp))
            Text(
                "3 Patti Chips Handler",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Hint(
                "Keeps track of chips, bets and the pot while you play 3 Patti or poker with real cards",
                center = true,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    onNameChange(it.take(20))
                    nameMissing = false
                },
                label = { Text("Your name") },
                singleLine = true,
                isError = nameMissing,
                supportingText = if (nameMissing) {
                    { Text("Enter your name first") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { requireName(onHost) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Host a table")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { requireName(onJoin) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Join a table")
            }
            if (message != null) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (savedTable != null) {
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Unfinished table", style = MaterialTheme.typography.labelLarge)
                        Text(savedTable.tableName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${savedTable.players} players · ${savedTable.rounds} rounds played",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { confirmDiscard = true }) { Text("Delete") }
                            Button(
                                onClick = { requireName(onResume) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                ),
                            ) { Text("Resume as host") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    NumberedSteps(
                        listOf(
                            "Everyone joins the same WiFi, or the host turns on their phone's hotspot and the others connect to it.",
                            "One person hosts a table. Everyone else taps Join a table and picks it.",
                            "Deal real cards. The app handles the betting for 3 Patti (boot, chaal, show, side show) " +
                                "or poker (blinds, raises, all-ins, side pots) and keeps the ledger.",
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Signature()
        }
    }

    if (confirmDiscard && savedTable != null) {
        ConfirmDialog(
            title = "Delete ${savedTable.tableName}?",
            text = "The saved chips and history of this table will be lost.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onDiscardSaved()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
