package com.threepatti.core

// The cards 3 Patti players can enter on their own phones: who may see them, and who they say won.

/**
 * The table as [viewerId] may see it. Cards a player entered stay on their own device, except in a side
 * show, where the two players see each other's, and at a show, where they are on the table for everyone.
 * [enteredHere] are seats whose cards are typed in on the viewer's device anyway, such as players
 * without a phone at the host's.
 */
fun GameState.visibleTo(viewerId: String?, enteredHere: Set<String> = emptySet()): GameState {
    val round = round ?: return this
    val showing = Rules.showingCards(round)
    // A side show is between its two players; a show is for everyone.
    val shownHere = if (round.phase != RoundPhase.SIDE_SHOW_COMPARE || viewerId in showing) showing else emptyList()
    fun visible(hand: Hand) =
        hand.cards.isEmpty() || hand.playerId == viewerId || hand.playerId in enteredHere || hand.playerId in shownHere
    if (round.hands.all(::visible)) return this
    return copy(round = round.copy(hands = round.hands.map { if (visible(it)) it else it.copy(cards = emptyList()) }))
}

/** The rankings of this table's variant. */
val TableSettings.handRules: HandRules get() = HandRules.of(variant, jokerRanks)

/** The 3 cards a player entered, or null when they haven't (or they're hidden from this device). */
fun Hand.enteredCards(): List<Card>? = cards.mapNotNull { Card.parse(it) }.takeIf { it.size == 3 }

/** Such as "Pair of 7s + K", for a hand whose cards this device can see. */
fun GameState.handName(hand: Hand): String? = hand.enteredCards()?.let { TeenPatti.best(it, settings.handRules).name }

/**
 * Works out from the entered cards who wins what the host has to decide now: the side show, or the pot
 * being decided at a show. Only once everyone in it has entered their cards.
 */
internal fun GameState.withSuggestion(): GameState {
    val round = round ?: return this
    if (settings.isPoker) return this
    val asker = round.sideShow?.takeIf { round.phase == RoundPhase.SIDE_SHOW_COMPARE }?.requesterId
    val hands = Rules.comparedIds(round).mapNotNull { round.hand(it) }
    val cards = hands.associate { it.playerId to it.enteredCards() }
    val missing = cards.filterValues { it == null }.keys.toList()
    var winners = emptyList<String>()
    var note: String? = null
    var awaiting = emptyList<String>()
    when {
        // Nobody entered cards: the host decides as usual.
        hands.size < 2 || missing.size == hands.size -> Unit
        missing.isNotEmpty() -> awaiting = missing
        else -> try {
            val verdict = TeenPatti.decide(cards.mapValues { it.value!! }, asker, settings.handRules)
            winners = verdict.winners
            note = verdict.note
        } catch (e: GameRuleException) {
            // Naming the card would tell everyone what's in a side show hand.
            note = "Two players entered the same card. Check the cards"
        }
    }
    if (winners == round.suggestedWinnerIds && note == round.suggestionNote && awaiting == round.awaitingCardsFrom) return this
    return copy(round = round.copy(suggestedWinnerIds = winners, suggestionNote = note, awaitingCardsFrom = awaiting))
}
