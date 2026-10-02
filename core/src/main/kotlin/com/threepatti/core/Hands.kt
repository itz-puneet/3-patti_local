package com.threepatti.core

import kotlinx.serialization.Serializable

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

/** 3 Patti variants that change who wins a show. */
@Serializable
enum class Variant(val label: String, val description: String) {
    CLASSIC("Classic", "Normal 3 Patti rankings"),
    MUFLIS("Muflis", "Rankings are reversed: the lowest hand wins"),
    AK47("AK47", "Aces, kings, 4s and 7s are jokers"),
    JOKER("Joker", "Every card of the joker rank is a joker"),
}

/** How hands are ranked in one deal. Jokers ([wildRanks]) stand for whatever card helps their hand most. */
data class HandRules(val lowestWins: Boolean = false, val wildRanks: Set<Int> = emptySet()) {
    companion object {
        fun of(variant: Variant, jokerRanks: Set<Int> = emptySet()): HandRules = when (variant) {
            Variant.CLASSIC -> HandRules()
            Variant.MUFLIS -> HandRules(lowestWins = true)
            Variant.AK47 -> HandRules(wildRanks = setOf(14, 13, 4, 7))
            Variant.JOKER -> HandRules(wildRanks = jokerRanks)
        }
    }
}

/** A ranked 3 card hand. Hands compare by type, then by their cards from the most important down. */
class TeenPattiHand internal constructor(
    val cards: List<Card>,
    val type: HandType,
    private val strength: List<Int>,
    /** Such as "Pure sequence A-K-Q", "Pair of 7s + K" or "Trail of 9s (K♠ as 9♥)". */
    val name: String,
    /** Jokers in the hand and the card each one stands for, when that's a different card. */
    val jokers: List<Pair<Card, Card>> = emptyList(),
) : Comparable<TeenPattiHand> {
    override fun compareTo(other: TeenPattiHand): Int {
        if (type != other.type) return type.compareTo(other.type)
        strength.zip(other.strength).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        return 0
    }

    /** This hand made with jokers: [cards] as entered, each joker standing for its pair in [swaps]. */
    internal fun withJokers(cards: List<Card>, swaps: List<Pair<Card, Card>>) = TeenPattiHand(
        cards,
        type,
        strength,
        "$name (${swaps.joinToString { (joker, card) -> "${joker.label} as ${card.label}" }})",
        swaps,
    )

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

    private val deck = Suit.entries.flatMap { suit -> (2..14).map { Card(it, suit) } }

    /**
     * The best hand [cards] make under [rules], or the lowest when the lowest wins. Each joker is tried
     * as every card not already in the hand.
     */
    fun best(cards: List<Card>, rules: HandRules = HandRules()): TeenPattiHand {
        if (cards.size != 3) fail("A hand has 3 cards")
        val wild = cards.indices.filter { cards[it].rank in rules.wildRanks }
        if (wild.isEmpty()) return evaluate(cards)
        val chosen = when {
            // Three jokers can be anything: a trail of aces, keeping any aces already there...
            wild.size == 3 && !rules.lowestWins -> {
                val spare = Suit.entries.map { Card(14, it) }.filter { it !in cards }.iterator()
                cards.map { if (it.rank == 14) it else spare.next() }
            }
            // ...or for the lowest hand 5-3-2 in mixed suits.
            wild.size == 3 -> listOf("5S", "3H", "2D").map { Card.parse(it)!! }
            else -> {
                val others = deck.filter { it !in cards }
                var bestCards = cards
                var bestHand: TeenPattiHand? = null
                fun visit(index: Int, current: MutableList<Card>) {
                    if (index == wild.size) {
                        val hand = evaluate(current)
                        val better = bestHand?.let { if (rules.lowestWins) hand < it else hand > it } ?: true
                        if (better) {
                            bestHand = hand
                            bestCards = current.toList()
                        }
                        return
                    }
                    // A joker first stays itself, so it only changes when that makes a better hand.
                    for (card in listOf(cards[wild[index]]) + others) {
                        // Two jokers never stand for the same card.
                        if (wild.take(index).any { current[it] == card }) continue
                        current[wild[index]] = card
                        visit(index + 1, current)
                    }
                }
                visit(0, cards.toMutableList())
                bestCards
            }
        }
        val swaps = wild.map { cards[it] to chosen[it] }.filter { (joker, card) -> joker != card }
        return if (swaps.isEmpty()) evaluate(cards) else evaluate(chosen).withJokers(cards, swaps)
    }

    /** How strong a sequence [ranks] (highest first) is, or null when they aren't one. */
    private fun runStrength(ranks: List<Int>): Int? = when {
        ranks == listOf(14, 13, 12) -> 15
        ranks == listOf(14, 3, 2) -> 14
        ranks[0] - 1 == ranks[1] && ranks[1] - 1 == ranks[2] -> ranks[0]
        else -> null
    }

    /**
     * Decides a show from everyone's cards under [rules]. Equal best hands split the pot, except in a side show,
     * where the player who asked for it ([sideShowAsker]) packs.
     */
    fun <K> decide(cards: Map<K, List<Card>>, sideShowAsker: K? = null, rules: HandRules = HandRules()): Verdict<K> {
        if (cards.size < 2) fail("Enter at least 2 hands")
        val seen = mutableSetOf<Card>()
        cards.values.flatten().forEach { if (!seen.add(it)) fail("${it.label} is entered twice") }
        val hands = cards.mapValues { best(it.value, rules) }
        val best = if (rules.lowestWins) hands.values.min() else hands.values.max()
        val top = hands.filterValues { it.compareTo(best) == 0 }.keys.toList()
        return when {
            top.size == 1 -> Verdict(hands, top, null)
            sideShowAsker != null && sideShowAsker in top ->
                Verdict(hands, top - sideShowAsker, "Equal hands: the player who asked for the side show packs")
            else -> Verdict(hands, top, "Equal hands split the pot")
        }
    }
}
