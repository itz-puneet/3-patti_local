package com.threepatti.core

// Small helpers for building a new GameState, shared by the 3 Patti and poker engines.

internal fun fail(message: String): Nothing = throw GameRuleException(message)

internal fun GameState.bumped(): GameState = copy(version = version + 1)

internal fun GameState.withLog(text: String, kind: LogKind = LogKind.MOVE): GameState {
    val seq = (log.lastOrNull()?.seq ?: 0) + 1
    return copy(log = (log + LogEntry(seq, text, kind)).takeLast(GameEngine.MAX_LOG))
}

internal fun GameState.updatePlayer(id: String, change: (Player) -> Player): GameState =
    copy(players = players.map { if (it.id == id) change(it) else it })

internal fun GameState.updateRound(change: (Round) -> Round): GameState = copy(round = change(round!!))

internal fun GameState.updateHand(id: String, change: (Hand) -> Hand): GameState =
    updateRound { r -> r.copy(hands = r.hands.map { if (it.playerId == id) change(it) else it }) }

/** Moves [amount] chips from the player into the pot. */
internal fun GameState.pay(id: String, amount: Int): GameState =
    updatePlayer(id) { it.copy(balance = it.balance - amount) }
        .updateRound { r ->
            r.copy(
                pot = r.pot + amount,
                hands = r.hands.map { if (it.playerId == id) it.copy(invested = it.invested + amount) else it },
            )
        }

/** The next seat after [lastDealerId] that is in [eligible]. */
internal fun nextDealer(players: List<Player>, lastDealerId: String?, eligible: Set<String>): String {
    val ids = players.map { it.id }
    val start = ids.indexOf(lastDealerId)
    for (i in 1..ids.size) {
        val id = ids[(start + i).mod(ids.size)]
        if (id in eligible) return id
    }
    return eligible.first()
}
