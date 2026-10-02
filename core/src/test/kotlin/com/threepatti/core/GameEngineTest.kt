package com.threepatti.core

import com.threepatti.core.GameAction.AdjustChips
import com.threepatti.core.GameAction.AllIn
import com.threepatti.core.GameAction.AnswerSideShow
import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.CancelRound
import com.threepatti.core.GameAction.DeclareWinners
import com.threepatti.core.GameAction.EnterCards
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
        assertEquals("Ravi needs 10 chips but has only 7", s.rejects(Bet("p2")))
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

    private fun GameState.winRound(vararg winners: String): GameState =
        act(StartRound).act(ForceShow).act(DeclareWinners(winners.toList()))

    @Test
    fun winnerDealsNextRoundSoThePlayerAfterThemGoesFirst() {
        var s = table("Ravi", "Meena", "Kiran").winRound("p3")
        assertEquals("p3" to "p4", GameEngine.nextDeal(s))
        assertEquals("Meena deals next, Kiran goes first", nextDealText(s))
        s = s.act(StartRound)
        assertNull(GameEngine.nextDeal(s))
        assertEquals("p3", s.round!!.dealerId)
        assertEquals("p4", s.round!!.turnId)
        assertTrue(s.log.last().text.startsWith("Round 2 started. Meena deals, Kiran goes first."))
        s = s.act(ForceShow).act(DeclareWinners(listOf("p4"))).act(StartRound)
        assertEquals("p4", s.round!!.dealerId)
        assertEquals("p1", s.round!!.turnId)
    }

    @Test
    fun playerAfterWinnerGoesFirstEvenWhenWinnerSitsOut() {
        var s = table("Ravi", "Meena", "Kiran", "Dev").winRound("p2")
        s = s.act(SetSittingOut("p2", true))
        assertEquals("p1" to "p3", GameEngine.nextDeal(s))
        s = s.act(StartRound)
        // Asha deals in Ravi's place, so Meena, who sits after Ravi, still goes first.
        assertEquals("p1", s.round!!.dealerId)
        assertEquals("p3", s.round!!.turnId)
        s = s.act(CancelRound)
        s = s.act(AdjustChips("p3", -s.balance("p3") + 2)).act(StartRound)
        // Meena can't pay the boot either, so Kiran is the first player after Ravi.
        assertEquals(listOf("p1", "p4", "p5"), s.round!!.hands.map { it.playerId })
        assertEquals("p1", s.round!!.dealerId)
        assertEquals("p4", s.round!!.turnId)
        assertTrue(s.log.last().text.contains("Meena can't pay the boot"))
    }

    @Test
    fun splitPotIsDealtByTheFirstWinnerAfterTheDealer() {
        // Round 1 is dealt by Asha. Meena and Kiran split it, and Meena comes first after Asha.
        var s = table("Ravi", "Meena", "Kiran").winRound("p4", "p3").act(StartRound)
        assertEquals("p3", s.round!!.dealerId)
        assertEquals("p4", s.round!!.turnId)
        // Round 2 is dealt by Meena. Asha and Ravi split it; after Meena comes Kiran, then Asha.
        s = s.act(ForceShow).act(DeclareWinners(listOf("p1", "p2"))).act(StartRound)
        assertEquals("p1", s.round!!.dealerId)
        assertEquals("p2", s.round!!.turnId)
    }

    @Test
    fun misdealIsDealtAgainBySameWinner() {
        var s = table("Ravi", "Meena", "Kiran").winRound("p3").act(StartRound).act(Bet("p4"))
        s = s.act(CancelRound).act(StartRound)
        assertEquals("p3", s.round!!.dealerId)
        assertEquals("p4", s.round!!.turnId)
    }

    @Test
    fun dealerMovesOneSeatWhenWinnerLeftTheTable() {
        var s = table("Ravi", "Meena", "Kiran").winRound("p3")
        // A winner can only be removed once they're back to even, so take the seat away directly.
        s = s.copy(players = s.players.filter { it.id != "p3" }).act(StartRound)
        assertEquals("p2", s.round!!.dealerId)
        assertEquals("p4", s.round!!.turnId)
    }

    /** Gives a player exactly [chips] chips before the round. */
    private fun GameState.withChips(id: String, chips: Int): GameState = act(AdjustChips(id, chips - balance(id)))

    @Test
    fun shortPlayerGoesAllInAndTheRestBuildASidePot() {
        var s = table("Ravi", "Meena", "Kiran").withChips("p2", 12).act(StartRound)   // Ravi has 7 after the boot
        s = s.act(Bet("p2")).act(Bet("p3")).act(Bet("p4")).act(Bet("p1"))           // blind 5 each, pot 40
        val options = Rules.seatOptions(s, "p2")
        assertEquals("Ravi needs 5 chips but has only 2", options.callError)
        assertTrue(options.canAllIn)
        assertEquals(2, options.allInAmount)

        s = s.act(AllIn("p2"))
        assertEquals("Meena has enough chips to play blind", s.rejects(AllIn("p3")))
        assertEquals("Ravi is all in with 2 chips", s.log.last().text)
        assertEquals(listOf(AllInBreak(42, listOf("p2"))), s.round!!.allInBreaks)
        assertEquals("p3", s.round!!.turnId)
        s = s.act(Bet("p3")).act(Bet("p4")).act(Bet("p1"))
        assertEquals("p3", s.round!!.turnId, "all-in Ravi is skipped")
        assertTrue(Rules.seatOptions(s, "p2").isAllIn)

        s = s.act(Bet("p3")).act(Pack("p4")).act(Pack("p1"))
        // Only Meena can still bet, so everyone left shows.
        val round = s.round!!
        assertEquals(RoundPhase.SHOWDOWN, round.phase)
        assertEquals(listOf(Pot(42, listOf("p2", "p3")), Pot(20, listOf("p3"))), round.pots)
        assertEquals("Show · Main pot: 42 chips", describeRound(s, round))
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(42, s.balance("p2"))
        assertEquals(250 - 20 + 20, s.balance("p3"), "Meena gets back the side pot nobody else could win")
        assertTrue(s.log.any { it.text == "Meena gets back 20 chips nobody called" })
        assertChipsConserved(s)
    }

    @Test
    fun lastChipMakesAPlayerAllIn() {
        var s = table("Ravi", "Meena").withChips("p2", 10).act(StartRound)
        s = s.act(Bet("p2"))
        assertEquals(0, s.balance("p2"))
        assertTrue(s.round!!.hand("p2")!!.allIn)
        assertEquals("Ravi is all in", s.log.last().text)
        s = s.act(Bet("p3"))
        assertEquals("p1", s.round!!.turnId)
        // Asha packs, leaving Meena as the only one who can bet: show.
        s = s.act(Pack("p1"))
        assertEquals(RoundPhase.SHOWDOWN, s.round!!.phase)
        assertEquals(listOf("p2", "p3"), s.round!!.showdownIds)
        // Ravi can win the 20 in the pot when he went all in, not Meena's blind after it.
        assertEquals(listOf(Pot(20, listOf("p2", "p3")), Pot(5, listOf("p3"))), s.round!!.pots)
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals(20, s.balance("p2"))
        assertEquals(245, s.balance("p3"))
        assertChipsConserved(s)

        // Heads-up nobody can bet after Ravi's last chip, so the show starts at once with a single pot.
        val headsUp = table("Ravi").withChips("p2", 10).act(StartRound).act(Bet("p2"))
        assertEquals(RoundPhase.SHOWDOWN, headsUp.round!!.phase)
        assertTrue(headsUp.round!!.pots.isEmpty())
    }

    @Test
    fun bootCanPutAPlayerAllIn() {
        var s = table("Ravi", "Meena", "Kiran").withChips("p2", 5)
        assertEquals("p1" to "p3", GameEngine.nextDeal(s), "Ravi can't bet after the boot, so Meena goes first")
        s = s.act(StartRound)
        assertTrue(s.round!!.hand("p2")!!.allIn)
        assertEquals("p3", s.round!!.turnId)
        assertTrue(s.log.any { it.text == "Ravi is all in with the boot" })
        assertTrue(s.log.any { it.text.startsWith("Round 1 started. Asha deals, Meena goes first.") })
        // Heads-up, an all-in boot leaves nothing to bet on.
        val headsUp = table("Ravi").withChips("p2", 5).act(StartRound)
        assertEquals(RoundPhase.SHOWDOWN, headsUp.round!!.phase)
        assertEquals(listOf("p1", "p2"), headsUp.round!!.showdownIds)
    }

    @Test
    fun twoAllInsMakeAMainPotAndTwoSidePots() {
        var s = table("Ravi", "Meena", "Kiran").withChips("p2", 12).withChips("p3", 30).act(StartRound)
        s = s.act(Bet("p2")).act(Bet("p3")).act(Bet("p4")).act(Bet("p1"))
        s = s.act(AllIn("p2"))                                                     // pot 42
        s = s.act(SeeCards("p3")).act(Bet("p3")).act(Bet("p4")).act(Bet("p1"))    // pot 62, Meena has 10
        s = s.act(Bet("p3"))                                                       // chaal with her last 10
        assertTrue(s.round!!.hand("p3")!!.allIn)
        s = s.act(Bet("p4")).act(Bet("p1")).act(Pack("p4"))                        // pot 82, only Asha can bet
        val round = s.round!!
        assertEquals(
            listOf(Pot(42, listOf("p1", "p2", "p3")), Pot(30, listOf("p1", "p3")), Pot(10, listOf("p1"))),
            round.pots,
        )
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals("Ravi can't win the side pot 1", s.rejects(DeclareWinners(listOf("p2"))))
        s = s.act(DeclareWinners(listOf("p3")))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(42, s.balance("p2"))
        assertEquals(30, s.balance("p3"))
        assertEquals(240, s.balance("p1"), "Asha's last bet nobody could match comes back")
        assertEquals("Ravi won 42 chips, Meena won 30 chips", winnerText(s, s.round!!))
        assertChipsConserved(s)
    }

    @Test
    fun cardsEnteredStayLockedOnceShown() {
        var s = table("Ravi", "Meena").act(StartRound)
        assertEquals("See your cards first", s.rejects(EnterCards("p2", listOf("AS", "KS", "QS"))))
        s = s.act(SeeCards("p2")).act(EnterCards("p2", listOf("AS", "KS", "QS")))
        assertEquals(listOf("AS", "KS", "QS"), s.round!!.hand("p2")!!.cards)
        s = s.act(EnterCards("p2", listOf("2C", "2D", "9H")))                      // can still change before it's shown
        assertEquals("Enter 3 cards", s.rejects(EnterCards("p2", listOf("AS", "KS"))))
        assertEquals("A♠ is entered twice", s.rejects(EnterCards("p2", listOf("AS", "AS", "KD"))))
        assertEquals("Unknown card 1X", s.rejects(EnterCards("p2", listOf("1X", "AS", "KD"))))
        s = s.act(ForceShow)
        assertTrue(s.round!!.hand("p2")!!.cardsShown)
        assertEquals("Ravi's cards were shown, so they can't change", s.rejects(EnterCards("p2", listOf("AS", "KS", "QS"))))
        // A blind player can enter their cards once they're on show.
        s = s.act(EnterCards("p3", listOf("7S", "7H", "7D")))
        assertEquals(listOf("7S", "7H", "7D"), s.round!!.hand("p3")!!.cards)
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
        assertEquals("Ravi is at -5. Even out their chips first, or let them sit out", s.rejects(RemovePlayer("p2")))
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
                round.phase == RoundPhase.SHOWDOWN -> {
                    // After an all-in only the players in the current pot can win it.
                    val ids = round.pots.getOrNull(round.potWinners.size)?.eligibleIds ?: round.showdownIds
                    DeclareWinners(ids.shuffled(random).take(1 + random.nextInt(ids.size)))
                }
                round.phase == RoundPhase.SIDE_SHOW_COMPARE ->
                    DeclareWinners(listOf(listOf(round.sideShow!!.requesterId, round.sideShow!!.targetId).random(random)))
                round.phase == RoundPhase.SIDE_SHOW_REQUESTED ->
                    AnswerSideShow(round.sideShow!!.targetId, random.nextBoolean())
                else -> {
                    val id = round.turnId!!
                    assertTrue(round.hand(id)!!.status != HandStatus.PACKED, "turn given to a packed player")
                    assertTrue(!round.hand(id)!!.allIn, "turn given to an all-in player")
                    val o = Rules.seatOptions(s, id)
                    val choices = buildList {
                        if (o.canSee) add(SeeCards(id))
                        if (o.canAllIn) repeat(4) { add(AllIn(id)) }
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
