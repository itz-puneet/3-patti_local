package com.threepatti.core

import com.threepatti.core.GameAction.AddPlayer
import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.net.ClientMessage
import com.threepatti.core.net.ServerMessage
import com.threepatti.core.net.Wire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableHostTest {
    private fun host(): TableHost = TableHost(HostSnapshot(GameEngine.newTable("Friday", TableSettings(), "Asha")))

    @Test
    fun sameDeviceGetsSameSeatBack() {
        val host = host()
        val ravi = host.join("device-ravi", "Ravi")
        val meena = host.join("device-meena", "Meena")
        assertNotEquals(ravi, meena)
        assertEquals(ravi, host.join("device-ravi", "Ravi on new name"))
        assertEquals("Ravi", host.state.value.player(ravi)!!.name)
    }

    @Test
    fun undoRestoresChipsButKeepsNewPhonesAndConnections() {
        val saved = mutableListOf<HostSnapshot>()
        val host = TableHost(HostSnapshot(GameEngine.newTable("Friday", TableSettings(), "Asha"))) { saved += it }
        val ravi = host.join("device-ravi", "Ravi")
        host.setConnected(ravi, true)
        assertFalse(host.canUndo.value)

        assertNull(host.perform(StartRound))
        assertNull(host.perform(Bet(ravi)))
        val meena = host.join("device-meena", "Meena")
        assertTrue(host.canUndo.value)

        assertNull(host.undo())
        val afterFirstUndo = host.state.value
        assertEquals(245, afterFirstUndo.player(ravi)!!.balance)
        assertTrue(afterFirstUndo.player(ravi)!!.connected)
        assertEquals(250, afterFirstUndo.player(meena)!!.balance, "phone that joined later keeps its seat")
        assertTrue(afterFirstUndo.log.last().text.startsWith("Host undid: Ravi played blind"))

        assertNull(host.undo())
        assertNull(host.state.value.round)
        assertEquals("Nothing to undo", host.undo())
        assertEquals(host.state.value, saved.last().state)
    }

    @Test
    fun undoRemovesPlayersAddedByHost() {
        val host = host()
        assertNull(host.perform(AddPlayer("Dadi")))
        assertEquals(2, host.state.value.players.size)
        host.undo()
        assertEquals(1, host.state.value.players.size)
    }

    @Test
    fun resumedTableShowsRemotePlayersOffline() {
        val first = host()
        val ravi = first.join("device-ravi", "Ravi")
        first.setConnected(ravi, true)
        val resumed = TableHost(first.snapshot())
        assertFalse(resumed.state.value.player(ravi)!!.connected)
        assertTrue(resumed.state.value.player(resumed.hostPlayerId)!!.connected)
        assertEquals(ravi, resumed.join("device-ravi", "Ravi"))
    }

    @Test
    fun rejectedActionsReturnMessageAndChangeNothing() {
        val host = host()
        val before = host.state.value
        assertEquals("Need at least 2 players with ₹5 or more to start a round", host.perform(StartRound))
        assertEquals(before, host.state.value)
        assertFalse(host.canUndo.value)
    }

    @Test
    fun messagesSurviveJsonRoundTrip() {
        val host = host()
        val ravi = host.join("device-ravi", "Ravi")
        host.perform(StartRound)
        val state = host.state.value
        val stateLine = Wire.encode(ServerMessage.State(state))
        assertFalse(stateLine.contains('\n'))
        assertEquals(ServerMessage.State(state), Wire.decodeServer(stateLine))

        val actions = listOf(
            GameAction.SeeCards(ravi), Bet(ravi, raise = true), GameAction.Pack(ravi), GameAction.Show(ravi),
            GameAction.RequestSideShow(ravi), GameAction.AnswerSideShow(ravi, true), StartRound,
            GameAction.DeclareWinners(listOf(ravi)), GameAction.ForceShow, GameAction.CancelRound, AddPlayer("X"),
            GameAction.RemovePlayer(ravi), GameAction.RenamePlayer(ravi, "R"), GameAction.AdjustChips(ravi, -5),
            GameAction.SetSittingOut(ravi, true), GameAction.MoveSeat(ravi, 1), GameAction.UpdateSettings(TableSettings()),
        )
        for (action in actions) {
            val line = Wire.encode(ClientMessage.Act(action))
            assertEquals(ClientMessage.Act(action), Wire.decodeClient(line), line)
        }
        assertEquals(ClientMessage.Ping, Wire.decodeClient(Wire.encode(ClientMessage.Ping)))
        assertNull(Wire.decodeClient("not json"))
        assertNull(Wire.decodeClient("""{"type":"from_the_future"}"""))
    }
}
