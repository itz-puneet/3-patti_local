package com.threepatti.core

import kotlinx.serialization.Serializable

/** What one seat can do right now. Used by the UI to decide which buttons to show and enable. */
@Serializable
data class SeatOptions(
    val playerId: String,
    val inRound: Boolean,
    val isPacked: Boolean,
    val isTurn: Boolean,
    val isBlind: Boolean,
    val canSee: Boolean,
    val canPack: Boolean,
    /** "Blind" or "Chaal". */
    val callLabel: String,
    val callAmount: Int,
    /** Why the normal bet is not allowed, or null when it is. */
    val callError: String?,
    val raiseAmount: Int,
    val raiseError: String?,
    val showAvailable: Boolean,
    val showAmount: Int,
    val showError: String?,
    val sideShowAvailable: Boolean,
    val sideShowTargetId: String?,
    val sideShowAmount: Int,
    val sideShowError: String?,
    /** Set when this seat has to accept or refuse a side show from that player. */
    val answerSideShowFrom: String?,
    /** All the chips left aren't enough for the blind or chaal: the player can put them all in. */
    val canAllIn: Boolean = false,
    val allInAmount: Int = 0,
    val isAllIn: Boolean = false,
    /** The player may enter or change their own cards now. */
    val canEnterCards: Boolean = false,
) {
    companion object {
        fun notPlaying(playerId: String) = SeatOptions(
            playerId = playerId, inRound = false, isPacked = false, isTurn = false, isBlind = false,
            canSee = false, canPack = false, callLabel = "Chaal", callAmount = 0, callError = "Not in this round",
            raiseAmount = 0, raiseError = "Not in this round", showAvailable = false, showAmount = 0,
            showError = "Not in this round", sideShowAvailable = false, sideShowTargetId = null, sideShowAmount = 0,
            sideShowError = "Not in this round", answerSideShowFrom = null,
        )
    }
}

object Rules {
    /** A blind player pays the stake, a seen player pays twice the stake. */
    fun callAmount(round: Round, hand: Hand): Int =
        if (hand.status == HandStatus.BLIND) round.stake else round.stake * 2

    fun betAmount(round: Round, hand: Hand, raise: Boolean): Int =
        callAmount(round, hand) * if (raise) 2 else 1

    /** A raise doubles the stake, so the seen bet after it would be four times the current stake. */
    fun canRaise(settings: TableSettings, round: Round): Boolean =
        settings.maxSeenBet == 0 || round.stake * 4 <= settings.maxSeenBet

    fun potLimitReached(settings: TableSettings, round: Round): Boolean =
        settings.potLimit > 0 && round.pot >= settings.potLimit

    /**
     * The pots of a 3 Patti show after someone went all in, main pot first. Each all-in closes a pot at
     * the size it had then; later bets build the next pot, which that player can't win. Packed players
     * win nothing, and pots with the same players in them are one pot.
     */
    fun sidePots(round: Round): List<Pot> {
        val active = round.activeHands.map { it.playerId }
        val pots = mutableListOf<Pot>()
        val out = mutableSetOf<String>()
        var from = 0
        fun closeAt(upTo: Int) {
            val amount = upTo - from
            if (amount <= 0) return
            from = upTo
            val eligible = active.filter { it !in out }
            val last = pots.lastOrNull()
            pots += when {
                last == null -> Pot(amount, eligible.ifEmpty { active })
                // Chips that only all-in or packed players were in for go to the pot before.
                eligible.isEmpty() || eligible == last.eligibleIds -> pots.removeAt(pots.lastIndex).let {
                    it.copy(amount = it.amount + amount)
                }
                else -> Pot(amount, eligible)
            }
        }
        for (cut in round.allInBreaks) {
            closeAt(cut.potAt)
            out += cut.playerIds
        }
        closeAt(round.pot)
        return pots
    }

    /** Players whose cards are on show: the two in a side show (to each other), or everyone in a show. */
    fun showingCards(round: Round): List<String> = when (round.phase) {
        RoundPhase.SIDE_SHOW_COMPARE -> round.sideShow?.let { listOf(it.requesterId, it.targetId) }.orEmpty()
        RoundPhase.SHOWDOWN, RoundPhase.FINISHED -> round.showdownIds
        else -> emptyList()
    }

    /** The players whose hands decide what the host must enter now: the side show pair, or the pot being decided. */
    fun comparedIds(round: Round): List<String> = when (round.phase) {
        RoundPhase.SIDE_SHOW_COMPARE -> showingCards(round)
        RoundPhase.SHOWDOWN -> round.pots.getOrNull(round.potWinners.size)?.eligibleIds ?: round.showdownIds
        else -> emptyList()
    }

    /** Why [playerId] can't enter or change their own cards now, or null when they can. */
    fun enterCardsError(state: GameState, playerId: String): String? {
        val round = state.round?.takeIf { it.isActive } ?: return "No round is running"
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: return "$name is not in this round"
        if (hand.status == HandStatus.PACKED) return "$name has packed"
        val showingNow = playerId in showingCards(round)
        if (hand.status == HandStatus.BLIND && !showingNow) return "See your cards first"
        if (hand.cards.isNotEmpty() && (hand.cardsShown || showingNow)) return "$name's cards were shown, so they can't change"
        return null
    }

    /** Next player after [fromId] (in seat order) who can still bet: not packed and not all in. */
    fun nextToAct(round: Round, fromId: String?): Hand? {
        val hands = round.hands
        val start = hands.indexOfFirst { it.playerId == fromId }
        for (i in 1..hands.size) {
            val hand = hands[(start + i).mod(hands.size)]
            if (hand.status != HandStatus.PACKED && !hand.allIn && hand.playerId != fromId) return hand
        }
        return null
    }

    /** Next player after [fromId] (in seat order) who has not packed. */
    fun nextActive(round: Round, fromId: String?): Hand? {
        val hands = round.hands
        val start = hands.indexOfFirst { it.playerId == fromId }
        for (i in 1..hands.size) {
            val hand = hands[(start + i).mod(hands.size)]
            if (hand.status != HandStatus.PACKED && hand.playerId != fromId) return hand
        }
        return null
    }

    /** Previous player before [fromId] (in seat order) who has not packed. */
    fun previousActive(round: Round, fromId: String): Hand? {
        val hands = round.hands
        val start = hands.indexOfFirst { it.playerId == fromId }
        if (start < 0) return null
        for (i in 1..hands.size) {
            val hand = hands[(start - i).mod(hands.size)]
            if (hand.status != HandStatus.PACKED && hand.playerId != fromId) return hand
        }
        return null
    }

    fun turnError(state: GameState, playerId: String): String? {
        val round = state.round?.takeIf { it.isActive } ?: return "No round is running"
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: return "$name is not in this round"
        if (hand.status == HandStatus.PACKED) return "$name has packed"
        return when (round.phase) {
            RoundPhase.BETTING -> if (round.turnId == playerId) null else "It's ${state.nameOf(round.turnId)}'s turn"
            RoundPhase.SIDE_SHOW_REQUESTED -> "Waiting for the side show answer"
            RoundPhase.SIDE_SHOW_COMPARE -> "Waiting for the side show result"
            RoundPhase.SHOWDOWN -> "Waiting for the show result"
            RoundPhase.FINISHED -> "This round is over"
        }
    }

    fun betError(state: GameState, playerId: String, raise: Boolean): String? {
        turnError(state, playerId)?.let { return it }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val blindLimit = state.settings.maxBlindTurns
        if (hand.status == HandStatus.BLIND && blindLimit > 0 && hand.blindTurns >= blindLimit) {
            return "Blind limit reached. See your cards to continue"
        }
        if (raise && !canRaise(state.settings, round)) return "Chaal limit reached, no more raises"
        return chipsError(state, playerId, betAmount(round, hand, raise))
    }

    /** Going all in is only for a player whose chips don't cover the blind or chaal. */
    fun allInError(state: GameState, playerId: String): String? {
        turnError(state, playerId)?.let { return it }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val blindLimit = state.settings.maxBlindTurns
        if (hand.status == HandStatus.BLIND && blindLimit > 0 && hand.blindTurns >= blindLimit) {
            return "Blind limit reached. See your cards to continue"
        }
        val balance = state.player(playerId)?.balance ?: 0
        if (balance <= 0) return "${state.nameOf(playerId)} has no chips left"
        if (balance >= callAmount(round, hand)) {
            return "${state.nameOf(playerId)} has enough chips to play ${if (hand.status == HandStatus.BLIND) "blind" else "chaal"}"
        }
        return null
    }

    fun showError(state: GameState, playerId: String): String? {
        turnError(state, playerId)?.let { return it }
        val round = state.round!!
        if (round.activeHands.size != 2) return "Show is allowed only when 2 players are left"
        return chipsError(state, playerId, callAmount(round, round.hand(playerId)!!))
    }

    fun sideShowError(state: GameState, playerId: String): String? {
        turnError(state, playerId)?.let { return it }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        if (hand.status != HandStatus.SEEN) return "See your cards before asking for a side show"
        if (round.activeHands.size < 3) return "With 2 players left, ask for a show instead"
        val target = previousActive(round, playerId) ?: return "No one to ask for a side show"
        if (target.status != HandStatus.SEEN) return "${state.nameOf(target.playerId)} hasn't seen their cards yet"
        return chipsError(state, playerId, round.stake * 2)
    }

    fun seatOptions(state: GameState, playerId: String): SeatOptions {
        val round = state.round?.takeIf { it.isActive }
        val hand = round?.hand(playerId)
        if (round == null || hand == null) return SeatOptions.notPlaying(playerId)
        val isBlind = hand.status == HandStatus.BLIND
        val packed = hand.status == HandStatus.PACKED
        val betting = round.phase == RoundPhase.BETTING
        val isTurn = betting && round.turnId == playerId && !packed
        val activeCount = round.activeHands.size
        return SeatOptions(
            playerId = playerId,
            inRound = true,
            isPacked = packed,
            isTurn = isTurn,
            isBlind = isBlind,
            canSee = isBlind,
            canPack = betting && !packed,
            callLabel = if (isBlind) "Blind" else "Chaal",
            callAmount = callAmount(round, hand),
            callError = betError(state, playerId, raise = false),
            raiseAmount = betAmount(round, hand, raise = true),
            raiseError = betError(state, playerId, raise = true),
            showAvailable = isTurn && activeCount == 2,
            showAmount = callAmount(round, hand),
            showError = showError(state, playerId),
            sideShowAvailable = isTurn && !isBlind && activeCount >= 3,
            sideShowTargetId = if (packed) null else previousActive(round, playerId)?.playerId,
            sideShowAmount = round.stake * 2,
            sideShowError = sideShowError(state, playerId),
            answerSideShowFrom = round.sideShow
                ?.takeIf { round.phase == RoundPhase.SIDE_SHOW_REQUESTED && it.targetId == playerId }
                ?.requesterId,
            canAllIn = isTurn && allInError(state, playerId) == null,
            allInAmount = state.player(playerId)?.balance ?: 0,
            isAllIn = hand.allIn,
            canEnterCards = !state.settings.isPoker && enterCardsError(state, playerId) == null,
        )
    }

    private fun chipsError(state: GameState, playerId: String, amount: Int): String? {
        val player = state.player(playerId) ?: return "Player not found"
        return if (player.balance < amount) {
            "${player.name} needs ${state.chipCount(amount)} but has only ${state.chips(player.balance)}"
        } else {
            null
        }
    }
}

fun TableSettings.gameName(): String = when {
    !isPoker -> "3 Patti"
    betLimit == BetLimit.POT_LIMIT -> "pot-limit poker"
    else -> "no-limit poker"
}

/** "ante 1" or "big blind ante 1 each", or null without an ante. */
fun TableSettings.anteText(): String? = when {
    !isPoker || ante <= 0 -> null
    anteStyle == AnteStyle.BIG_BLIND -> "big blind ante ${formatChips(ante)} each"
    else -> "ante ${formatChips(ante)}"
}

fun TableSettings.summary(): String {
    fun m(amount: Int) = formatChips(amount)
    if (isPoker) {
        return listOfNotNull(
            "Blinds ${m(smallBlind)}/${m(bigBlind)}",
            anteText(),
            if (betLimit == BetLimit.POT_LIMIT) "pot limit" else "no limit",
        ).joinToString(" · ")
    }
    return listOfNotNull(
        "Boot ${m(bootAmount)}",
        if (maxSeenBet > 0) "chaal limit ${m(maxSeenBet)}" else "no chaal limit",
        if (potLimit > 0) "pot limit ${m(potLimit)}" else "no pot limit",
        if (maxBlindTurns > 0) "max $maxBlindTurns blind turns" else null,
        variantText(),
    ).joinToString(" · ")
}

/** "Muflis" or "Joker (7, A)", or null for classic 3 Patti and for poker. */
fun TableSettings.variantText(): String? = when {
    isPoker || variant == Variant.CLASSIC -> null
    variant == Variant.JOKER -> "Joker (${jokerRanks.sorted().joinToString { rankLabel(it) }})"
    else -> variant.label
}
