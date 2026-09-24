package com.threepatti.core

import com.threepatti.core.GameAction.AdjustChips
import com.threepatti.core.GameAction.AnswerSideShow
import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.CancelRound
import com.threepatti.core.GameAction.DeclareWinners
import com.threepatti.core.GameAction.ForceShow
import com.threepatti.core.GameAction.MoveSeat
import com.threepatti.core.GameAction.Pack
import com.threepatti.core.GameAction.RemovePlayer
import com.threepatti.core.GameAction.RenamePlayer
import com.threepatti.core.GameAction.RequestSideShow
import com.threepatti.core.GameAction.SeeCards
import com.threepatti.core.GameAction.SetSittingOut
import com.threepatti.core.GameAction.Show
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.GameAction.UpdateSettings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// Seats: p1 Asha (host), p2 Ravi, p3 Meena, p4 Kiran.
class GameEngineTest {
    private fun table(vararg names: String = arrayOf("Ravi", "Meena"), settings: TableSettings = TableSettings()): GameState {
        var state = GameEngine.newTable("Test table", settings, "Asha")
        for (name in names) state = GameEngine.addPlayer(state, name, hasDevice = true).first
        return state
    }

    private fun GameState.act(action: GameAction, actor: Actor = Actor.Host): GameState =
        GameEngine.apply(this, action, actor)

    private fun GameState.balance(id: String): Int = player(id)!!.balance

    private fun GameState.rejects(action: GameAction, actor: Actor = Actor.Host): String =
        assertFailsWith<GameRuleException> { act(action, actor) }.message!!

    private fun assertChipsConserved(state: GameState) {
        val pot = state.round?.takeIf { it.isActive }?.pot ?: 0
        assertEquals(state.players.sumOf { it.buyIn }, state.players.sumOf { it.balance } + pot, "chips were created or lost")
    }

    @Test
    fun startRoundCollectsBootAndGivesTurnToPlayerAfterDealer() {
        val state = table().act(StartRound)
        val round = state.round!!
        assertEquals(15, round.pot)
        assertEquals(5, round.stake)
        assertEquals("p1", round.dealerId)
        assertEquals("p2", round.turnId)
        assertTrue(round.hands.all { it.status == HandStatus.BLIND && it.invested == 5 })
        assertTrue(state.players.all { it.balance == 245 })
        assertChipsConserved(state)
    }

    @Test
    fun blindPaysStakeSeenPaysDoubleAndRaiseDoublesStake() {
        var s = table().act(StartRound)
        s = s.act(Bet("p2"))
        assertEquals(240, s.balance("p2"))
        assertEquals("p3", s.round!!.turnId)

        s = s.act(SeeCards("p3")).act(Bet("p3"))
        assertEquals(235, s.balance("p3"))

        s = s.act(Bet("p1", raise = true))
        assertEquals(235, s.balance("p1"))
        assertEquals(10, s.round!!.stake)

        s = s.act(Bet("p2")).act(Bet("p3"))
        assertEquals(230, s.balance("p2"))
        assertEquals(215, s.balance("p3"))
        assertEquals(15 + 5 + 10 + 10 + 10 + 20, s.round!!.pot)
        assertChipsConserved(s)
    }

    @Test
    fun onlyThePlayerWhoseTurnItIsCanBet() {
        val s = table().act(StartRound)
        assertEquals("It's Ravi's turn", s.rejects(Bet("p3")))
    }

    @Test
    fun chaalLimitStopsRaises() {
        var s = table(settings = TableSettings(bootAmount = 5, maxSeenBet = 20)).act(StartRound)
        assertTrue(Rules.canRaise(s.settings, s.round!!))
        s = s.act(Bet("p2", raise = true))
        assertEquals("Chaal limit reached, no more raises", s.rejects(Bet("p3", raise = true)))
        s = s.act(SeeCards("p3")).act(Bet("p3"))
        assertEquals(20, s.round!!.hand("p3")!!.invested - 5)
    }

    @Test
    fun lastPlayerStandingWinsThePot() {
        var s = table().act(StartRound)
        s = s.act(Pack("p2"))
        assertEquals("p3", s.round!!.turnId)
        s = s.act(Pack("p3"))
        val round = s.round!!
        assertEquals(RoundPhase.FINISHED, round.phase)
        assertEquals(listOf("p1"), round.winnerIds)
        assertEquals(260, s.balance("p1"))
        assertEquals(mapOf("p1" to 10, "p2" to -5, "p3" to -5), s.results.single().changes)
        assertChipsConserved(s)
    }

    @Test
    fun packingOutOfTurnKeepsTheTurn() {
        var s = table("Ravi", "Meena", "Kiran").act(StartRound)
        s = s.act(Pack("p4"))
        assertEquals("p2", s.round!!.turnId)
        s = s.act(Bet("p2"))
        assertEquals("p3", s.round!!.turnId)
        s = s.act(Bet("p3"))
        assertEquals("p1", s.round!!.turnId, "packed player is skipped")
    }

    @Test
    fun showBetweenTwoPlayersWaitsForHostThenPaysWinner() {
        var s = table("Ravi").act(StartRound)
        assertEquals("p2", s.round!!.turnId)
        s = s.act(SeeCards("p2")).act(Show("p2"))
        val round = s.round!!
        assertEquals(RoundPhase.SHOWDOWN, round.phase)
        assertEquals(listOf("p1", "p2"), round.showdownIds)
        assertEquals(20, round.pot)
        s = s.act(DeclareWinners(listOf("p1")))
        assertEquals(265, s.balance("p1"))
        assertEquals(235, s.balance("p2"))
        assertChipsConserved(s)
    }

    @Test
    fun showNeedsExactlyTwoPlayers() {
        val s = table().act(StartRound)
        assertEquals("Show is allowed only when 2 players are left", s.rejects(Show("p2")))
    }

    @Test
    fun acceptedSideShowPacksTheLoser() {
        var s = table().act(StartRound)
        s = s.act(SeeCards("p2")).act(Bet("p2"))
        s = s.act(SeeCards("p3")).act(RequestSideShow("p3"))
        assertEquals(RoundPhase.SIDE_SHOW_REQUESTED, s.round!!.phase)
        assertEquals(SideShow("p3", "p2"), s.round!!.sideShow)
        assertEquals(235, s.balance("p3"))

        assertEquals("Only Ravi can answer this side show", s.rejects(AnswerSideShow("p1", true)))
        s = s.act(AnswerSideShow("p2", accept = true), Actor.Remote("p2"))
        assertEquals(RoundPhase.SIDE_SHOW_COMPARE, s.round!!.phase)

        s = s.act(DeclareWinners(listOf("p3")))
        assertEquals(HandStatus.PACKED, s.round!!.hand("p2")!!.status)
        assertEquals(RoundPhase.BETTING, s.round!!.phase)
        assertEquals("p1", s.round!!.turnId)
        assertChipsConserved(s)
    }

    @Test
    fun refusedSideShowMovesTurnOn() {
        var s = table().act(StartRound)
        s = s.act(SeeCards("p2")).act(Bet("p2"))
        s = s.act(SeeCards("p3")).act(RequestSideShow("p3"))
        s = s.act(AnswerSideShow("p2", accept = false))
        assertEquals(RoundPhase.BETTING, s.round!!.phase)
        assertNull(s.round!!.sideShow)
        assertEquals("p1", s.round!!.turnId)
    }

    @Test
    fun sideShowNeedsBothPlayersSeen() {
        var s = table().act(StartRound)
        s = s.act(Bet("p2"))
        s = s.act(SeeCards("p3"))
        assertEquals("Ravi hasn't seen their cards yet", s.rejects(RequestSideShow("p3")))
    }

    @Test
    fun potLimitForcesShowForEveryone() {
        var s = table(settings = TableSettings(potLimit = 30)).act(StartRound)
        s = s.act(Bet("p2")).act(Bet("p3")).act(Bet("p1"))
        val round = s.round!!
        assertEquals(30, round.pot)
        assertEquals(RoundPhase.SHOWDOWN, round.phase)
        assertEquals(listOf("p1", "p2", "p3"), round.showdownIds)
    }

    @Test
    fun blindLimitForcesPlayerToSee() {
        var s = table(settings = TableSettings(maxBlindTurns = 1)).act(StartRound)
        s = s.act(Bet("p2")).act(Bet("p3")).act(Bet("p1"))
        assertEquals("Blind limit reached. See your cards to continue", s.rejects(Bet("p2")))
        s = s.act(SeeCards("p2")).act(Bet("p2"))
        assertEquals(230, s.balance("p2"))
    }

    @Test
    fun notEnoughChipsUntilTopUp() {
        var s = table(settings = TableSettings(startingBalance = 12, bootAmount = 5, maxSeenBet = 0)).act(StartRound)
        s = s.act(SeeCards("p2"))
        assertEquals("Ravi needs ₹10 but has only ₹7", s.rejects(Bet("p2")))
        s = s.act(AdjustChips("p2", 20))
        assertEquals(27, s.balance("p2"))
        assertEquals(32, s.player("p2")!!.buyIn)
        s = s.act(Bet("p2"))
        assertEquals(17, s.balance("p2"))
        assertChipsConserved(s)
    }

    @Test
    fun splitPotGivesOddChipToFirstWinner() {
        var s = table().act(StartRound).act(ForceShow)
        s = s.act(DeclareWinners(listOf("p3", "p2")))
        assertEquals(245 + 8, s.balance("p2"))
        assertEquals(245 + 7, s.balance("p3"))
        assertEquals(listOf("p2", "p3"), s.results.single().winnerIds)
        assertChipsConserved(s)
    }

    @Test
    fun cancelRoundRefundsEveryoneAndKeepsDealer() {
        var s = table().act(StartRound).act(Bet("p2", raise = true))
        s = s.act(CancelRound)
        assertNull(s.round)
        assertTrue(s.players.all { it.balance == 250 })
        assertEquals("p1", s.act(StartRound).round!!.dealerId)
    }

    @Test
    fun dealerRotatesAndSkipsPlayersSittingOutOrShortOfChips() {
        var s = table("Ravi", "Meena", "Kiran")
        s = s.act(StartRound).act(ForceShow).act(DeclareWinners(listOf("p1")))
        s = s.act(StartRound)
        assertEquals("p2", s.round!!.dealerId)
        s = s.act(ForceShow).act(DeclareWinners(listOf("p1")))
        s = s.act(SetSittingOut("p3", true)).act(AdjustChips("p4", -s.balance("p4") + 2))
        s = s.act(StartRound)
        val round = s.round!!
        assertEquals(listOf("p1", "p2"), round.hands.map { it.playerId })
        assertEquals("p1", round.dealerId)
        assertEquals("p2", round.turnId)
        assertTrue(s.log.last().text.contains("Kiran can't pay the boot"))
    }

    @Test
    fun playersCanOnlyActForThemselves() {
        val s = table().act(StartRound)
        assertEquals("You can only play for yourself", s.rejects(Bet("p2"), Actor.Remote("p3")))
        assertEquals("Only the host can do that", s.rejects(StartRound, Actor.Remote("p2")))
        assertEquals("Only the host can do that", s.rejects(AdjustChips("p2", 100), Actor.Remote("p2")))
        assertEquals(240, s.act(Bet("p2"), Actor.Remote("p2")).balance("p2"))
        assertEquals(240, s.act(Bet("p2"), Actor.Host).balance("p2"))
    }

    @Test
    fun removingPlayersNeedsASettledBalance() {
        var s = table("Ravi", "Meena", "Kiran")
        assertEquals("The host can't be removed", s.rejects(RemovePlayer("p1")))
        s = s.act(StartRound).act(Pack("p2")).act(Pack("p3")).act(Pack("p4"))
        assertEquals("Ravi is at -₹5. Settle up first, or let them sit out", s.rejects(RemovePlayer("p2")))
        s = s.act(AdjustChips("p2", 5)).act(AdjustChips("p2", -250)).act(AdjustChips("p2", 250))
        assertEquals(-5, s.player("p2")!!.net)
        val fresh = GameEngine.addPlayer(s, "Guest", hasDevice = false)
        val removed = fresh.first.act(RemovePlayer(fresh.second))
        assertNull(removed.player(fresh.second))
    }

    @Test
    fun seatsAndSettingsChangeOnlyBetweenRounds() {
        var s = table()
        s = s.act(MoveSeat("p3", -1))
        assertEquals(listOf("p1", "p3", "p2"), s.players.map { it.id })
        val running = s.act(StartRound)
        assertEquals("Change seats between rounds", running.rejects(MoveSeat("p3", 1)))
        assertEquals("Change settings between rounds", running.rejects(UpdateSettings(TableSettings(bootAmount = 10))))
        assertEquals(
            "Chaal limit must be at least 20 (twice the boot)",
            s.rejects(UpdateSettings(TableSettings(bootAmount = 10, maxSeenBet = 15))),
        )
        assertEquals(10, s.act(UpdateSettings(TableSettings(bootAmount = 10))).settings.bootAmount)
    }

    @Test
    fun namesAreCleanedAndKeptUnique() {
        var s = table("Ravi")
        s = GameEngine.addPlayer(s, "  ravi  ", hasDevice = true).first
        assertEquals("ravi 2", s.players.last().name)
        s = GameEngine.addPlayer(s, "   ", hasDevice = true).first
        assertEquals("Player 4", s.players.last().name)
        assertEquals("Someone is already called Ravi", s.rejects(RenamePlayer("p4", "RAVI")))
        assertEquals("Raju", s.act(RenamePlayer("p4", "Raju"), Actor.Remote("p4")).player("p4")!!.name)
    }

    @Test
    fun randomLegalPlayNeverCreatesOrLosesChips() {
        for (seed in 1..6) playRandomly(seed)
    }

    private fun playRandomly(seed: Int) {
        val random = Random(seed)
        val settings = TableSettings(startingBalance = 100, bootAmount = 5, maxSeenBet = 40, potLimit = 200, maxBlindTurns = 3)
        var s = table("Ravi", "Meena", "Kiran", "Sonu", settings = settings)
        var roundsFinished = 0
        repeat(3_000) { step ->
            val round = s.round?.takeIf { it.isActive }
            val action: GameAction = when {
                round == null -> {
                    s.players.filter { it.balance < settings.bootAmount }.forEach { s = s.act(AdjustChips(it.id, 100)) }
                    StartRound
                }
                round.phase == RoundPhase.SHOWDOWN ->
                    DeclareWinners(round.showdownIds.shuffled(random).take(1 + random.nextInt(round.showdownIds.size)))
                round.phase == RoundPhase.SIDE_SHOW_COMPARE ->
                    DeclareWinners(listOf(listOf(round.sideShow!!.requesterId, round.sideShow!!.targetId).random(random)))
                round.phase == RoundPhase.SIDE_SHOW_REQUESTED ->
                    AnswerSideShow(round.sideShow!!.targetId, random.nextBoolean())
                else -> {
                    val id = round.turnId!!
                    assertTrue(round.hand(id)!!.status != HandStatus.PACKED, "turn given to a packed player")
                    val o = Rules.seatOptions(s, id)
                    val choices = buildList {
                        if (o.canSee) add(SeeCards(id))
                        if (o.callError == null) repeat(4) { add(Bet(id)) }
                        if (o.raiseError == null) add(Bet(id, raise = true))
                        if (o.showAvailable && o.showError == null) add(Show(id))
                        if (o.sideShowAvailable && o.sideShowError == null) add(RequestSideShow(id))
                        add(Pack(id))
                    }
                    choices.random(random)
                }
            }
            s = try {
                s.act(action)
            } catch (e: GameRuleException) {
                fail("Seed $seed step $step: allowed action $action was rejected: ${e.message}")
            }
            if (s.round?.phase == RoundPhase.FINISHED && action !is StartRound) roundsFinished++
            assertChipsConserved(s)
        }
        assertTrue(roundsFinished > 50, "seed $seed: only $roundsFinished rounds finished")
        assertEquals(0, s.players.sumOf { it.net } + (s.round?.takeIf { it.isActive }?.pot ?: 0))
    }
}
