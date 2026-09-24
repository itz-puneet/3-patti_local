package com.threepatti.core.net

import com.threepatti.core.Actor
import com.threepatti.core.TableHost
import com.threepatti.core.gameName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves a [TableHost] to the other phones on the local network.
 * Every change to the table is pushed to every connected phone.
 */
class HostServer(
    private val table: TableHost,
    private val scope: CoroutineScope,
    private val requestedPort: Int = Wire.DEFAULT_PORT,
    private val discoveryPort: Int = Wire.DISCOVERY_PORT,
) {
    /** The TCP port players connect to. Valid after [start]. */
    @Volatile
    var port: Int = -1
        private set

    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var job: Job? = null
    private val connections = ConcurrentHashMap<String, Connection>()

    val connectedCount: Int get() = connections.size

    /** Opens the server. Throws [IOException] if no port could be opened. */
    fun start() {
        check(job == null) { "Server already started" }
        val socket = openServerSocket()
        serverSocket = socket
        port = socket.localPort
        job = scope.launch(Dispatchers.IO) {
            launch { acceptLoop(socket) }
            launch { broadcastLoop() }
            launch { pingLoop() }
            launch { discoveryLoop() }
        }
    }

    fun stop(message: String = "The host closed the table") {
        connections.values.forEach { it.send(ServerMessage.Goodbye(message)) }
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        job?.cancel()
        // Give the goodbye messages a moment to go out, then drop anyone still connected.
        scope.launch(Dispatchers.IO) {
            delay(1_000)
            connections.values.forEach { it.close() }
            connections.clear()
        }
    }

    private fun openServerSocket(): ServerSocket = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(requestedPort))
        }
    } catch (e: IOException) {
        // The usual port is busy; any free port works because discovery tells players which one.
        ServerSocket(0)
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            scope.launch(Dispatchers.IO) { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        val connection = try {
            socket.tcpNoDelay = true
            socket.soTimeout = Wire.READ_TIMEOUT_MS
            Connection(
                socket,
                socket.getInputStream().bufferedReader(Charsets.UTF_8),
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8),
            )
        } catch (e: IOException) {
            runCatching { socket.close() }
            return
        }
        try {
            val hello = Wire.decodeClient(connection.reader.readLine() ?: return) as? ClientMessage.Hello ?: return
            if (hello.protocol != Wire.PROTOCOL_VERSION) {
                connection.writeNow(
                    ServerMessage.Goodbye("This app version doesn't match the host's. Install the same version on both phones."),
                )
                return
            }
            val playerId = table.join(hello.deviceId, hello.name)
            connection.playerId = playerId
            connection.send(ServerMessage.Welcome(playerId))
            connection.send(ServerMessage.State(table.state.value))
            connection.startWriter()
            connections.put(playerId, connection)?.close()
            table.setConnected(playerId, true)

            while (true) {
                val line = connection.reader.readLine() ?: break
                when (val message = Wire.decodeClient(line)) {
                    is ClientMessage.Act -> table.perform(message.action, Actor.Remote(playerId))
                        ?.let { connection.send(ServerMessage.Error(it)) }
                    ClientMessage.Ping, is ClientMessage.Hello, null -> Unit
                }
            }
        } catch (e: IOException) {
            // Connection dropped or timed out. The player can reconnect and get the same seat back.
        } finally {
            connection.close()
            val playerId = connection.playerId
            if (playerId != null && connections.remove(playerId, connection)) {
                table.setConnected(playerId, false)
            }
        }
    }

    private suspend fun broadcastLoop() {
        table.state.collect { state ->
            for (connection in connections.values) {
                if (state.player(connection.playerId) == null) {
                    connection.send(ServerMessage.Goodbye("The host removed you from the table"))
                } else {
                    connection.send(ServerMessage.State(state))
                }
            }
        }
    }

    private suspend fun pingLoop() {
        while (scope.isActive) {
            delay(Wire.PING_INTERVAL_MS)
            connections.values.forEach { it.send(ServerMessage.Ping) }
        }
    }

    private fun discoveryLoop() {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(discoveryPort))
            }
        } catch (e: IOException) {
            // Players can still join by typing the address.
            return
        }
        discoverySocket = socket
        val buffer = ByteArray(512)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: IOException) {
                break
            }
            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
            if (text != Wire.DISCOVERY_REQUEST) continue
            val state = table.state.value
            val reply = Wire.json.encodeToString(
                TableAnnouncement.serializer(),
                TableAnnouncement(
                    Wire.PROTOCOL_VERSION,
                    state.tableName,
                    state.hostName,
                    port,
                    state.players.size,
                    state.settings.gameName(),
                ),
            ).toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(reply, reply.size, packet.socketAddress)) }
        }
    }

    private inner class Connection(
        val socket: Socket,
        val reader: BufferedReader,
        private val writer: BufferedWriter,
    ) {
        @Volatile
        var playerId: String? = null

        // Older state updates can be dropped safely because every update carries the full table.
        private val outbox = Channel<ServerMessage>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        fun send(message: ServerMessage) {
            outbox.trySend(message)
        }

        fun writeNow(message: ServerMessage) {
            runCatching {
                writer.write(Wire.encode(message))
                writer.newLine()
                writer.flush()
            }
        }

        fun startWriter() {
            scope.launch(Dispatchers.IO) {
                try {
                    for (message in outbox) {
                        writer.write(Wire.encode(message))
                        writer.newLine()
                        writer.flush()
                        if (message is ServerMessage.Goodbye) break
                    }
                } catch (e: IOException) {
                    // Reader side notices the broken socket and cleans up.
                } finally {
                    close()
                }
            }
        }

        fun close() {
            outbox.close()
            runCatching { socket.close() }
        }
    }
}
