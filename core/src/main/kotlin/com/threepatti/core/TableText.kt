package com.threepatti.core

// Short texts about the table, shared by the app screens and the browser page.

fun GameState.namesOf(ids: List<String>): String = when (ids.size) {
    0 -> "nobody"
    1 -> nameOf(ids[0])
    else -> ids.dropLast(1).joinToString { nameOf(it) } + " and " + nameOf(ids.last())
}

/** One line saying what the table is waiting for. */
fun describeRound(state: GameState, round: Round): String {
    val sideShow = round.sideShow
    return when (round.phase) {
        RoundPhase.BETTING -> if (state.settings.isPoker) {
            "${PokerRules.streetName(round.street)} · ${state.nameOf(round.turnId)}'s turn"
        } else {
            "${state.nameOf(round.turnId)}'s turn"
        }
        RoundPhase.SIDE_SHOW_REQUESTED ->
            "${state.nameOf(sideShow?.requesterId)} asked ${state.nameOf(sideShow?.targetId)} for a side show"
        RoundPhase.SIDE_SHOW_COMPARE ->
            "${state.nameOf(sideShow?.requesterId)} and ${state.nameOf(sideShow?.targetId)} are comparing cards"
        RoundPhase.SHOWDOWN -> if (state.settings.isPoker) {
            val index = round.potWinners.size
            val pot = round.pots.getOrNull(index)
            if (pot == null) "Showdown" else "Showdown · ${PokerRules.potLabel(round, index)} ${state.money(pot.amount)}"
        } else {
            "Show: " + round.showdownIds.joinToString(" vs ") { state.nameOf(it) }
        }
        RoundPhase.FINISHED -> winnerText(state, round)
    }
}

fun winnerText(state: GameState, round: Round): String {
    val contestedPots = round.pots.count { !(it.eligibleIds.size == 1 && round.pots.size > 1) }
    if (state.settings.isPoker && round.winnerIds.size > 1 && contestedPots > 1) {
        // Different pots went to different players.
        val won = PokerEngine.contestedWinnings(round)
        return round.winnerIds.joinToString { "${state.nameOf(it)} won ${state.money(won[it] ?: 0)}" }
    }
    return if (round.winnerIds.size == 1) {
        "${state.nameOf(round.winnerIds[0])} won ${state.money(round.pot)}"
    } else {
        "${state.namesOf(round.winnerIds)} split ${state.money(round.pot)}"
    }
}
