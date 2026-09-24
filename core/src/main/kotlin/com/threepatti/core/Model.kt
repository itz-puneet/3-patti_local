package com.threepatti.core

import kotlinx.serialization.Serializable

/** Which game a table plays. Chosen when the table is opened and fixed after that. */
@Serializable
enum class GameType { TEEN_PATTI, POKER }

/** How much a poker player may bet: anything up to all their chips, or at most the size of the pot. */
@Serializable
enum class BetLimit { NO_LIMIT, POT_LIMIT }

/** Who puts in the poker ante: every player, or the big blind for the whole table. */
@Serializable
enum class AnteStyle { EVERYONE, BIG_BLIND }

/** Poker betting rounds. The cards themselves are dealt for real at the table. */
@Serializable
enum class Street { PREFLOP, FLOP, TURN, RIVER }

@Serializable
data class TableSettings(
    val game: GameType = GameType.TEEN_PATTI,
    val startingBalance: Int = 250,
    // 3 Patti.
    val bootAmount: Int = 5,
    /** Highest amount a seen player may bet in one turn (the chaal limit). 0 means no limit. */
    val maxSeenBet: Int = 80,
    /** When the pot reaches this amount every player still in the round must show. 0 means no limit. */
    val potLimit: Int = 0,
    /** How many blind bets a player may make before they have to see their cards. 0 means no limit. */
    val maxBlindTurns: Int = 0,
    // Poker.
    val smallBlind: Int = 1,
    val bigBlind: Int = 2,
    val betLimit: BetLimit = BetLimit.NO_LIMIT,
    /** Ante per player each hand, on top of the blinds. 0 means no ante. */
    val ante: Int = 0,
    val anteStyle: AnteStyle = AnteStyle.EVERYONE,
    val currency: String = "₹",
) {
    val isPoker: Boolean get() = game == GameType.POKER

    fun validationError(): String? = when {
        startingBalance <= 0 -> "Starting chips must be more than 0"
        currency.length > 4 -> "Currency symbol is too long"
        isPoker -> when {
            smallBlind <= 0 -> "Small blind must be more than 0"
            bigBlind < smallBlind -> "Big blind can't be smaller than the small blind"
            bigBlind > startingBalance -> "Big blind can't be more than the starting chips"
            ante < 0 -> "Ante can't be negative"
            ante > startingBalance -> "Ante can't be more than the starting chips"
            else -> null
        }
        bootAmount <= 0 -> "Boot amount must be more than 0"
        bootAmount > startingBalance -> "Boot can't be more than the starting chips"
        maxSeenBet < 0 -> "Chaal limit can't be negative"
        maxSeenBet in 1 until bootAmount * 2 -> "Chaal limit must be at least ${bootAmount * 2} (twice the boot)"
        potLimit < 0 -> "Pot limit can't be negative"
        potLimit in 1..bootAmount * 2 -> "Pot limit must be more than ${bootAmount * 2}"
        maxBlindTurns < 0 -> "Blind limit can't be negative"
        else -> null
    }
}

@Serializable
data class Player(
    val id: String,
    val name: String,
    /** Chips the player has in front of them right now. */
    val balance: Int,
    /** Total chips handed to the player (starting chips plus top ups, minus cash outs). */
    val buyIn: Int,
    val isHost: Boolean = false,
    /** False for players added by the host who don't have their own phone. */
    val hasDevice: Boolean = true,
    /** True when the player plays from a web browser (such as an iPhone) instead of the app. */
    val onBrowser: Boolean = false,
    val connected: Boolean = false,
    val sittingOut: Boolean = false,
) {
    /** Profit (positive) or loss (negative) for the session so far. */
    val net: Int get() = balance - buyIn
}

/** BLIND and SEEN are 3 Patti. A poker hand is ACTIVE until it folds. PACKED is a pack or fold in both games. */
@Serializable
enum class HandStatus { BLIND, SEEN, PACKED, ACTIVE }

@Serializable
data class Hand(
    val playerId: String,
    val status: HandStatus = HandStatus.BLIND,
    /** Chips this player has put into the current pot, including the boot or blinds. */
    val invested: Int = 0,
    val blindTurns: Int = 0,
    // Poker.
    /** Chips put in during the current betting round. */
    val streetBet: Int = 0,
    val allIn: Boolean = false,
    /** Has acted since the last full raise, so the betting round can end once everyone has matched. */
    val acted: Boolean = false,
    /** Faced only a short all-in raise after acting: may call or fold but not raise again. */
    val raiseClosed: Boolean = false,
)

/** A poker pot and the players who can win it. The main pot comes first, then side pots. */
@Serializable
data class Pot(val amount: Int, val eligibleIds: List<String>)

@Serializable
enum class RoundPhase {
    /** Players take turns to bet. */
    BETTING,

    /** A seen player asked the previous player for a side show and is waiting for an answer. */
    SIDE_SHOW_REQUESTED,

    /** Side show accepted: the two players compare cards and the host enters who won. */
    SIDE_SHOW_COMPARE,

    /** Cards are shown on the table and the host enters the winner. */
    SHOWDOWN,

    /** Pot has been paid out. */
    FINISHED,
}

@Serializable
data class SideShow(val requesterId: String, val targetId: String)

@Serializable
data class Round(
    val number: Int,
    val dealerId: String,
    val previousDealerId: String? = null,
    /** Players dealt into this round, in seat order. */
    val hands: List<Hand>,
    val pot: Int,
    /** Current stake: what a blind player pays. A seen player pays twice this. */
    val stake: Int,
    val turnId: String?,
    val phase: RoundPhase,
    val sideShow: SideShow? = null,
    val showdownIds: List<String> = emptyList(),
    val winnerIds: List<String> = emptyList(),
    // Poker.
    val street: Street = Street.PREFLOP,
    /** Highest bet in the current betting round that others must match. */
    val currentBet: Int = 0,
    /** Smallest raise increment allowed right now. */
    val minRaise: Int = 0,
    val smallBlindId: String? = null,
    val bigBlindId: String? = null,
    /** Pots at showdown, main pot first. */
    val pots: List<Pot> = emptyList(),
    /** Winners of the pots decided so far, in the same order as [pots]. */
    val potWinners: List<List<String>> = emptyList(),
) {
    val isActive: Boolean get() = phase != RoundPhase.FINISHED
    val activeHands: List<Hand> get() = hands.filter { it.status != HandStatus.PACKED }

    fun hand(playerId: String?): Hand? = hands.firstOrNull { it.playerId == playerId }
}

@Serializable
data class RoundResult(
    val number: Int,
    val pot: Int,
    val winnerIds: List<String>,
    val winnerNames: List<String>,
    /** Chips won or lost in this round by every player who was dealt in. */
    val changes: Map<String, Int>,
)

@Serializable
data class LogEntry(val seq: Long, val text: String)

@Serializable
data class GameState(
    val tableName: String,
    val settings: TableSettings,
    /** Players in seat order. */
    val players: List<Player>,
    val round: Round? = null,
    val lastDealerId: String? = null,
    val results: List<RoundResult> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val nextPlayerNumber: Int = 1,
    val version: Long = 0,
) {
    val isRoundActive: Boolean get() = round?.isActive == true

    /** "hand" in poker, "round" in 3 Patti. */
    val roundWord: String get() = if (settings.isPoker) "hand" else "round"
    val hostName: String get() = players.firstOrNull { it.isHost }?.name ?: "Host"

    fun player(id: String?): Player? = players.firstOrNull { it.id == id }

    fun nameOf(id: String?): String = player(id)?.name ?: "Former player"

    fun money(amount: Int): String = formatMoney(amount, settings.currency)
}

fun formatMoney(amount: Int, currency: String): String =
    if (amount < 0) "-$currency${-amount}" else "$currency$amount"

fun formatSignedMoney(amount: Int, currency: String): String = when {
    amount > 0 -> "+$currency$amount"
    amount < 0 -> "-$currency${-amount}"
    else -> "${currency}0"
}
