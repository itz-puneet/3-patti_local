package com.threepatti.core

// 3 Patti hand rankings, used to decide a show or side show when players aren't sure who won.

enum class Suit(val symbol: String, val letter: Char, val red: Boolean) {
    SPADES("♠", 'S', false),
    HEARTS("♥", 'H', true),
    DIAMONDS("♦", 'D', true),
    CLUBS("♣", 'C', false),
}

/** A playing card. [rank] runs from 2 to 14, where 11 to 14 are J, Q, K and A. */
data class Card(val rank: Int, val suit: Suit) {
    init {
        require(rank in 2..14) { "No card has rank $rank" }
    }

    /** Short code such as "AS" or "10H", used by the browser page. */
    val code: String get() = rankLabel(rank) + suit.letter

    /** Such as "A♠" or "10♥". */
    val label: String get() = rankLabel(rank) + suit.symbol

    companion object {
        fun parse(code: String): Card? {
            val text = code.trim().uppercase()
            val suit = Suit.entries.firstOrNull { it.letter == text.lastOrNull() } ?: return null
            val rank = when (val r = text.dropLast(1)) {
                "A" -> 14
                "K" -> 13
                "Q" -> 12
                "J" -> 11
                "T" -> 10
                else -> r.toIntOrNull()
            }
            return if (rank != null && rank in 2..14) Card(rank, suit) else null
        }
    }
}

/** "A", "K", "Q", "J" or the number. */
fun rankLabel(rank: Int): String = when (rank) {
    14 -> "A"
    13 -> "K"
    12 -> "Q"
    11 -> "J"
    else -> rank.toString()
}

private fun rankPlural(rank: Int): String = when (rank) {
    14 -> "Aces"
    13 -> "Kings"
    12 -> "Queens"
    11 -> "Jacks"
    else -> "${rank}s"
}

/** 3 Patti hands, weakest first. */
enum class HandType(val label: String) {
    HIGH_CARD("High card"),
    PAIR("Pair"),
    COLOR("Color"),
    SEQUENCE("Sequence"),
    PURE_SEQUENCE("Pure sequence"),
    TRAIL("Trail"),
}

/** A ranked 3 card hand. Hands compare by type, then by their cards from the most important down. */
class TeenPattiHand internal constructor(
    val cards: List<Card>,
    val type: HandType,
    private val strength: List<Int>,
    /** Such as "Pure sequence A-K-Q" or "Pair of 7s + K". */
    val name: String,
) : Comparable<TeenPattiHand> {
    override fun compareTo(other: TeenPattiHand): Int {
        if (type != other.type) return type.compareTo(other.type)
        strength.zip(other.strength).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        return 0
    }

    override fun toString() = name
}

/** Who wins a show or side show, worked out from the players' cards. */
data class Verdict<K>(
    val hands: Map<K, TeenPattiHand>,
    /** More than one only when equal hands split the pot. */
    val winners: List<K>,
    /** Explains a tie, or null. */
    val note: String?,
)

object TeenPatti {
    /**
     * Ranks 3 cards. Trail beats pure sequence, then sequence, color, pair and high card. A-K-Q is the
     * highest sequence and A-2-3 the second highest; after them sequences go by their top card.
     */
    fun evaluate(cards: List<Card>): TeenPattiHand {
        if (cards.size != 3) fail("A hand has 3 cards")
        val ranks = cards.map { it.rank }.sortedDescending()
        val sameSuit = cards.map { it.suit }.distinct().size == 1
        val run = runStrength(ranks)
        val runText = if (ranks == listOf(14, 3, 2)) "A-2-3" else ranks.joinToString("-") { rankLabel(it) }
        val all = ranks.joinToString("-") { rankLabel(it) }
        return when {
            ranks[0] == ranks[2] -> TeenPattiHand(cards, HandType.TRAIL, listOf(ranks[0]), "Trail of ${rankPlural(ranks[0])}")
            run != null && sameSuit -> TeenPattiHand(cards, HandType.PURE_SEQUENCE, listOf(run), "Pure sequence $runText")
            run != null -> TeenPattiHand(cards, HandType.SEQUENCE, listOf(run), "Sequence $runText")
            sameSuit -> TeenPattiHand(cards, HandType.COLOR, ranks, "Color $all")
            ranks[0] == ranks[1] || ranks[1] == ranks[2] -> {
                val pair = ranks[1]
                val third = if (ranks[0] == pair) ranks[2] else ranks[0]
                TeenPattiHand(cards, HandType.PAIR, listOf(pair, third), "Pair of ${rankPlural(pair)} + ${rankLabel(third)}")
            }
            else -> TeenPattiHand(cards, HandType.HIGH_CARD, ranks, "High card $all")
        }
    }

    /** How strong a sequence [ranks] (highest first) is, or null when they aren't one. */
    private fun runStrength(ranks: List<Int>): Int? = when {
        ranks == listOf(14, 13, 12) -> 15
        ranks == listOf(14, 3, 2) -> 14
        ranks[0] - 1 == ranks[1] && ranks[1] - 1 == ranks[2] -> ranks[0]
        else -> null
    }

    /**
     * Decides a show from everyone's cards. Equal best hands split the pot, except in a side show,
     * where the player who asked for it ([sideShowAsker]) packs.
     */
    fun <K> decide(cards: Map<K, List<Card>>, sideShowAsker: K? = null): Verdict<K> {
        if (cards.size < 2) fail("Enter at least 2 hands")
        val seen = mutableSetOf<Card>()
        cards.values.flatten().forEach { if (!seen.add(it)) fail("${it.label} is entered twice") }
        val hands = cards.mapValues { evaluate(it.value) }
        val best = hands.values.max()
        val top = hands.filterValues { it.compareTo(best) == 0 }.keys.toList()
        return when {
            top.size == 1 -> Verdict(hands, top, null)
            sideShowAsker != null && sideShowAsker in top ->
                Verdict(hands, top - sideShowAsker, "Equal hands: the player who asked for the side show packs")
            else -> Verdict(hands, top, "Equal hands split the pot")
        }
    }
}
