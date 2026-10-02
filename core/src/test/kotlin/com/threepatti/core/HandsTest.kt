package com.threepatti.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandsTest {
    private fun hand(vararg codes: String) = TeenPatti.evaluate(codes.map { Card.parse(it)!! })
    private fun cards(text: String) = text.split(" ").map { Card.parse(it)!! }

    private fun assertBeats(winner: TeenPattiHand, loser: TeenPattiHand) {
        assertTrue(winner > loser, "$winner should beat $loser")
        assertTrue(loser < winner, "$loser should lose to $winner")
    }

    @Test
    fun parsesCardCodes() {
        assertEquals(Card(14, Suit.SPADES), Card.parse("AS"))
        assertEquals(Card(10, Suit.HEARTS), Card.parse("10H"))
        assertEquals(Card(10, Suit.HEARTS), Card.parse("th"))
        assertEquals("10♥", Card.parse("10H")!!.label)
        assertEquals("QD", Card.parse("QD")!!.code)
        assertNull(Card.parse("1S"))
        assertNull(Card.parse("AX"))
        assertNull(Card.parse(""))
    }

    @Test
    fun namesEachKindOfHand() {
        assertEquals("Trail of Kings", hand("KS", "KH", "KD").name)
        assertEquals("Pure sequence A-K-Q", hand("QS", "AS", "KS").name)
        assertEquals("Pure sequence A-2-3", hand("2H", "AH", "3H").name)
        assertEquals("Sequence 10-9-8", hand("9S", "10H", "8D").name)
        assertEquals("Color K-9-4", hand("4C", "KC", "9C").name)
        assertEquals("Pair of 7s + K", hand("7S", "KD", "7H").name)
        assertEquals("High card A-J-5", hand("5S", "AD", "JH").name)
    }

    @Test
    fun handTypesRankTrailFirstAndHighCardLast() {
        val order = listOf(
            hand("2S", "2H", "2D"), // trail
            hand("AS", "KS", "QS"), // pure sequence
            hand("AS", "KH", "QD"), // sequence
            hand("AS", "KS", "JS"), // color
            hand("AS", "AH", "KD"), // pair
            hand("AS", "KH", "JD"), // high card
        )
        assertEquals(HandType.entries.reversed(), order.map { it.type })
        order.zipWithNext().forEach { (better, worse) -> assertBeats(better, worse) }
    }

    @Test
    fun sequencesRankAkqThenA23ThenByTopCard() {
        val akq = hand("AS", "KH", "QD")
        val a23 = hand("AS", "2H", "3D")
        val kqj = hand("KS", "QH", "JD")
        val low = hand("4S", "3H", "2D")
        assertBeats(akq, a23)
        assertBeats(a23, kqj)
        assertBeats(kqj, low)
        assertBeats(hand("AH", "2H", "3H"), hand("KH", "QH", "JH"))
        assertEquals(HandType.HIGH_CARD, hand("KS", "AH", "2D").type, "K-A-2 doesn't wrap round")
    }

    @Test
    fun trailsRankAcesHighest() {
        assertBeats(hand("AS", "AH", "AD"), hand("KS", "KH", "KD"))
        assertBeats(hand("3S", "3H", "3D"), hand("2S", "2H", "2D"))
    }

    @Test
    fun colorsAndHighCardsCompareCardByCard() {
        assertBeats(hand("KC", "9C", "4C"), hand("KD", "9D", "3D"))
        assertBeats(hand("AC", "4C", "2C"), hand("KD", "QD", "9D"))
        assertBeats(hand("AS", "JH", "5D"), hand("AH", "JD", "4C"))
        assertEquals(0, hand("AS", "JH", "5D").compareTo(hand("AH", "JD", "5C")))
    }

    @Test
    fun pairsCompareByPairThenThirdCard() {
        assertBeats(hand("KS", "KH", "2D"), hand("QS", "QH", "AD"))
        assertBeats(hand("KS", "KH", "9D"), hand("KC", "KD", "5S"))
        assertEquals(0, hand("KS", "KH", "9D").compareTo(hand("KC", "KD", "9S")))
    }

    @Test
    fun everyPossibleHandIsCountedOnce() {
        val deck = Suit.entries.flatMap { suit -> (2..14).map { Card(it, suit) } }
        val counts = mutableMapOf<HandType, Int>()
        for (a in deck.indices) for (b in a + 1 until deck.size) for (c in b + 1 until deck.size) {
            val type = TeenPatti.evaluate(listOf(deck[a], deck[b], deck[c])).type
            counts[type] = (counts[type] ?: 0) + 1
        }
        assertEquals(
            mapOf(
                HandType.TRAIL to 52,
                HandType.PURE_SEQUENCE to 48,
                HandType.SEQUENCE to 720,
                HandType.COLOR to 1096,
                HandType.PAIR to 3744,
                HandType.HIGH_CARD to 16440,
            ),
            counts,
        )
    }

    @Test
    fun showGoesToTheBestHand() {
        val verdict = TeenPatti.decide(
            mapOf("asha" to cards("KS KH 2D"), "ravi" to cards("9C 8C 7C"), "meena" to cards("AS JH 5D")),
        )
        assertEquals(listOf("ravi"), verdict.winners)
        assertEquals("Pure sequence 9-8-7", verdict.hands.getValue("ravi").name)
        assertNull(verdict.note)
    }

    @Test
    fun equalHandsSplitAShow() {
        val verdict = TeenPatti.decide(mapOf("asha" to cards("AS JH 5D"), "ravi" to cards("AH JD 5C"), "meena" to cards("2S 3H 7D")))
        assertEquals(listOf("asha", "ravi"), verdict.winners)
        assertEquals("Equal hands split the pot", verdict.note)
    }

    @Test
    fun equalHandsInASideShowMakeTheAskerPack() {
        val tie = mapOf("asha" to cards("AS JH 5D"), "ravi" to cards("AH JD 5C"))
        val verdict = TeenPatti.decide(tie, sideShowAsker = "asha")
        assertEquals(listOf("ravi"), verdict.winners)
        assertEquals("Equal hands: the player who asked for the side show packs", verdict.note)
        assertEquals(listOf("asha"), TeenPatti.decide(mapOf("asha" to cards("AS AH 5D"), "ravi" to cards("AC JD 5C")), "asha").winners)
    }

    private val ak47 = HandRules.of(Variant.AK47)
    private fun best(text: String, rules: HandRules) = TeenPatti.best(cards(text), rules)

    @Test
    fun muflisGivesThePotToTheLowestHand() {
        val muflis = HandRules.of(Variant.MUFLIS)
        val verdict = TeenPatti.decide(
            mapOf("asha" to cards("AS AH AD"), "ravi" to cards("5C 3D 2H"), "meena" to cards("9S 8S 7S")),
            rules = muflis,
        )
        assertEquals(listOf("ravi"), verdict.winners)
        assertEquals("High card 5-3-2", verdict.hands.getValue("ravi").name)
        // 4-3-2 is a sequence, so it loses to 6-3-2 when the lowest wins.
        assertEquals(listOf(2), TeenPatti.decide(mapOf(1 to cards("4C 3D 2H"), 2 to cards("6C 3S 2D")), rules = muflis).winners)
    }

    @Test
    fun ak47CardsAreJokers() {
        assertEquals("Trail of 9s (A♠ as 9♠, K♦ as 9♥)", best("AS KD 9C", ak47).name)
        assertEquals("Trail of 9s (7♠ as 9♠)", best("7S 9D 9C", ak47).name)
        assertEquals("Pure sequence Q-J-10 (4♣ as Q♣)", best("JC 10C 4C", ak47).name)
        assertEquals(HandType.HIGH_CARD, best("QS 9H 2D", ak47).type, "no jokers, no change")
        assertEquals("Trail of Aces", best("AS AH AC", ak47).name, "jokers that stay themselves aren't listed")
        assertEquals("Trail of Aces (K♦ as A♥)", best("AS KD AC", ak47).name)
    }

    @Test
    fun jokerRankIsChosenForTheDeal() {
        val sevens = HandRules.of(Variant.JOKER, setOf(7))
        assertEquals("Pure sequence 10-9-8 (7♣ as 9♣)", best("10C 8C 7C", sevens).name)
        assertEquals(HandType.PAIR, best("KS KH 2D", sevens).type)
        val verdict = TeenPatti.decide(mapOf("asha" to cards("7S 9D 9H"), "ravi" to cards("QS QH QD")), rules = sevens)
        assertEquals(listOf("ravi"), verdict.winners, "a trail of queens beats a trail of 9s made with a joker")
        assertEquals("Trail of 9s (7♠ as 9♠)", verdict.hands.getValue("asha").name)
        val pairs = TeenPatti.decide(mapOf("asha" to cards("7S 2D 9H"), "ravi" to cards("QS QH 4D")), rules = sevens)
        assertEquals("Pair of 9s + 2 (7♠ as 9♠)", pairs.hands.getValue("asha").name)
        assertEquals(listOf("ravi"), pairs.winners)
    }

    @Test
    fun threeJokersMakeTheBestOrLowestHandAndNoCardRepeats() {
        assertEquals("Trail of Aces (K♠ as A♥, 4♦ as A♦)", best("AS KS 4D", ak47).name)
        assertEquals("Trail of Aces (K♠ as A♠, K♥ as A♥, K♦ as A♦)", best("KS KH KD", ak47).name)
        val lowest = TeenPatti.best(cards("KS KH KD"), HandRules(lowestWins = true, wildRanks = setOf(13)))
        assertEquals("High card 5-3-2 (K♠ as 5♠, K♥ as 3♥, K♦ as 2♦)", lowest.name)
        val two = best("7S 4H QD", ak47)
        assertEquals("Trail of Queens (7♠ as Q♠, 4♥ as Q♥)", two.name)
        assertEquals(3, (two.jokers.map { it.second } + Card.parse("QD")!!).toSet().size, "every card in the hand is different")
    }

    @Test
    fun lowestWinsWithJokersPicksTheWeakestHand() {
        val rules = HandRules(lowestWins = true, wildRanks = setOf(7))
        assertEquals("High card 5-3-2 (7♠ as 5♠)", TeenPatti.best(cards("7S 2H 3D"), rules).name)
        assertEquals(listOf(1), TeenPatti.decide(mapOf(1 to cards("7S 2H 3D"), 2 to cards("6C 4S 2D")), rules = rules).winners)
    }

    @Test
    fun rejectsCardsEnteredTwiceOrIncompleteHands() {
        assertEquals(
            "A♠ is entered twice",
            assertFailsWith<GameRuleException> { TeenPatti.decide(mapOf(1 to cards("AS KH 2D"), 2 to cards("AS 3H 4D"))) }.message,
        )
        assertEquals("A hand has 3 cards", assertFailsWith<GameRuleException> { TeenPatti.evaluate(cards("AS KH")) }.message)
        assertEquals("Enter at least 2 hands", assertFailsWith<GameRuleException> { TeenPatti.decide(mapOf(1 to cards("AS KH 2D"))) }.message)
    }
}
