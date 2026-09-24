package com.threepatti.core

/**
 * Poker betting: blinds, four betting rounds, all-ins and side pots. Cards are dealt for real at the table,
 * so the app never knows them; at showdown the host enters who won each pot.
 */
internal object PokerEngine {
    fun startHand(state: GameState): GameState {
        if (state.isRoundActive) fail("Finish the current hand first")
        val settings = state.settings
        val eligible = state.players.filter { !it.sittingOut && it.balance > 0 }
        if (eligible.size < 2) fail("Need at least 2 players with chips to deal a hand")
        val eligibleIds = eligible.map { it.id }.toSet()
        val dealerId = nextDealer(state.players, state.lastDealerId, eligibleIds)
        val number = state.results.size + 1
        val draft = Round(
            number = number,
            dealerId = dealerId,
            previousDealerId = state.lastDealerId,
            hands = eligible.map { Hand(playerId = it.id, status = HandStatus.ACTIVE) },
            pot = 0,
            stake = settings.bigBlind,
            turnId = null,
            phase = RoundPhase.BETTING,
            street = Street.PREFLOP,
            currentBet = settings.bigBlind,
            minRaise = settings.bigBlind,
        )
        // Heads-up the dealer posts the small blind. Otherwise the two seats after the dealer post the blinds.
        val smallBlindId = if (eligible.size == 2) dealerId else Rules.nextActive(draft, dealerId)!!.playerId
        val bigBlindId = Rules.nextActive(draft, smallBlindId)!!.playerId
        var next = state.copy(
            round = draft.copy(smallBlindId = smallBlindId, bigBlindId = bigBlindId),
            lastDealerId = dealerId,
        ).withLog("Hand $number. ${state.nameOf(dealerId)} deals")
        next = postBlind(next, smallBlindId, settings.smallBlind, "small blind")
        next = postBlind(next, bigBlindId, settings.bigBlind, "big blind")
        val short = state.players.filter { !it.sittingOut && it.balance == 0 }
        if (short.isNotEmpty()) next = next.withLog("${short.joinToString { it.name }} has no chips and sits out this hand")
        // The player after the big blind acts first (heads-up that is the dealer).
        return continueBetting(next, bigBlindId)
    }

    private fun postBlind(state: GameState, playerId: String, blind: Int, label: String): GameState {
        val amount = minOf(blind, state.player(playerId)!!.balance)
        var next = state.pay(playerId, amount).updateHand(playerId) { it.copy(streetBet = it.streetBet + amount) }
        val allIn = next.player(playerId)!!.balance == 0
        if (allIn) next = next.updateHand(playerId) { it.copy(allIn = true) }
        return next.withLog("${state.nameOf(playerId)} posts the $label ${state.money(amount)}${if (allIn) " and is all-in" else ""}")
    }

    fun check(state: GameState, playerId: String): GameState {
        PokerRules.turnError(state, playerId)?.let { fail(it) }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        if (hand.streetBet < round.currentBet) {
            fail("You need to call ${state.money(round.currentBet - hand.streetBet)} or fold")
        }
        val next = state.updateHand(playerId) { it.copy(acted = true) }.withLog("${state.nameOf(playerId)} checks")
        return continueBetting(next, playerId)
    }

    fun call(state: GameState, playerId: String): GameState {
        PokerRules.turnError(state, playerId)?.let { fail(it) }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val owed = round.currentBet - hand.streetBet
        if (owed <= 0) fail("Nothing to call. You can check")
        val amount = minOf(owed, state.player(playerId)!!.balance)
        var next = state.pay(playerId, amount).updateHand(playerId) { it.copy(streetBet = it.streetBet + amount, acted = true) }
        val allIn = next.player(playerId)!!.balance == 0
        if (allIn) next = next.updateHand(playerId) { it.copy(allIn = true) }
        next = next.withLog("${state.nameOf(playerId)} calls ${state.money(amount)}${if (allIn) " and is all-in" else ""}")
        return continueBetting(next, playerId)
    }

    fun raiseTo(state: GameState, playerId: String, raiseTo: Int): GameState {
        PokerRules.raiseError(state, playerId, raiseTo)?.let { fail(it) }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val amount = raiseTo - hand.streetBet
        val increment = raiseTo - round.currentBet
        val isBet = round.currentBet == 0
        // An opening bet always lets everyone act again. A raise must be at least the last raise to do so.
        val fullRaise = isBet || increment >= round.minRaise
        var next = state.pay(playerId, amount)
        val allIn = next.player(playerId)!!.balance == 0
        next = next.updateRound { r ->
            r.copy(
                currentBet = raiseTo,
                minRaise = if (fullRaise) maxOf(increment, state.settings.bigBlind) else r.minRaise,
                hands = r.hands.map { h ->
                    when {
                        h.playerId == playerId ->
                            h.copy(streetBet = raiseTo, acted = true, raiseClosed = false, allIn = allIn)
                        h.status == HandStatus.PACKED || h.allIn -> h
                        // A full raise lets everyone act again, including raising.
                        fullRaise -> h.copy(acted = false, raiseClosed = false)
                        // A short all-in: those who already acted must respond but may not raise again.
                        h.acted -> h.copy(acted = false, raiseClosed = true)
                        else -> h
                    }
                },
            )
        }
        val name = state.nameOf(playerId)
        val text = when {
            allIn -> "$name is all-in for ${state.money(raiseTo)}"
            isBet -> "$name bets ${state.money(raiseTo)}"
            else -> "$name raises to ${state.money(raiseTo)}"
        }
        return continueBetting(next.withLog(text), playerId)
    }

    fun fold(state: GameState, playerId: String): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No hand is being played")
        if (round.phase != RoundPhase.BETTING) fail("Betting is over for this hand")
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: fail("$name is not in this hand")
        if (hand.status == HandStatus.PACKED) fail("$name has already folded")
        if (hand.allIn) fail("$name is all-in and can't fold")
        val next = state.updateHand(playerId) { it.copy(status = HandStatus.PACKED) }.withLog("$name folds")
        return continueBetting(next, playerId)
    }

    /** After a move: award the pot if everyone else folded, end the betting round, or pass the turn. */
    private fun continueBetting(state: GameState, fromId: String?): GameState {
        val round = state.round!!
        val live = round.activeHands
        if (live.size == 1) return winByFold(state, live.single().playerId)
        if (PokerRules.bettingRoundComplete(round)) return nextStreet(state)
        val next = if (round.turnId != null && round.turnId != fromId && round.hand(round.turnId)?.let { canAct(it) } == true) {
            // Someone folded out of turn: the player whose turn it was still has to act.
            round.turnId
        } else {
            PokerRules.nextToAct(round, fromId)?.playerId
        }
        return state.updateRound { it.copy(turnId = next) }
    }

    private fun canAct(hand: Hand) = hand.status != HandStatus.PACKED && !hand.allIn

    private fun nextStreet(state: GameState): GameState {
        val round = state.round!!
        val canActCount = round.hands.count { canAct(it) }
        if (round.street == Street.RIVER) return showdown(state, "Showdown")
        if (canActCount < 2) {
            return showdown(state, "No more betting. Deal the rest of the board and show")
        }
        val street = Street.entries[round.street.ordinal + 1]
        val reset = state.updateRound { r ->
            r.copy(
                street = street,
                currentBet = 0,
                minRaise = state.settings.bigBlind,
                hands = r.hands.map { it.copy(streetBet = 0, acted = false, raiseClosed = false) },
            )
        }
        val first = PokerRules.nextToAct(reset.round!!, round.dealerId)?.playerId
        return reset.updateRound { it.copy(turnId = first) }
            .withLog("${PokerRules.streetName(street)}. Pot ${state.money(round.pot)}")
    }

    fun showdown(state: GameState, message: String): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No hand is being played")
        if (round.phase == RoundPhase.SHOWDOWN) fail("The showdown is already waiting for its result")
        val next = state.updateRound {
            it.copy(
                phase = RoundPhase.SHOWDOWN,
                turnId = null,
                showdownIds = it.activeHands.map { h -> h.playerId },
                pots = PokerRules.pots(it),
                potWinners = emptyList(),
                hands = it.hands.map { h -> h.copy(streetBet = 0) },
            )
        }.withLog(message)
        return awardUncontested(next)
    }

    /** Host enters the winner (or winners, to split) of the next pot, main pot first. */
    fun declarePotWinners(state: GameState, winnerIds: List<String>): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No hand is being played")
        if (round.phase != RoundPhase.SHOWDOWN) fail("There is no showdown waiting for a result")
        val index = round.potWinners.size
        val pot = round.pots.getOrNull(index) ?: fail("Every pot already has a winner")
        if (winnerIds.isEmpty()) fail("Pick the winner")
        if (winnerIds.toSet().size != winnerIds.size) fail("A player was picked twice")
        winnerIds.firstOrNull { it !in pot.eligibleIds }?.let {
            fail("${state.nameOf(it)} can't win the ${PokerRules.potLabel(round, index).lowercase()}")
        }
        val ordered = round.hands.map { it.playerId }.filter { it in winnerIds }
        val label = PokerRules.potLabel(round, index)
        val text = if (ordered.size == 1) {
            "$label ${state.money(pot.amount)}: ${state.nameOf(ordered.single())} wins"
        } else {
            "$label ${state.money(pot.amount)}: split between ${state.namesOf(ordered)}"
        }
        val next = state.updateRound { it.copy(potWinners = it.potWinners + listOf(ordered)) }.withLog(text)
        return awardUncontested(next)
    }

    /** Pots that only one player can win (chips nobody called) go straight back. Finishes when all are decided. */
    private fun awardUncontested(state: GameState): GameState {
        var next = state
        while (true) {
            val round = next.round!!
            val pot = round.pots.getOrNull(round.potWinners.size) ?: return finishHand(next)
            val only = pot.eligibleIds.singleOrNull() ?: return next
            next = next.updateRound { it.copy(potWinners = it.potWinners + listOf(listOf(only))) }
                .withLog("${next.money(pot.amount)} nobody called goes back to ${next.nameOf(only)}")
        }
    }

    private fun winByFold(state: GameState, winnerId: String): GameState {
        val round = state.round!!
        val pots = listOf(Pot(round.pot, listOf(winnerId)))
        return finishHand(
            state.updateRound { it.copy(pots = pots, potWinners = listOf(listOf(winnerId))) }
                .withLog("Everyone else folded"),
        )
    }

    private fun finishHand(state: GameState): GameState {
        val round = state.round!!
        val order = round.hands.map { it.playerId }
        val winnings = mutableMapOf<String, Int>()
        round.pots.forEachIndexed { i, pot ->
            val winners = order.filter { it in round.potWinners[i] }
            val share = pot.amount / winners.size
            // Any odd chip of a split pot goes to the first winner after the dealer.
            winners.forEachIndexed { w, id -> winnings[id] = (winnings[id] ?: 0) + share + if (w == 0) pot.amount % winners.size else 0 }
        }
        // Chips that simply went back to their owner are not a win.
        val won = contestedWinnings(round)
        val winnerIds = order.filter { (won[it] ?: 0) > 0 }.ifEmpty { order.filter { it in winnings } }
        val names = winnerIds.map { state.nameOf(it) }
        val changes = round.hands.associate { it.playerId to ((winnings[it.playerId] ?: 0) - it.invested) }
        val contested = won.values.sum().takeIf { it > 0 } ?: round.pot
        val result = RoundResult(round.number, contested, winnerIds, names, changes)
        val finished = state.copy(
            players = state.players.map { p -> winnings[p.id]?.let { p.copy(balance = p.balance + it) } ?: p },
            round = round.copy(phase = RoundPhase.FINISHED, turnId = null, winnerIds = winnerIds, pot = contested),
            results = state.results + result,
        )
        return finished.withLog(winnerText(finished, finished.round!!))
    }

    /** What each winner took from pots that had more than one player in them (or from a pot won by folds). */
    fun contestedWinnings(round: Round): Map<String, Int> {
        val order = round.hands.map { it.playerId }
        val won = mutableMapOf<String, Int>()
        round.pots.forEachIndexed { i, pot ->
            val winners = order.filter { it in (round.potWinners.getOrNull(i) ?: emptyList()) }
            if (winners.isEmpty()) return@forEachIndexed
            val refund = pot.eligibleIds.size == 1 && round.pots.size > 1
            if (refund) return@forEachIndexed
            val share = pot.amount / winners.size
            winners.forEachIndexed { w, id -> won[id] = (won[id] ?: 0) + share + if (w == 0) pot.amount % winners.size else 0 }
        }
        return won
    }
}
