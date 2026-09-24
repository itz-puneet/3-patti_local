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
        RoundPhase.BETTING -> "${state.nameOf(round.turnId)}'s turn"
        RoundPhase.SIDE_SHOW_REQUESTED ->
            "${state.nameOf(sideShow?.requesterId)} asked ${state.nameOf(sideShow?.targetId)} for a side show"
        RoundPhase.SIDE_SHOW_COMPARE ->
            "${state.nameOf(sideShow?.requesterId)} and ${state.nameOf(sideShow?.targetId)} are comparing cards"
        RoundPhase.SHOWDOWN -> "Show: " + round.showdownIds.joinToString(" vs ") { state.nameOf(it) }
        RoundPhase.FINISHED -> winnerText(state, round)
    }
}

fun winnerText(state: GameState, round: Round): String =
    if (round.winnerIds.size == 1) {
        "${state.nameOf(round.winnerIds[0])} won ${state.money(round.pot)}"
    } else {
        "${state.namesOf(round.winnerIds)} split ${state.money(round.pot)}"
    }
