package com.threepatti.core

import com.threepatti.core.GameAction.AdjustChips
import com.threepatti.core.GameAction.AllIn
import com.threepatti.core.GameAction.AnswerSideShow
import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.DeclareWinners
import com.threepatti.core.GameAction.EnterCards
import com.threepatti.core.GameAction.ForceShow
import com.threepatti.core.GameAction.Pack
import com.threepatti.core.GameAction.RequestSideShow
import com.threepatti.core.GameAction.SeeCards
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.GameAction.UpdateSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Seats: p1 Asha (host), p2 Ravi, p3 Meena, p4 Kiran.
class EnteredCardsTest {
    private fun table(vararg names: String = arrayOf("Ravi", "Meena", "Kiran"), settings: TableSettings = TableSettings()): GameState {
        var state = GameEngine.newTable("Test table", settings, "Asha")
        for (name in names) state = GameEngine.addPlayer(state, name, hasDevice = true).first
        return state
    }

    private fun GameState.act(action: GameAction): GameState = GameEngine.apply(this, action)

    private fun GameState.rejects(action: GameAction): String =
        assertFailsWith<GameRuleException> { act(action) }.message!!

    private fun GameState.enter(playerId: String, vararg cards: String) = act(EnterCards(playerId, cards.toList()))

    /** Whose cards [viewerId] can see. */
    private fun GameState.cardsSeenBy(viewerId: String, enteredHere: Set<String> = emptySet()): List<String> =
        visibleTo(viewerId, enteredHere).round!!.hands.filter { it.cards.isNotEmpty() }.map { it.playerId }

    /** Ravi and Meena have seen and entered their cards, and Meena asked Ravi for a side show. */
    private fun sideShowAsked(ravi: List<String>, meena: List<String>): GameState =
        table().act(StartRound)
            .act(SeeCards("p2")).enter("p2", *ravi.toTypedArray()).act(Bet("p2"))
            .act(SeeCards("p3")).enter("p3", *meena.toTypedArray()).act(RequestSideShow("p3"))

    @Test
    fun cardsStayWithTheirPlayerUntilShown() {
        val s = sideShowAsked(listOf("AS", "KS", "QS"), listOf("2C", "2D", "9H"))
        assertEquals(listOf("p2"), s.cardsSeenBy("p2"))
        assertEquals(listOf("p3"), s.cardsSeenBy("p3"))
        assertEquals(emptyList(), s.cardsSeenBy("p4"))
        assertEquals(emptyList(), s.cardsSeenBy("p1"), "the host doesn't see them either")
        assertNull(s.round!!.suggestionNote)
        assertTrue(s.round!!.suggestedWinnerIds.isEmpty())
        // Nothing to hide gives back the same table.
        val plain = table().act(StartRound)
        assertTrue(plain.visibleTo("p4") === plain)
    }

    @Test
    fun sideShowPlayersSeeEachOthersCardsAndTheAppsVerdict() {
        var s = sideShowAsked(listOf("AS", "KS", "QS"), listOf("2C", "2D", "9H")).act(AnswerSideShow("p2", true))
        assertEquals(listOf("p2", "p3"), s.cardsSeenBy("p2"))
        assertEquals(listOf("p2", "p3"), s.cardsSeenBy("p3"))
        assertEquals(emptyList(), s.cardsSeenBy("p4"))
        assertEquals(emptyList(), s.cardsSeenBy("p1"))
        assertEquals(listOf("p2"), s.round!!.suggestedWinnerIds, "everyone sees who won, so the host can confirm")
        assertNull(s.round!!.suggestionNote)
        assertEquals("Pure sequence A-K-Q", s.handName(s.round!!.hand("p2")!!))

        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals(HandStatus.PACKED, s.round!!.hand("p3")!!.status)
        assertEquals(listOf("p2"), s.cardsSeenBy("p2"), "back to betting, the cards are private again")
        assertTrue(s.round!!.suggestedWinnerIds.isEmpty())
        assertEquals("Ravi's cards were shown, so they can't change", s.rejects(EnterCards("p2", listOf("2S", "3S", "4S"))))
    }

    @Test
    fun equalSideShowHandsMakeTheAskerPack() {
        val s = sideShowAsked(listOf("AS", "KD", "9C"), listOf("AH", "KC", "9D")).act(AnswerSideShow("p2", true))
        assertEquals(listOf("p2"), s.round!!.suggestedWinnerIds)
        assertEquals("Equal hands: the player who asked for the side show packs", s.round!!.suggestionNote)
    }

    @Test
    fun cardsEnteredDuringASideShowLockToo() {
        var s = table().act(StartRound)
            .act(SeeCards("p2")).act(Bet("p2"))
            .act(SeeCards("p3")).enter("p3", "QS", "QH", "4D").act(RequestSideShow("p3"))
            .act(AnswerSideShow("p2", true))
        assertEquals(listOf("p2"), s.round!!.awaitingCardsFrom)
        assertNull(s.round!!.suggestionNote)
        s = s.enter("p2", "JS", "JH", "JD")
        assertEquals(listOf("p2"), s.round!!.suggestedWinnerIds)
        assertTrue(s.round!!.awaitingCardsFrom.isEmpty())
        assertTrue(s.round!!.hand("p2")!!.cardsShown, "Meena has seen them")
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals("Ravi's cards were shown, so they can't change", s.rejects(EnterCards("p2", listOf("AS", "AH", "AD"))))
    }

    @Test
    fun showPutsEveryonesCardsOnTheTable() {
        var s = table().act(StartRound)
            .act(SeeCards("p2")).enter("p2", "KS", "KH", "2D")
            .act(SeeCards("p3")).enter("p3", "9C", "8C", "7C")
            .act(ForceShow)
        assertEquals(listOf("p2", "p3"), s.cardsSeenBy("p4"))
        assertEquals(listOf("p1", "p4"), s.round!!.awaitingCardsFrom)
        s = s.enter("p1", "AS", "JH", "5D").enter("p4", "3C", "3D", "QS")
        assertEquals(listOf("p1", "p2", "p3", "p4"), s.cardsSeenBy("p4"))
        assertEquals(listOf("p3"), s.round!!.suggestedWinnerIds)
        s = s.act(DeclareWinners(s.round!!.suggestedWinnerIds))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(listOf("p1", "p2", "p3", "p4"), s.cardsSeenBy("p2"), "cards shown stay on the table after the result")
        assertTrue(s.round!!.suggestedWinnerIds.isEmpty())
    }

    @Test
    fun aRoundWonByPackingShowsNoCards() {
        var s = table("Ravi").act(StartRound).act(SeeCards("p2")).enter("p2", "AS", "AH", "AD")
        s = s.act(Pack("p2"))
        assertEquals(RoundPhase.FINISHED, s.round!!.phase)
        assertEquals(emptyList(), s.cardsSeenBy("p1"))
    }

    @Test
    fun theTableVariantDecides() {
        val muflis = TableSettings(variant = Variant.MUFLIS)
        var s = table("Ravi", settings = muflis).act(StartRound)
            .act(SeeCards("p2")).enter("p2", "AS", "AH", "AD")
            .act(SeeCards("p1")).enter("p1", "5C", "3D", "2H")
            .act(ForceShow)
        assertEquals(listOf("p1"), s.round!!.suggestedWinnerIds, "lowest hand wins in Muflis")

        s = table("Ravi", settings = TableSettings(variant = Variant.JOKER, jokerRanks = setOf(7))).act(StartRound)
            .act(SeeCards("p2")).enter("p2", "7S", "9D", "9H")
            .act(SeeCards("p1")).enter("p1", "QS", "QH", "4D")
            .act(ForceShow)
        assertEquals(listOf("p2"), s.round!!.suggestedWinnerIds)
        assertEquals("Trail of 9s (7♠ as 9♠)", s.handName(s.round!!.hand("p2")!!))
    }

    @Test
    fun sameCardTwiceGivesNoVerdictAndNamesNoCard() {
        val s = sideShowAsked(listOf("AS", "KD", "9C"), listOf("AS", "2C", "3D")).act(AnswerSideShow("p2", true))
        assertTrue(s.round!!.suggestedWinnerIds.isEmpty())
        assertEquals("Two players entered the same card. Check the cards", s.round!!.suggestionNote)
    }

    @Test
    fun eachSidePotIsSuggestedAmongItsOwnPlayers() {
        var s = table().act(AdjustChips("p2", 12 - 250)).act(StartRound)
        s = s.act(Bet("p2")).act(Bet("p3")).act(Bet("p4")).act(Bet("p1")).act(AllIn("p2"))
        for (id in listOf("p2", "p3", "p4", "p1")) s = s.act(SeeCards(id))
        s = s.enter("p2", "AS", "AH", "AD").enter("p3", "KS", "KH", "2D")
            .enter("p4", "9C", "8C", "7C").enter("p1", "3S", "5H", "8D")
        s = s.act(Bet("p3")).act(Bet("p4")).act(Bet("p1")).act(ForceShow)
        assertEquals(listOf(listOf("p1", "p2", "p3", "p4"), listOf("p1", "p3", "p4")), s.round!!.pots.map { it.eligibleIds })
        assertEquals(listOf("p2"), s.round!!.suggestedWinnerIds, "Ravi's trail wins the main pot")
        s = s.act(DeclareWinners(listOf("p2")))
        assertEquals(listOf("p4"), s.round!!.suggestedWinnerIds, "without Ravi, Kiran's sequence wins the side pot")
    }

    @Test
    fun settingsCheckTheJokers() {
        val table = table()
        assertEquals("Pick the joker rank", table.rejects(UpdateSettings(TableSettings(variant = Variant.JOKER))))
        assertEquals(
            "Joker ranks run from 2 to A",
            table.rejects(UpdateSettings(TableSettings(variant = Variant.JOKER, jokerRanks = setOf(15)))),
        )
        val joker = table.act(UpdateSettings(TableSettings(variant = Variant.JOKER, jokerRanks = setOf(14, 7))))
        assertEquals("Table settings changed: Boot 5 · chaal limit 80 · no pot limit · Joker (7, A)", joker.log.last().text)
        assertEquals("Boot 5 · chaal limit 80 · no pot limit · Muflis", TableSettings(variant = Variant.MUFLIS).summary())
    }
}
