package com.threepatti.core

import com.threepatti.core.GameAction.AdjustChips
import com.threepatti.core.GameAction.Call
import com.threepatti.core.GameAction.CancelRound
import com.threepatti.core.GameAction.Check
import com.threepatti.core.GameAction.DeclareWinners
import com.threepatti.core.GameAction.Pack
import com.threepatti.core.GameAction.RaiseTo
import com.threepatti.core.GameAction.SeeCards
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.GameAction.UpdateSettings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// Seats: p1 Asha (host), p2 Ravi, p3 Meena, p4 Kiran. Blinds 1/2, 100 chips each unless changed.
class PokerEngineTest {
    private val noLimit = TableSettings(game = GameType.POKER, startingBalance = 100, smallBlind = 1, bigBlind = 2)
    private val potLimit = noLimit.copy(betLimit = BetLimit.POT_LIMIT)

    private fun table(vararg names: String = arrayOf("Ravi", "Meena"), settings: TableSettings = noLimit): GameState {
        var state = GameEngine.newTable("Poker night", settings, "Asha")
        for (name in names) state = GameEngine.addPlayer(state, name, hasDevice = true).first
        return state
    }

    private fun GameState.act(action: GameAction, actor: Actor = Actor.Host): GameState = GameEngine.apply(this, action, actor)

    private fun GameState.balance(id: String) = player(id)!!.balance

    private fun GameState.rejects(action: GameAction, actor: Actor = Actor.Host): String =
        assertFailsWith<GameRuleException> { act(action, actor) }.message!!

    private fun assertChipsConserved(state: GameState) {
        val pot = state.round?.takeIf { it.isActive }?.pot ?: 0
        assertEquals(state.players.sumOf { it.buyIn }, state.players.sumOf { it.balance } + pot, "chips were created or lost")
    }

    @Test
    fun blindsArePostedAndPlayerAfterBigBlindActsFirst() {
        val s = table().act(StartRound)
        val round = s.round!!
        assertEquals("p1", round.dealerId)
        assertEquals("p2", round.smallBlindId)
        assertEquals("p3", round.bigBlindId)
        assertEquals("p1", round.turnId, "three-handed the dealer is first to act pre-flop")
        assertEquals(3, round.pot)
        assertEquals(listOf(100, 99, 98), s.players.map { it.balance })
        assertEquals(2, round.currentBet)
        assertEquals(Street.PREFLOP, round.street)
        assertChipsConserved(s)
    }

    @Test
    fun headsUpDealerPostsSmallBlindAndActsFirstPreflopOnly() {
        var s = table("Ravi").act(StartRound)
        assertEquals("p1", s.round!!.smallBlindId)
        assertEquals("p2", s.round!!.bigBlindId)
        assertEquals("p1", s.round!!.turnId)
        s = s.act(Call("p1")).act(Check("p2"))
        assertEquals(Street.FLOP, s.round!!.street)
        assertEquals("p2", s.round!!.turnId, "after the flop the big blind acts first heads-up")
    }

    @Test
    fun bigBlindGetsTheOptionThenFlopStartsAfterDealer() {
        var s = table().act(StartRound)
        s = s.act(Call("p1")).act(Call("p2"))
        assertEquals(Street.PREFLOP, s.round!!.street)
        assertEquals("p3", s.round!!.turnId)
        val options = PokerRules.options(s, "p3")
        assertTrue(options.canCheck)
        assertTrue(options.canRaise)
        s = s.act(Check("p3"))
        val round = s.round!!
        assertEquals(Street.FLOP, round.street)
        assertEquals("p2", round.turnId)
        assertEquals(0, round.currentBet)
        assertTrue(round.hands.all { it.streetBet == 0 && !it.acted })
        assertEquals(6, round.pot)
    }

    @Test
    fun raisesMustBeAtLeastTheLastRaise() {
        var s = table().act(StartRound)
        assertEquals("Raise to at least ₹4", s.rejects(RaiseTo("p1", 3)))
        s = s.act(RaiseTo("p1", 6))
        assertEquals(4, s.round!!.minRaise)
        assertEquals("Raise to at least ₹10", s.rejects(RaiseTo("p2", 9)))
        s = s.act(RaiseTo("p2", 10))
        assertEquals(10, s.round!!.currentBet)
        assertEquals(90, s.balance("p2"))
        assertEquals("You need to call ₹8 or fold", s.rejects(Check("p3")))
        assertEquals("It's Meena's turn", s.rejects(Call("p1")))
    }

    @Test
    fun noLimitAllowsAllIn() {
        var s = table().act(StartRound)
        assertEquals(100, PokerRules.options(s, "p1").maxRaiseTo)
        s = s.act(RaiseTo("p1", 100))
        assertTrue(s.round!!.hand("p1")!!.allIn)
        assertEquals(0, s.balance("p1"))
        assertEquals("Asha is all-in for ₹100", s.log.last().text)
        val ravi = PokerRules.options(s, "p2")
        assertTrue(ravi.canCall && ravi.callIsAllIn)
        assertEquals(99, ravi.toCall)
        assertFalse(ravi.canRaise, "calling already puts all of Ravi's chips in")
    }

    @Test
    fun potLimitCapsBetsAtThePot() {
        var s = table(settings = potLimit).act(StartRound)
        val o = PokerRules.options(s, "p1")
        assertEquals(7, o.maxRaiseTo, "pre-flop pot raise with 1/2 blinds is to 7")
        assertEquals(7, o.potRaiseTo)
        assertEquals("Pot limit: the most you can make it is ₹7", s.rejects(RaiseTo("p1", 8)))
        s = s.act(RaiseTo("p1", 7))
        // Pot is now 10, Ravi (small blind) owes 6: pot raise is 7 + 10 + 6 = 23.
        assertEquals(23, PokerRules.options(s, "p2").maxRaiseTo)
        s = s.act(Call("p2")).act(Call("p3"))
        assertEquals(Street.FLOP, s.round!!.street)
        assertEquals(21, s.round!!.pot)
        val flop = PokerRules.options(s, "p2")
        assertTrue(flop.isBet)
        assertEquals(21, flop.maxRaiseTo)
        assertEquals(10, flop.halfPotRaiseTo)
        assertEquals(2, flop.minRaiseTo)
    }

    @Test
    fun lastPlayerLeftWinsWhenEveryoneFolds() {
        var s = table().act(StartRound)
        s = s.act(Pack("p1")).act(Pack("p2"))
        val round = s.round!!
        assertEquals(RoundPhase.FINISHED, round.phase)
        assertEquals(listOf("p3"), round.winnerIds)
        assertEquals(101, s.balance("p3"))
        assertEquals(mapOf("p1" to 0, "p2" to -1, "p3" to 1), s.results.single().changes)
        assertEquals("Meena won ₹3", s.log.last().text)
        assertChipsConserved(s)
    }

    @Test
    fun handGoesThroughAllStreetsToShowdown() {
        var s = table().act(StartRound)
        s = s.act(Call("p1")).act(Call("p2")).act(Check("p3"))
        for (street in listOf(Street.FLOP, Street.TURN, Street.RIVER)) {
            assertEquals(street, s.round!!.street)
            s = s.act(Check("p2")).act(Check("p3")).act(Check("p1"))
        }
        val round = s.round!!
        assertEquals(RoundPhase.SHOWDOWN, round.phase)
        assertEquals(listOf(Pot(6, listOf("p1", "p2", "p3"))), round.pots)
        assertEquals("Showdown · Pot ₹6", describeRound(s, round))
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(104, s.balance("p2"))
        assertChipsConserved(s)
    }

    @Test
    fun allInsMakeSidePotsAndUncalledChipsGoBack() {
        var s = table()
        s = s.act(AdjustChips("p2", -70)).act(AdjustChips("p3", -40))
        s = s.act(StartRound)
        s = s.act(RaiseTo("p1", 100)).act(Call("p2")).act(Call("p3"))
        val round = s.round!!
        assertEquals(RoundPhase.SHOWDOWN, round.phase, "everyone is all-in, nothing left to bet")
        assertEquals(
            listOf(Pot(90, listOf("p1", "p2", "p3")), Pot(60, listOf("p1", "p3")), Pot(40, listOf("p1"))),
            round.pots,
        )
        assertEquals("Showdown · Main pot ₹90", describeRound(s, round))
        assertEquals("Ravi can't win the side pot 1", s.act(DeclareWinners(listOf("p2"))).rejects(DeclareWinners(listOf("p2"))))
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals("Showdown · Side pot 1 ₹60", describeRound(s, s.round!!))
        s = s.act(DeclareWinners(listOf("p3")))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(listOf(40, 90, 60), s.players.map { it.balance })
        assertEquals(listOf("p2", "p3"), s.round!!.winnerIds)
        assertEquals(150, s.results.single().pot)
        assertEquals("Ravi won ₹90, Meena won ₹60", winnerText(s, s.round!!))
        assertChipsConserved(s)
    }

    @Test
    fun shortAllInDoesNotReopenRaising() {
        var s = table().act(AdjustChips("p3", -85)).act(StartRound)
        s = s.act(RaiseTo("p1", 10)).act(Call("p2"))
        s = s.act(RaiseTo("p3", 15))
        assertTrue(s.round!!.hand("p3")!!.allIn)
        val asha = PokerRules.options(s, "p1")
        assertTrue(asha.canCall)
        assertEquals(5, asha.toCall)
        assertFalse(asha.canRaise)
        assertEquals("You can only call or fold after a short all-in", s.rejects(RaiseTo("p1", 30)))
        s = s.act(Call("p1")).act(Call("p2"))
        assertEquals(Street.FLOP, s.round!!.street)
        assertEquals(45, s.round!!.pot)
    }

    @Test
    fun foldingOutOfTurnKeepsTheTurn() {
        var s = table("Ravi", "Meena", "Kiran").act(StartRound)
        assertEquals("p4", s.round!!.turnId)
        s = s.act(Pack("p2"))
        assertEquals("p4", s.round!!.turnId)
        s = s.act(Call("p4")).act(Call("p1"))
        assertEquals("p3", s.round!!.turnId, "big blind still has the option")
    }

    @Test
    fun dealerAndBlindsMoveEachHandAndCancelKeepsDealer() {
        var s = table().act(StartRound).act(Pack("p1")).act(Pack("p2"))
        s = s.act(StartRound)
        assertEquals("p2", s.round!!.dealerId)
        assertEquals("p3", s.round!!.smallBlindId)
        assertEquals("p1", s.round!!.bigBlindId)
        s = s.act(RaiseTo("p2", 20)).act(CancelRound)
        assertNull(s.round)
        assertEquals("Hand 2 cancelled, all bets returned", s.log.last().text)
        assertEquals("p2", s.act(StartRound).round!!.dealerId)
        assertChipsConserved(s)
    }

    @Test
    fun movesBelongToTheRightGame() {
        val s = table().act(StartRound)
        assertEquals("That move is not part of poker", s.rejects(SeeCards("p1")))
        val teenPatti = GameEngine.newTable("T", TableSettings(), "Asha")
        assertEquals("That move is not part of 3 Patti", teenPatti.rejects(Check("p1")))
        assertEquals(
            "This table plays no-limit poker. Open a new table to play 3 Patti",
            table().rejects(UpdateSettings(TableSettings())),
        )
        assertEquals("You can only play for yourself", s.rejects(Call("p1"), Actor.Remote("p2")))
        assertEquals(98, s.act(Call("p1"), Actor.Remote("p1")).balance("p1"))
    }

    @Test
    fun settingsValidation() {
        assertNull(noLimit.validationError())
        assertEquals("Big blind can't be smaller than the small blind", noLimit.copy(bigBlind = 0).validationError())
        assertEquals("Big blind can't be more than the starting chips", noLimit.copy(bigBlind = 500).validationError())
        assertEquals("Blinds ₹1/₹2 · pot limit", potLimit.summary())
        assertEquals("pot-limit poker", potLimit.gameName())
    }

    @Test
    fun randomLegalPlayNeverCreatesOrLosesChips() {
        for (settings in listOf(noLimit, potLimit)) {
            for (seed in 1..4) playRandomly(settings, seed)
        }
    }

    private fun playRandomly(settings: TableSettings, seed: Int) {
        val random = Random(seed)
        var s = table("Ravi", "Meena", "Kiran", "Sonu", settings = settings)
        var hands = 0
        repeat(3_000) { step ->
            val round = s.round?.takeIf { it.isActive }
            val action: GameAction = when {
                round == null -> {
                    s.players.filter { it.balance < 5 }.forEach { s = s.act(AdjustChips(it.id, 100)) }
                    StartRound
                }
                round.phase == RoundPhase.SHOWDOWN -> {
                    val pot = round.pots[round.potWinners.size]
                    assertTrue(pot.eligibleIds.size > 1, "single-player pots are returned automatically")
                    DeclareWinners(pot.eligibleIds.shuffled(random).take(1 + random.nextInt(pot.eligibleIds.size)))
                }
                random.nextInt(25) == 0 -> {
                    // Host folds someone out of turn now and then.
                    val candidates = round.hands.filter { it.status != HandStatus.PACKED && !it.allIn }
                    Pack(candidates.random(random).playerId)
                }
                else -> {
                    val id = round.turnId ?: fail("Seed $seed step $step: betting with nobody to act")
                    val o = PokerRules.options(s, id)
                    assertTrue(o.isTurn, "turn given to a player who can't act")
                    val choices = buildList {
                        add(Pack(id))
                        if (o.canCheck) repeat(4) { add(Check(id)) }
                        if (o.canCall) repeat(4) { add(Call(id)) }
                        if (o.canRaise) {
                            add(RaiseTo(id, o.minRaiseTo))
                            add(RaiseTo(id, o.maxRaiseTo))
                            add(RaiseTo(id, o.minRaiseTo + random.nextInt(o.maxRaiseTo - o.minRaiseTo + 1)))
                        }
                    }
                    choices.random(random)
                }
            }
            s = try {
                s.act(action)
            } catch (e: GameRuleException) {
                fail("${settings.gameName()} seed $seed step $step: allowed action $action was rejected: ${e.message}")
            }
            val after = s.round
            if (after?.phase == RoundPhase.FINISHED && action !is StartRound) hands++
            if (after?.phase == RoundPhase.SHOWDOWN) assertEquals(after.pot, after.pots.sumOf { it.amount })
            assertChipsConserved(s)
        }
        assertTrue(hands > 100, "${settings.gameName()} seed $seed: only $hands hands finished")
    }
}
