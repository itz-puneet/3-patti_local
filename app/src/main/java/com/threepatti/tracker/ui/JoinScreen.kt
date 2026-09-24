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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.threepatti.core.net.DiscoveredTable
import com.threepatti.core.net.NetUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    tables: List<DiscoveredTable>,
    scanning: Boolean,
    initialAddress: String,
    onScan: () -> Unit,
    onJoin: (host: String, port: Int) -> Unit,
    onBack: () -> Unit,
) {
    var address by remember { mutableStateOf(initialAddress) }
    var addressError by remember { mutableStateOf(false) }
    val joinTyped: () -> Unit = {
        val parsed = NetUtils.parseAddress(address)
        if (parsed == null) {
            addressError = true
        } else {
            onJoin(parsed.first, parsed.second)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a table") },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Tables on this network", Modifier.weight(1f))
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onScan) { Text("Search again") }
                }
            }
            if (tables.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (scanning) "Looking for tables…" else "No tables found yet", style = MaterialTheme.typography.titleSmall)
                        Hint("Make sure you are on the same WiFi as the host, or connected to the host's hotspot, and that the host has opened a table.")
                    }
                }
            }
            tables.forEach { table ->
                Card(
                    onClick = { onJoin(table.address, table.port) },
                    enabled = table.compatible,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(table.tableName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Host: ${table.hostName} · ${table.players} ${if (table.players == 1) "player" else "players"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Hint(if (table.compatible) table.address else "Different app version. Install the same version as the host.")
                        }
                        Spacer(Modifier.width(8.dp))
                        if (table.compatible) Text("Join", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Or type the host's address")
            Hint("The host can find it in the menu under Table address.")
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it.trim().take(40)
                    addressError = false
                },
                label = { Text("Host address") },
                placeholder = { Text("192.168.1.5") },
                singleLine = true,
                isError = addressError,
                supportingText = if (addressError) {
                    { Text("Type an address like 192.168.1.5") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { joinTyped() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = joinTyped, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Join") }
        }
    }
}
