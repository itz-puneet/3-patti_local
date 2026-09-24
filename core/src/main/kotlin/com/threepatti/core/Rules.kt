package com.threepatti.core

/** What one seat can do right now. Used by the UI to decide which buttons to show and enable. */
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
        )
    }

    private fun chipsError(state: GameState, playerId: String, amount: Int): String? {
        val player = state.player(playerId) ?: return "Player not found"
        return if (player.balance < amount) {
            "${player.name} needs ${state.money(amount)} but has only ${state.money(player.balance)}"
        } else {
            null
        }
    }
}

fun TableSettings.summary(): String {
    fun m(amount: Int) = formatMoney(amount, currency)
    return listOfNotNull(
        "Boot ${m(bootAmount)}",
        if (maxSeenBet > 0) "chaal limit ${m(maxSeenBet)}" else "no chaal limit",
        if (potLimit > 0) "pot limit ${m(potLimit)}" else "no pot limit",
        if (maxBlindTurns > 0) "max $maxBlindTurns blind turns" else null,
    ).joinToString(" · ")
}
