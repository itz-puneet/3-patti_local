package com.threepatti.core

import kotlinx.serialization.Serializable

/** What one poker seat can do right now. Used by the app and the browser page to show the right buttons. */
@Serializable
data class PokerOptions(
    val playerId: String,
    val inHand: Boolean,
    val folded: Boolean,
    val allIn: Boolean,
    val isTurn: Boolean,
    /** Chips behind (not yet in the pot). */
    val stack: Int,
    /** Chips put in during this betting round. */
    val streetBet: Int,
    /** What calling costs, already capped at the stack. */
    val toCall: Int,
    val canCheck: Boolean,
    val canCall: Boolean,
    /** Calling puts in every remaining chip. */
    val callIsAllIn: Boolean,
    val canRaise: Boolean,
    /** No one has bet yet in this betting round, so the move is a bet rather than a raise. */
    val isBet: Boolean,
    /** Smallest total this player may bet or raise to (equal to [maxRaiseTo] when only all-in is left). */
    val minRaiseTo: Int,
    /** Largest total this player may bet or raise to: all-in, or the pot size in pot limit. */
    val maxRaiseTo: Int,
    val halfPotRaiseTo: Int,
    val potRaiseTo: Int,
) {
    companion object {
        fun notPlaying(playerId: String, stack: Int = 0) = PokerOptions(
            playerId = playerId, inHand = false, folded = false, allIn = false, isTurn = false, stack = stack,
            streetBet = 0, toCall = 0, canCheck = false, canCall = false, callIsAllIn = false, canRaise = false,
            isBet = false, minRaiseTo = 0, maxRaiseTo = 0, halfPotRaiseTo = 0, potRaiseTo = 0,
        )
    }
}

object PokerRules {
    fun streetName(street: Street): String = when (street) {
        Street.PREFLOP -> "Pre-flop"
        Street.FLOP -> "Flop"
        Street.TURN -> "Turn"
        Street.RIVER -> "River"
    }

    /** Next seat after [fromId] that is still in the hand and has chips to bet with. */
    fun nextToAct(round: Round, fromId: String?): Hand? {
        val hands = round.hands
        val start = hands.indexOfFirst { it.playerId == fromId }
        for (i in 1..hands.size) {
            val hand = hands[(start + i).mod(hands.size)]
            if (hand.status != HandStatus.PACKED && !hand.allIn && hand.playerId != fromId) return hand
        }
        return null
    }

    /** True once everyone who can still bet has acted and matched the current bet. */
    fun bettingRoundComplete(round: Round): Boolean {
        val canAct = round.hands.filter { it.status != HandStatus.PACKED && !it.allIn }
        return when (canAct.size) {
            0 -> true
            // Everyone else is all-in: nothing left to bet against once this player has matched.
            1 -> canAct.single().streetBet >= round.currentBet
            else -> canAct.all { it.acted && it.streetBet == round.currentBet }
        }
    }

    /**
     * Splits the chips into a main pot and side pots. Each pot can be won only by players who are still in
     * the hand and put in at least that much. A pot only one player can win is chips nobody called.
     */
    fun pots(round: Round): List<Pot> {
        val remaining = round.hands.associate { it.playerId to it.invested }.toMutableMap()
        val live = round.hands.filter { it.status != HandStatus.PACKED }.map { it.playerId }.toSet()
        val pots = mutableListOf<Pot>()
        while (remaining.values.any { it > 0 }) {
            val liveWithChips = remaining.filter { (id, left) -> id in live && left > 0 }
            if (liveWithChips.isEmpty()) {
                // Only folded players' chips are left: they belong to the last pot.
                val extra = remaining.values.sum()
                if (pots.isEmpty()) {
                    pots += Pot(extra, live.toList())
                } else {
                    pots[pots.lastIndex] = pots.last().copy(amount = pots.last().amount + extra)
                }
                break
            }
            val level = liveWithChips.values.min()
            var amount = 0
            for ((id, left) in remaining.toMap()) {
                val take = minOf(left, level)
                amount += take
                remaining[id] = left - take
            }
            val eligible = round.hands.map { it.playerId }.filter { it in liveWithChips }
            pots += Pot(amount, eligible)
        }
        return pots
    }

    fun potLabel(round: Round, index: Int): String = when {
        round.pots.size <= 1 -> "Pot"
        index == 0 -> "Main pot"
        else -> "Side pot $index"
    }

    fun options(state: GameState, playerId: String): PokerOptions {
        val player = state.player(playerId)
        val round = state.round?.takeIf { it.isActive }
        val hand = round?.hand(playerId)
        if (player == null || round == null || hand == null) {
            return PokerOptions.notPlaying(playerId, player?.balance ?: 0)
        }
        val folded = hand.status == HandStatus.PACKED
        val isTurn = round.phase == RoundPhase.BETTING && round.turnId == playerId && !folded && !hand.allIn
        val owed = (round.currentBet - hand.streetBet).coerceAtLeast(0)
        val toCall = minOf(owed, player.balance)
        val allInTo = hand.streetBet + player.balance
        val maxTo = maxRaiseTo(state.settings, round, hand, player.balance)
        val fullMin = if (round.currentBet == 0) state.settings.bigBlind else round.currentBet + round.minRaise
        val canRaise = isTurn && !hand.raiseClosed && allInTo > round.currentBet && maxTo > round.currentBet
        val minTo = minOf(fullMin, maxTo)
        val potTo = maxOf(minTo, minOf(maxTo, potRaiseTo(round, hand)))
        val halfTo = maxOf(minTo, minOf(maxTo, halfPotRaiseTo(round, hand)))
        return PokerOptions(
            playerId = playerId,
            inHand = true,
            folded = folded,
            allIn = hand.allIn,
            isTurn = isTurn,
            stack = player.balance,
            streetBet = hand.streetBet,
            toCall = toCall,
            canCheck = isTurn && owed == 0,
            canCall = isTurn && owed > 0,
            callIsAllIn = owed > 0 && owed >= player.balance,
            canRaise = canRaise,
            isBet = round.currentBet == 0,
            minRaiseTo = if (canRaise) minTo else 0,
            maxRaiseTo = if (canRaise) maxTo else 0,
            halfPotRaiseTo = if (canRaise) halfTo else 0,
            potRaiseTo = if (canRaise) potTo else 0,
        )
    }

    fun raiseError(state: GameState, playerId: String, raiseTo: Int): String? {
        turnError(state, playerId)?.let { return it }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val balance = state.player(playerId)!!.balance
        val allInTo = hand.streetBet + balance
        if (hand.raiseClosed) return "You can only call or fold after a short all-in"
        if (raiseTo <= round.currentBet) {
            return if (round.currentBet == 0) "Bet at least ${state.money(state.settings.bigBlind)}" else "A raise must be more than ${state.money(round.currentBet)}"
        }
        if (raiseTo > allInTo) return "${state.nameOf(playerId)} has only ${state.money(balance)} left"
        val maxTo = maxRaiseTo(state.settings, round, hand, balance)
        if (raiseTo > maxTo) return "Pot limit: the most you can make it is ${state.money(maxTo)}"
        val minTo = if (round.currentBet == 0) state.settings.bigBlind else round.currentBet + round.minRaise
        if (raiseTo < minTo && raiseTo != allInTo) {
            return if (round.currentBet == 0) "Bet at least ${state.money(minTo)}" else "Raise to at least ${state.money(minTo)}"
        }
        return null
    }

    fun turnError(state: GameState, playerId: String): String? {
        val round = state.round?.takeIf { it.isActive } ?: return "No hand is being played"
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: return "$name is not in this hand"
        if (hand.status == HandStatus.PACKED) return "$name has folded"
        if (hand.allIn) return "$name is all-in"
        if (round.phase != RoundPhase.BETTING) return "Betting is over for this hand"
        if (round.turnId != playerId) return "It's ${state.nameOf(round.turnId)}'s turn"
        return null
    }

    /** Largest total bet for this player: all their chips in no limit, the size of the pot in pot limit. */
    fun maxRaiseTo(settings: TableSettings, round: Round, hand: Hand, balance: Int): Int {
        val allInTo = hand.streetBet + balance
        if (settings.betLimit == BetLimit.NO_LIMIT) return allInTo
        return minOf(allInTo, potRaiseTo(round, hand))
    }

    /** Pot-sized raise: call first, then raise by the whole pot including that call. */
    private fun potRaiseTo(round: Round, hand: Hand): Int {
        val owed = (round.currentBet - hand.streetBet).coerceAtLeast(0)
        return round.currentBet + round.pot + owed
    }

    private fun halfPotRaiseTo(round: Round, hand: Hand): Int {
        val owed = (round.currentBet - hand.streetBet).coerceAtLeast(0)
        return round.currentBet + (round.pot + owed) / 2
    }
}
