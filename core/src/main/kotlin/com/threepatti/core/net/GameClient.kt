package com.threepatti.core.net

import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

sealed class ConnectionStatus {
    /** Trying to reach the host for the first time. [lastError] is set after a failed attempt. */
    data class Connecting(val lastError: String? = null) : ConnectionStatus()
    data object Connected : ConnectionStatus()

    /** Was connected, lost the host, trying again. */
    data class Reconnecting(val reason: String) : ConnectionStatus()

    /** Finished for good: the player left, the host closed the table or removed the player. */
    data class Closed(val reason: String) : ConnectionStatus()
}

/**
 * A player's connection to the host. Keeps reconnecting until [close] is called or the host says goodbye,
 * and always rejoins with the same [deviceId] so the player keeps their seat.
 */
class GameClient(
    val host: String,
    val port: Int,
    private val deviceId: String,
    private val name: String,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connecting())
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _playerId = MutableStateFlow<String?>(null)
    val playerId: StateFlow<String?> = _playerId.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    @Volatile
    private var closed = false

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var outbox: Channel<ClientMessage>? = null

    private var job: Job? = null

    fun start() {
        check(job == null) { "Client already started" }
        job = scope.launch(Dispatchers.IO) { runLoop() }
    }

    /** Sends a move to the host. Returns false (and reports an error) when not connected. */
    fun send(action: GameAction): Boolean {
        val channel = outbox
        if (channel == null || _status.value != ConnectionStatus.Connected) {
            _errors.tryEmit("Not connected to the host")
            return false
        }
        return channel.trySend(ClientMessage.Act(action)).isSuccess
    }

    fun close(reason: String = "You left the table") {
        closed = true
        job?.cancel()
        runCatching { socket?.close() }
        _status.value = ConnectionStatus.Closed(reason)
    }

    private suspend fun runLoop() {
        var everConnected = false
        var failures = 0
        while (!closed) {
            val outcome = connectOnce { everConnected = true; failures = 0 }
            if (closed) return
            when (outcome) {
                is Outcome.Ended -> {
                    closed = true
                    _status.value = ConnectionStatus.Closed(outcome.message)
                    return
                }
                is Outcome.Lost -> {
                    failures++
                    _status.value = if (everConnected) {
                        ConnectionStatus.Reconnecting(outcome.reason)
                    } else {
                        ConnectionStatus.Connecting(outcome.reason)
                    }
                }
            }
            delay(if (failures < 3) 1_000 else 3_000)
        }
    }

    private suspend fun connectOnce(onWelcome: () -> Unit): Outcome = coroutineScope {
        val socket = Socket()
        this@GameClient.socket = socket
        val channel = Channel<ClientMessage>(capacity = 32)
        try {
            socket.connect(InetSocketAddress(host, port), Wire.CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
            socket.soTimeout = Wire.READ_TIMEOUT_MS
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

            val writerJob = launch(Dispatchers.IO) {
                try {
                    for (message in channel) {
                        writer.write(Wire.encode(message))
                        writer.newLine()
                        writer.flush()
                    }
                } catch (e: IOException) {
                    runCatching { socket.close() }
                }
            }
            val pingJob = launch {
                while (true) {
                    delay(Wire.PING_INTERVAL_MS)
                    channel.trySend(ClientMessage.Ping)
                }
            }
            channel.send(ClientMessage.Hello(Wire.PROTOCOL_VERSION, deviceId, name))

            try {
                var welcomed = false
                var outcome: Outcome? = null
                while (outcome == null) {
                    val line = reader.readLine()
                    if (line == null) {
                        outcome = Outcome.Lost("The host closed the connection")
                        continue
                    }
                    when (val message = Wire.decodeServer(line)) {
                        is ServerMessage.Welcome -> {
                            _playerId.value = message.playerId
                            welcomed = true
                        }
                        is ServerMessage.State -> {
                            _state.value = message.state
                            // Connected only once this phone has its seat and the current table.
                            if (welcomed && outbox == null) {
                                outbox = channel
                                _status.value = ConnectionStatus.Connected
                                onWelcome()
                            }
                        }
                        is ServerMessage.Error -> _errors.tryEmit(message.message)
                        is ServerMessage.Goodbye -> outcome = Outcome.Ended(message.message)
                        ServerMessage.Ping, null -> Unit
                    }
                }
                outcome
            } finally {
                outbox = null
                pingJob.cancel()
                writerJob.cancel()
            }
        } catch (e: IOException) {
            Outcome.Lost(describe(e))
        } finally {
            channel.close()
            runCatching { socket.close() }
        }
    }

    private fun describe(e: IOException): String = when (e) {
        is java.net.SocketTimeoutException -> "The host is not answering"
        is java.net.ConnectException -> "Can't reach the host at $host"
        is java.net.UnknownHostException -> "Unknown address $host"
        is java.net.NoRouteToHostException -> "No route to $host. Are you on the same WiFi?"
        else -> e.message ?: "Connection lost"
    }

    private sealed class Outcome {
        data class Lost(val reason: String) : Outcome()
        data class Ended(val message: String) : Outcome()
    }
}
