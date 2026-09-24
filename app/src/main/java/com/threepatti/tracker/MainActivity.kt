package com.threepatti.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.threepatti.core.net.DiscoveredTable
import com.threepatti.core.net.Discovery
import com.threepatti.tracker.ui.AppTheme
import com.threepatti.tracker.ui.GameScreen
import com.threepatti.tracker.ui.HomeScreen
import com.threepatti.tracker.ui.HostSetupScreen
import com.threepatti.tracker.ui.JoinScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TrackerApp
        setContent {
            AppTheme {
                AppRoot(app, onMinimize = { moveTaskToBack(true) })
            }
        }
    }
}

private enum class Screen { Home, HostSetup, Join }

@Composable
private fun AppRoot(app: TrackerApp, onMinimize: () -> Unit) {
    val session by app.sessions.active.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var name by remember { mutableStateOf(app.prefs.playerName) }
    var saved by remember { mutableStateOf(app.sessions.savedTable()) }
    var message by remember { mutableStateOf<String?>(null) }
    val askForNotifications = rememberNotificationPermission()

    val active = session
    if (active != null) {
        // Back should not close the table by accident; it just sends the app to the background.
        BackHandler(onBack = onMinimize)
        KeepScreenOn()
        GameScreen(
            session = active,
            onExit = {
                app.sessions.end()
                saved = app.sessions.savedTable()
                screen = Screen.Home
            },
        )
        return
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            name = name,
            onNameChange = {
                name = it
                app.prefs.playerName = it.trim()
            },
            savedTable = saved,
            message = message,
            onHost = {
                message = null
                screen = Screen.HostSetup
            },
            onJoin = {
                message = null
                screen = Screen.Join
            },
            onResume = {
                askForNotifications()
                message = app.sessions.resumeHost()
            },
            onDiscardSaved = {
                app.sessions.discardSaved()
                saved = null
            },
        )
        Screen.HostSetup -> {
            var error by remember { mutableStateOf<String?>(null) }
            BackHandler { screen = Screen.Home }
            HostSetupScreen(
                defaultTableName = "${name.trim()}'s table",
                replacesTable = saved?.tableName,
                errorMessage = error,
                onBack = { screen = Screen.Home },
                onOpen = { tableName, settings ->
                    askForNotifications()
                    error = app.sessions.startHost(tableName, settings, name)
                },
            )
        }
        Screen.Join -> {
            BackHandler { screen = Screen.Home }
            JoinRoute(
                lastAddress = app.prefs.lastHostAddress,
                onJoin = { host, port -> app.sessions.join(host, port, name.trim()) },
                onBack = { screen = Screen.Home },
            )
        }
    }
}

@Composable
private fun JoinRoute(lastAddress: String, onJoin: (String, Int) -> Unit, onBack: () -> Unit) {
    var tables by remember { mutableStateOf(emptyList<DiscoveredTable>()) }
    var scanning by remember { mutableStateOf(true) }
    var restart by remember { mutableIntStateOf(0) }
    LaunchedEffect(restart) {
        while (true) {
            scanning = true
            tables = Discovery.scan()
            scanning = false
            delay(4_000)
        }
    }
    JoinScreen(
        tables = tables,
        scanning = scanning,
        initialAddress = lastAddress,
        onScan = { restart++ },
        onJoin = onJoin,
        onBack = onBack,
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Returns a function that asks for the notification permission (Android 13+) if it isn't granted yet. */
@Composable
private fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
