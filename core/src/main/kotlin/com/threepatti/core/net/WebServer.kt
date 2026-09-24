package com.threepatti.core.net

import com.threepatti.core.Actor
import com.threepatti.core.GameAction
import com.threepatti.core.GameState
import com.threepatti.core.PokerOptions
import com.threepatti.core.PokerRules
import com.threepatti.core.Rules
import com.threepatti.core.SeatOptions
import com.threepatti.core.Settlement
import com.threepatti.core.TableHost
import com.threepatti.core.Transfer
import com.threepatti.core.describeRound
import com.threepatti.core.summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/** Everything the browser page needs to draw the table for one player. */
@Serializable
data class WebUpdate(
    val playerId: String,
    val state: GameState,
    val options: SeatOptions,
    /** Set at poker tables instead of [options]. */
    val poker: PokerOptions? = null,
    val transfers: List<Transfer>,
    val roundText: String?,
    val rulesSummary: String,
)

@Serializable
data class WebResult(val ok: Boolean, val error: String? = null)

@Serializable
data class WebGoodbye(val message: String)

/**
 * Lets phones without the app (such as iPhones) play from their browser. Serves a single page, pushes the
 * table to it with Server-Sent Events, and takes moves as small POST requests.
 */
class WebServer(
    private val table: TableHost,
    private val scope: CoroutineScope,
    private val requestedPort: Int = DEFAULT_PORT,
    private val page: ByteArray = loadPage(),
    /** A browser that sends no heartbeat for this long is treated as offline. */
    private val heartbeatTimeoutMs: Long = 30_000,
) {
    /** The HTTP port. Valid after [start]. */
    @Volatile
    var port: Int = -1
        private set

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val streams = ConcurrentHashMap<String, EventStream>()

    /** Opens the server. Throws [IOException] if no port could be opened. */
    fun start() {
        check(job == null) { "Server already started" }
        val socket = openServerSocket()
        serverSocket = socket
        port = socket.localPort
        job = scope.launch(Dispatchers.IO) {
            launch { watchHeartbeats() }
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                launch { handle(client) }
            }
        }
    }

    fun stop(message: String = "The host closed the table") {
        streams.values.forEach { stream ->
            runCatching { stream.send("goodbye", Wire.json.encodeToString(WebGoodbye.serializer(), WebGoodbye(message))) }
            stream.close()
        }
        runCatching { serverSocket?.close() }
        job?.cancel()
    }

    private fun openServerSocket(): ServerSocket {
        val ports = if (requestedPort == 0) listOf(0) else (requestedPort until requestedPort + 10).toList() + 0
        var lastError: IOException? = null
        for (candidate in ports) {
            try {
                return ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(candidate))
                }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No free port")
    }

    private suspend fun handle(socket: Socket) {
        try {
            socket.soTimeout = 15_000
            val request = readRequest(BufferedInputStream(socket.getInputStream())) ?: return
            val out = socket.getOutputStream()
            when {
                request.method == "GET" && (request.path == "/" || request.path == "/index.html") ->
                    respond(out, 200, "text/html; charset=utf-8", page)
                request.method == "GET" && request.path == "/events" -> serveEvents(socket, out, request)
                request.method == "POST" && request.path == "/action" -> handleAction(out, request)
                request.method == "POST" && request.path == "/ping" -> handlePing(out, request)
                else -> respond(out, 404, "text/plain; charset=utf-8", "Not found".toByteArray())
            }
        } catch (e: IOException) {
            // Browser went away.
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun serveEvents(socket: Socket, out: OutputStream, request: Request) {
        val deviceId = request.query["device"]?.takeIf { it.isNotBlank() && it.length <= 64 }
        if (deviceId == null) {
            respond(out, 400, "text/plain; charset=utf-8", "Missing device".toByteArray())
            return
        }
        socket.soTimeout = 0
        out.write(
            (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: keep-alive\r\n\r\n" +
                    "retry: 2000\n\n"
                ).toByteArray(),
        )
        out.flush()

        val playerId = table.join(deviceId, request.query["name"].orEmpty(), onBrowser = true)
        val stream = EventStream(socket, out, currentCoroutineContext().job)
        streams.put(playerId, stream)?.close()
        table.setConnected(playerId, true)
        try {
            coroutineScope {
                val pinger = launch {
                    while (true) {
                        delay(PING_INTERVAL_MS)
                        stream.comment()
                    }
                }
                table.state.takeWhile { it.player(playerId) != null }.collect { state ->
                    stream.send("update", Wire.json.encodeToString(WebUpdate.serializer(), update(state, playerId)))
                }
                pinger.cancel()
                stream.send(
                    "goodbye",
                    Wire.json.encodeToString(WebGoodbye.serializer(), WebGoodbye("The host removed you from the table")),
                )
            }
        } finally {
            stream.close()
            if (streams.remove(playerId, stream)) table.setConnected(playerId, false)
        }
    }

    private fun handleAction(out: OutputStream, request: Request) {
        val playerId = request.query["device"]?.let { table.playerIdFor(it) }
        if (playerId == null) {
            respondJson(out, 403, WebResult(false, "Join the table first"))
            return
        }
        streams[playerId]?.touch()
        val action = runCatching {
            Wire.json.decodeFromString(GameAction.serializer(), request.body.toString(Charsets.UTF_8))
        }.getOrNull()
        if (action == null) {
            respondJson(out, 400, WebResult(false, "Unknown action"))
            return
        }
        val error = table.perform(action, Actor.Remote(playerId))
        respondJson(out, 200, WebResult(error == null, error))
    }

    private fun handlePing(out: OutputStream, request: Request) {
        val playerId = request.query["device"]?.let { table.playerIdFor(it) }
        playerId?.let { streams[it]?.touch() }
        respond(out, 204, null, ByteArray(0))
    }

    /** Browsers in the background stop sending heartbeats. Treat them as offline so the host can play for them. */
    private suspend fun watchHeartbeats() {
        while (true) {
            delay(heartbeatTimeoutMs / 6)
            val now = System.currentTimeMillis()
            streams.values.filter { now - it.lastSeen > heartbeatTimeoutMs }.forEach { it.close() }
        }
    }

    private fun update(state: GameState, playerId: String) = WebUpdate(
        playerId = playerId,
        state = state,
        options = Rules.seatOptions(state, playerId),
        poker = if (state.settings.isPoker) PokerRules.options(state, playerId) else null,
        transfers = Settlement.transfers(state.players),
        roundText = state.round?.let { describeRound(state, it) },
        rulesSummary = state.settings.summary(),
    )

    private fun respondJson(out: OutputStream, code: Int, result: WebResult) = respond(
        out,
        code,
        "application/json; charset=utf-8",
        Wire.json.encodeToString(WebResult.serializer(), result).toByteArray(),
    )

    private fun respond(out: OutputStream, code: Int, contentType: String?, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val head = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            if (contentType != null) append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: ByteArray,
    )

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        var headerCount = 0
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            if (++headerCount > 100) return null
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 0..MAX_BODY) return null
        // Read by hand: InputStream.readNBytes only exists on Android 13 and newer.
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        val target = parts[1]
        val questionMark = target.indexOf('?')
        val path = if (questionMark >= 0) target.substring(0, questionMark) else target
        val query = if (questionMark >= 0) parseQuery(target.substring(questionMark + 1)) else emptyMap()
        return Request(parts[0].uppercase(), path, query, body)
    }

    private fun parseQuery(text: String): Map<String, String> = text.split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            decode(key) to decode(value)
        }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** Reads one CRLF-terminated header line, or null at end of stream or when the line is too long. */
    private fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            if (b != '\r'.code) bytes.write(b)
            if (bytes.size() > MAX_LINE) return null
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private class EventStream(private val socket: Socket, private val out: OutputStream, private val job: Job) {
        @Volatile
        var lastSeen: Long = System.currentTimeMillis()
            private set

        fun touch() {
            lastSeen = System.currentTimeMillis()
        }

        fun send(event: String, data: String) = write("event: $event\ndata: $data\n\n")

        fun comment() = write(": ping\n\n")

        private fun write(text: String) {
            synchronized(this) {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }

        fun close() {
            job.cancel()
            runCatching { socket.close() }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val MAX_BODY = 64 * 1024
        private const val MAX_LINE = 8 * 1024
        private const val PING_INTERVAL_MS = 10_000L

        fun loadPage(): ByteArray =
            WebServer::class.java.getResourceAsStream("/web/index.html")?.use { it.readBytes() }
                ?: error("Browser page missing from the app")
    }
}
