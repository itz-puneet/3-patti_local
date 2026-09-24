package com.threepatti.core

import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.RemovePlayer
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.net.WebGoodbye
import com.threepatti.core.net.WebResult
import com.threepatti.core.net.WebServer
import com.threepatti.core.net.WebUpdate
import com.threepatti.core.net.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val table = TableHost(HostSnapshot(GameEngine.newTable("Friday night", TableSettings(), "Asha")))
    private val server = WebServer(table, scope, requestedPort = 0, heartbeatTimeoutMs = 1_500)

    @AfterTest
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    /** A browser tab listening to /events. */
    private inner class Browser(val device: String, name: String) : AutoCloseable {
        private val socket = Socket("127.0.0.1", server.port).apply { soTimeout = 5_000 }
        private val reader: BufferedReader = socket.getInputStream().bufferedReader()

        init {
            val out = socket.getOutputStream()
            out.write("GET /events?device=$device&name=${name.replace(" ", "%20")} HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            out.flush()
            assertEquals("HTTP/1.1 200 OK", reader.readLine())
            while (reader.readLine().isNotEmpty()) Unit
        }

        /** Next named event as (event, data). Skips comments and the retry line. */
        fun next(): Pair<String, String> {
            var event: String? = null
            while (true) {
                val line = reader.readLine() ?: error("stream ended")
                when {
                    line.startsWith("event: ") -> event = line.removePrefix("event: ")
                    line.startsWith("data: ") && event != null -> return event to line.removePrefix("data: ")
                }
            }
        }

        fun nextUpdate(match: (WebUpdate) -> Boolean = { true }): WebUpdate {
            while (true) {
                val (event, data) = next()
                check(event == "update") { "expected update, got $event: $data" }
                val update = Wire.json.decodeFromString(WebUpdate.serializer(), data)
                if (match(update)) return update
            }
        }

        fun act(action: GameAction): WebResult = post("/action?device=$device", Wire.json.encodeToString(GameAction.serializer(), action))

        override fun close() = socket.close()
    }

    private fun post(path: String, body: String): WebResult {
        val connection = URI("http://127.0.0.1:${server.port}$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
        return Wire.json.decodeFromString(WebResult.serializer(), stream.bufferedReader().readText())
    }

    private fun ping(device: String) {
        val connection = URI("http://127.0.0.1:${server.port}/ping?device=$device").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { }
        assertEquals(204, connection.responseCode)
    }

    @Test
    fun servesThePage() {
        server.start()
        val connection = URI("http://127.0.0.1:${server.port}/").toURL().openConnection() as HttpURLConnection
        assertEquals(200, connection.responseCode)
        assertTrue(connection.contentType.startsWith("text/html"))
        assertTrue(connection.inputStream.bufferedReader().readText().contains("<title>3 Patti Tracker</title>"))
        val missing = URI("http://127.0.0.1:${server.port}/nope").toURL().openConnection() as HttpURLConnection
        assertEquals(404, missing.responseCode)
    }

    @Test
    fun browserJoinsAndPlaysItsOwnSeat() = runBlocking<Unit> {
        server.start()
        Browser("web-priya", "Priya Shah").use { priya ->
            val first = priya.nextUpdate()
            val me = first.playerId
            assertEquals("Priya Shah", first.state.player(me)!!.name)
            withTimeout(5_000) { table.state.first { it.player(me)?.connected == true } }

            assertEquals(WebResult(false, "No round is running"), priya.act(Bet(me)))
            assertNull(table.perform(StartRound))
            val started = priya.nextUpdate { it.state.round != null }
            assertEquals(me, started.state.round!!.turnId)
            assertTrue(started.options.isTurn)
            assertEquals("Priya Shah's turn", started.roundText)

            assertEquals(WebResult(false, "You can only play for yourself"), priya.act(Bet("p1")))
            assertEquals(WebResult(true), priya.act(Bet(me, raise = true)))
            val after = priya.nextUpdate { it.state.round?.pot == 20 }
            assertEquals(235, after.state.player(me)!!.balance)
            assertFalse(after.options.isTurn)
        }
    }

    @Test
    fun sameBrowserGetsItsSeatBack() {
        server.start()
        val me = Browser("web-ravi", "Ravi").use { it.nextUpdate().playerId }
        Browser("web-ravi", "Someone else").use { again ->
            val update = again.nextUpdate()
            assertEquals(me, update.playerId)
            assertEquals("Ravi", update.state.player(me)!!.name)
        }
        assertEquals(2, table.state.value.players.size)
    }

    @Test
    fun strangersCannotAct() {
        server.start()
        assertEquals(WebResult(false, "Join the table first"), post("/action?device=nobody", """{"type":"see","playerId":"p1"}"""))
    }

    @Test
    fun removedPlayerGetsGoodbye() {
        server.start()
        Browser("web-guest", "Guest").use { guest ->
            val me = guest.nextUpdate().playerId
            assertNull(table.perform(RemovePlayer(me)))
            val (event, data) = guest.next()
            assertEquals("goodbye", event)
            assertEquals(WebGoodbye("The host removed you from the table"), Wire.json.decodeFromString(WebGoodbye.serializer(), data))
        }
    }

    @Test
    fun silentBrowserIsMarkedOffline() = runBlocking<Unit> {
        server.start()
        Browser("web-sleepy", "Sleepy").use { sleepy ->
            val me = sleepy.nextUpdate().playerId
            withTimeout(5_000) { table.state.first { it.player(me)?.connected == true } }
            // Heartbeats keep it online...
            repeat(6) {
                ping("web-sleepy")
                Thread.sleep(400)
            }
            assertTrue(table.state.value.player(me)!!.connected)
            // ...and without them the host takes over after the timeout.
            withTimeout(5_000) { table.state.first { it.player(me)?.connected == false } }
        }
    }

    @Test
    fun qrCodeHasFinderPatterns() {
        val qr = QrCode.encode("http://192.168.43.1:8080")
        assertTrue(qr.size >= 21)
        // Top-left finder pattern: dark 7x7 border with a light ring inside.
        assertTrue((0 until 7).all { qr[it, 0] && qr[0, it] && qr[it, 6] && qr[6, it] })
        assertFalse(qr[1, 1])
        assertTrue(qr[3, 3])
    }
}
