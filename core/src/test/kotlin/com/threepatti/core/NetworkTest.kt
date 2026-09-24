package com.threepatti.core

import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.RemovePlayer
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.net.ClientMessage
import com.threepatti.core.net.ConnectionStatus
import com.threepatti.core.net.Discovery
import com.threepatti.core.net.GameClient
import com.threepatti.core.net.HostServer
import com.threepatti.core.net.ServerMessage
import com.threepatti.core.net.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val table = TableHost(HostSnapshot(GameEngine.newTable("Friday night", TableSettings(), "Asha")))
    private val discoveryPort = DatagramSocket(0).use { it.localPort }
    private val server = HostServer(table, scope, requestedPort = 0, discoveryPort = discoveryPort)

    @AfterTest
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun client(device: String, name: String) =
        GameClient("127.0.0.1", server.port, device, name, scope).apply { start() }

    private suspend fun GameClient.awaitConnected() = status.first { it == ConnectionStatus.Connected }

    @Test
    fun playersJoinPlayAndReconnectOverTheNetwork() = runBlocking<Unit> {
        server.start()
        val clients = mapOf("device-ravi" to client("device-ravi", "Ravi"), "device-meena" to client("device-meena", "Meena"))
        withTimeout(5_000) {
            clients.values.forEach { it.awaitConnected() }
            table.state.first { s -> s.players.size == 3 && s.players.all { it.connected } }
        }

        assertNull(table.perform(StartRound))
        // Both phones joined at the same time, so either can have the first turn (the seat after the host).
        val firstId = table.state.value.round!!.turnId!!
        val (firstDevice, first) = clients.entries.single { it.value.playerId.value == firstId }.toPair()
        val second = clients.values.single { it !== first }
        val secondId = second.playerId.value!!
        withTimeout(5_000) { first.state.first { it?.round?.turnId == firstId } }

        assertTrue(first.send(Bet(firstId)))
        withTimeout(5_000) { second.state.first { it != null && it.round?.pot == 20 && it.round?.turnId == secondId } }

        val error = async(start = CoroutineStart.UNDISPATCHED) { second.errors.first() }
        second.send(Bet(firstId))
        assertEquals("You can only play for yourself", withTimeout(5_000) { error.await() })

        first.close()
        withTimeout(5_000) { table.state.first { it.player(firstId)?.connected == false } }
        val again = client(firstDevice, "New name")
        withTimeout(5_000) { again.awaitConnected() }
        assertEquals(firstId, again.playerId.value)
        assertEquals(240, again.state.value!!.player(firstId)!!.balance)

        server.stop()
        val closed = withTimeout(5_000) { again.status.first { it is ConnectionStatus.Closed } }
        assertEquals(ConnectionStatus.Closed("The host closed the table"), closed)
    }

    @Test
    fun removedPlayerIsDisconnected() = runBlocking<Unit> {
        server.start()
        val guest = client("device-guest", "Guest")
        withTimeout(5_000) { guest.awaitConnected() }
        assertNull(table.perform(RemovePlayer(guest.playerId.value!!)))
        val closed = withTimeout(5_000) { guest.status.first { it is ConnectionStatus.Closed } }
        assertEquals(ConnectionStatus.Closed("The host removed you from the table"), closed)
    }

    @Test
    fun tablesAreFoundByDiscovery() = runBlocking<Unit> {
        server.start()
        val tables = Discovery.scan(listOf(InetAddress.getLoopbackAddress()), discoveryPort, timeoutMs = 1_000)
        val found = tables.single()
        assertEquals("Friday night", found.tableName)
        assertEquals("Asha", found.hostName)
        assertEquals(server.port, found.port)
        assertTrue(found.compatible)
    }

    @Test
    fun olderAppVersionIsTurnedAway() {
        server.start()
        Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 5_000
            val writer = socket.getOutputStream().bufferedWriter()
            writer.write(Wire.encode(ClientMessage.Hello(protocol = 999, deviceId = "old", name = "Old")))
            writer.newLine()
            writer.flush()
            val reply = Wire.decodeServer(socket.getInputStream().bufferedReader().readLine())
            assertIs<ServerMessage.Goodbye>(reply)
        }
        assertEquals(1, table.state.value.players.size)
    }

    @Test
    fun unreachableHostKeepsRetrying() = runBlocking<Unit> {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val lonely = GameClient("127.0.0.1", port, "device-x", "X", scope).apply { start() }
        val status = withTimeout(5_000) {
            lonely.status.first { it is ConnectionStatus.Connecting && it.lastError != null }
        }
        assertEquals(ConnectionStatus.Connecting("Can't reach the host at 127.0.0.1"), status)
        lonely.close()
        assertIs<ConnectionStatus.Closed>(lonely.status.value)
    }
}
