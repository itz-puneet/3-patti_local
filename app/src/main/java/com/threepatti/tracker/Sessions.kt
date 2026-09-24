package com.threepatti.tracker

import android.content.Context
import android.net.wifi.WifiManager
import com.threepatti.core.GameAction
import com.threepatti.core.GameEngine
import com.threepatti.core.GameRuleException
import com.threepatti.core.GameState
import com.threepatti.core.HostSnapshot
import com.threepatti.core.TableHost
import com.threepatti.core.TableSettings
import com.threepatti.core.net.ConnectionStatus
import com.threepatti.core.net.GameClient
import com.threepatti.core.net.HostServer
import com.threepatti.core.net.NetUtils
import com.threepatti.core.net.WebServer
import com.threepatti.core.net.Wire
import com.threepatti.tracker.ui.SavedTableInfo
import com.threepatti.tracker.ui.TableSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

interface ActiveSession : TableSession {
    fun close()
}

/** Starts, resumes and ends the table this phone is hosting or playing at. */
class SessionManager(
    private val context: Context,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val store = TableStore(File(context.filesDir, "table.json"), scope)
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    fun savedTable(): SavedTableInfo? = store.load()?.state?.let {
        SavedTableInfo(it.tableName, it.players.size, it.results.size)
    }

    /** Returns an error message, or null when the table is open. */
    fun startHost(tableName: String, settings: TableSettings, hostName: String): String? {
        val state = try {
            GameEngine.newTable(tableName, settings, hostName)
        } catch (e: GameRuleException) {
            return e.message
        }
        return launchHost(HostSnapshot(state))
    }

    fun resumeHost(): String? {
        val snapshot = store.load() ?: return "The saved table could not be read"
        return launchHost(snapshot)
    }

    fun join(host: String, port: Int, name: String) {
        end()
        prefs.lastHostAddress = if (port == Wire.DEFAULT_PORT) host else "$host:$port"
        val session = ClientTableSession(host, port, prefs.deviceId, name, scope)
        session.start()
        _active.value = session
    }

    fun end() {
        _active.value?.close()
        _active.value = null
    }

    fun discardSaved() = store.delete()

    private fun launchHost(snapshot: HostSnapshot): String? {
        end()
        val session = HostTableSession(context.applicationContext, snapshot, store, scope)
        return try {
            session.start()
            _active.value = session
            null
        } catch (e: IOException) {
            session.close()
            "Couldn't open the table: ${e.message}"
        }
    }
}

class HostTableSession(
    private val context: Context,
    snapshot: HostSnapshot,
    private val store: TableStore,
    scope: CoroutineScope,
) : ActiveSession {
    private val table = TableHost(snapshot, onSnapshot = store::save)
    private val server = HostServer(table, scope)
    private val web = WebServer(table, scope)
    private var multicastLock: WifiManager.MulticastLock? = null
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override val isHost = true
    override val state: StateFlow<GameState?> = table.state
    override val myPlayerId: StateFlow<String?> = MutableStateFlow(table.hostPlayerId)
    override val connection: StateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.Connected)
    override val canUndo: StateFlow<Boolean> = table.canUndo
    override val nextUndo: StateFlow<String?> = table.nextUndo
    override val messages: SharedFlow<String> = _messages.asSharedFlow()
    override val hostAddress: String? = null

    fun start() {
        server.start()
        try {
            web.start()
        } catch (e: IOException) {
            // Phones with the app can still join; only the browser link is missing.
        }
        store.save(table.snapshot())
        // Some phones drop broadcast packets unless an app holds this lock, which would hide the table from Join.
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("3patti-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        HostService.start(context, table.state.value.tableName)
    }

    override fun addresses(): List<String> = NetUtils.localAddresses()
        .map { if (server.port == Wire.DEFAULT_PORT) it.ip else "${it.ip}:${server.port}" }
        .distinct()

    override fun webLinks(): List<String> {
        if (web.port <= 0) return emptyList()
        return NetUtils.localAddresses().map { "http://${it.ip}:${web.port}" }.distinct()
    }

    override fun submit(action: GameAction) {
        table.perform(action)?.let { _messages.tryEmit(it) }
    }

    override fun undo() {
        table.undo()?.let { _messages.tryEmit(it) }
    }

    override fun close() {
        server.stop()
        web.stop()
        runCatching { multicastLock?.release() }
        multicastLock = null
        HostService.stop(context)
    }
}

class ClientTableSession(
    host: String,
    port: Int,
    deviceId: String,
    name: String,
    scope: CoroutineScope,
) : ActiveSession {
    private val client = GameClient(host, port, deviceId, name, scope)

    override val isHost = false
    override val state: StateFlow<GameState?> = client.state
    override val myPlayerId: StateFlow<String?> = client.playerId
    override val connection: StateFlow<ConnectionStatus> = client.status
    override val canUndo: StateFlow<Boolean> = MutableStateFlow(false)
    override val nextUndo: StateFlow<String?> = MutableStateFlow(null)
    override val messages: SharedFlow<String> = client.errors
    override val hostAddress: String = if (port == Wire.DEFAULT_PORT) host else "$host:$port"

    fun start() = client.start()

    override fun addresses(): List<String> = emptyList()

    override fun webLinks(): List<String> = emptyList()

    override fun submit(action: GameAction) {
        client.send(action)
    }

    override fun undo() = Unit

    override fun close() = client.close()
}

/** Saves the hosted table after every change so it survives the app being closed. */
class TableStore(private val file: File, scope: CoroutineScope) {
    private val lock = Any()
    private val pending = MutableStateFlow<HostSnapshot?>(null)

    init {
        scope.launch(Dispatchers.IO) {
            pending.filterNotNull().collect { write(it) }
        }
    }

    fun save(snapshot: HostSnapshot) {
        pending.value = snapshot
    }

    fun load(): HostSnapshot? = synchronized(lock) {
        pending.value ?: runCatching {
            if (file.exists()) Wire.json.decodeFromString(HostSnapshot.serializer(), file.readText()) else null
        }.getOrNull()
    }

    fun delete() {
        synchronized(lock) {
            pending.value = null
            file.delete()
        }
    }

    private fun write(snapshot: HostSnapshot) {
        synchronized(lock) {
            // Skip if a newer snapshot is waiting or the table was deleted meanwhile.
            if (pending.value !== snapshot) return
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(Wire.json.encodeToString(HostSnapshot.serializer(), snapshot))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
