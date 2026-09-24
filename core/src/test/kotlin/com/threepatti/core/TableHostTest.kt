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
        assertFalse(host.canUndo.value, "starting a round can't be undone; Cancel round is for misdeals")
        assertNull(host.perform(Bet(ravi)))
        val meena = host.join("device-meena", "Meena")
        assertTrue(host.canUndo.value)
        assertEquals("Ravi played blind ₹5", host.nextUndo.value)

        assertNull(host.undo())
        val afterUndo = host.state.value
        assertEquals(245, afterUndo.player(ravi)!!.balance)
        assertTrue(afterUndo.player(ravi)!!.connected)
        assertEquals(250, afterUndo.player(meena)!!.balance, "phone that joined later keeps its seat")
        assertEquals(LogEntry(afterUndo.log.last().seq, "Host undid: Ravi played blind ₹5", LogKind.UNDO), afterUndo.log.last())

        assertEquals("Nothing to undo. Once a round starts, earlier rounds are final", host.undo())
        assertEquals(host.state.value, saved.last().state)
    }

    @Test
    fun undoNeverErasesHistory() {
        val host = host()
        val ravi = host.join("device-ravi", "Ravi")
        assertNull(host.perform(StartRound))
        assertNull(host.perform(Bet(ravi)))
        assertNull(host.perform(GameAction.ForceShow))
        assertNull(host.perform(GameAction.DeclareWinners(listOf(ravi))))
        val logBefore = host.state.value.log

        // Oops, wrong winner: undo the payout and the show.
        assertNull(host.undo())
        assertNull(host.undo())
        val s = host.state.value
        assertEquals(RoundPhase.BETTING, s.round!!.phase)
        assertEquals(2, s.undoCount)
        assertTrue(s.log.map { it.text }.containsAll(logBefore.map { it.text }), "every line is still there")
        val undone = s.log.filter { it.undone }.map { it.text }
        assertEquals(listOf("Host called a show for everyone still playing", "Ravi won the pot of ₹15"), undone)
        assertEquals(2, s.log.count { it.kind == LogKind.UNDO })
        assertFalse(s.log.any { it.kind == LogKind.UNDO && it.undone })
        assertFalse(s.log.any { it.kind == LogKind.SEAT && it.undone }, "joining is never marked undone")

        // The undone result stays listed but doesn't count, so the round keeps its number.
        assertEquals(1, s.results.size)
        assertTrue(s.results.single().undone)
        assertEquals(1, s.nextRoundNumber)
        assertNull(host.perform(GameAction.ForceShow))
        assertNull(host.perform(GameAction.DeclareWinners(listOf("p1"))))
        val results = host.state.value.results
        assertEquals(listOf(true, false), results.map { it.undone })
        assertEquals(listOf(1, 1), results.map { it.number })
        assertEquals(2, host.state.value.nextRoundNumber)
    }

    @Test
    fun startingARoundLocksEarlierRounds() {
        val host = host()
        val ravi = host.join("device-ravi", "Ravi")
        assertNull(host.perform(StartRound))
        assertNull(host.perform(GameAction.Pack(ravi)))
        assertTrue(host.canUndo.value, "the result can be fixed until the next round starts")
        assertNull(host.perform(StartRound))
        assertFalse(host.canUndo.value)
        assertEquals("Nothing to undo. Once a round starts, earlier rounds are final", host.undo())
        assertEquals(1, host.state.value.results.count { !it.undone })
        assertNull(host.perform(GameAction.CancelRound))
        assertEquals("Round 2 cancelled, all bets returned", host.nextUndo.value)
    }

    @Test
    fun joiningWithSameNameTakesOverSeatAddedWithoutPhone() {
        val host = host()
        assertNull(host.perform(AddPlayer("Dadi")))
        val dadi = host.state.value.players.last().id
        assertNull(host.perform(GameAction.AdjustChips(dadi, 50)))

        assertEquals(dadi, host.join("web-dadi", "  dadi ", onBrowser = true))
        val seat = host.state.value.player(dadi)!!
        assertTrue(seat.hasDevice)
        assertTrue(seat.onBrowser)
        assertEquals(300, seat.balance)
        assertEquals("Dadi", seat.name)
        assertEquals(2, host.state.value.players.size)
        assertEquals("Dadi now plays from their own browser", host.state.value.log.last().text)
        assertEquals(dadi, host.playerIdFor("web-dadi"))

        // Undo is about the game, so the seat stays with the browser.
        assertNull(host.undo())
        assertEquals(250, host.state.value.player(dadi)!!.balance)
        assertTrue(host.state.value.player(dadi)!!.hasDevice)
    }

    @Test
    fun seatsOfPlayersWithPhonesAreNeverTakenOver() {
        val host = host()
        val ravi = host.join("device-ravi", "Ravi")
        val other = host.join("device-other", "Ravi")
        assertNotEquals(ravi, other)
        assertEquals("Ravi 2", host.state.value.player(other)!!.name)
        assertFalse(host.state.value.player(other)!!.onBrowser)
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
