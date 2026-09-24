package com.threepatti.core

import kotlinx.serialization.Serializable

@Serializable
data class TableSettings(
    val startingBalance: Int = 250,
    val bootAmount: Int = 5,
    /** Highest amount a seen player may bet in one turn (the chaal limit). 0 means no limit. */
    val maxSeenBet: Int = 80,
    /** When the pot reaches this amount every player still in the round must show. 0 means no limit. */
    val potLimit: Int = 0,
    /** How many blind bets a player may make before they have to see their cards. 0 means no limit. */
    val maxBlindTurns: Int = 0,
    val currency: String = "₹",
) {
    fun validationError(): String? = when {
        startingBalance <= 0 -> "Starting chips must be more than 0"
        bootAmount <= 0 -> "Boot amount must be more than 0"
        bootAmount > startingBalance -> "Boot can't be more than the starting chips"
        maxSeenBet < 0 -> "Chaal limit can't be negative"
        maxSeenBet in 1 until bootAmount * 2 -> "Chaal limit must be at least ${bootAmount * 2} (twice the boot)"
        potLimit < 0 -> "Pot limit can't be negative"
        potLimit in 1..bootAmount * 2 -> "Pot limit must be more than ${bootAmount * 2}"
        maxBlindTurns < 0 -> "Blind limit can't be negative"
        currency.length > 4 -> "Currency symbol is too long"
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

@Serializable
enum class HandStatus { BLIND, SEEN, PACKED }

@Serializable
data class Hand(
    val playerId: String,
    val status: HandStatus = HandStatus.BLIND,
    /** Chips this player has put into the current pot, including the boot. */
    val invested: Int = 0,
    val blindTurns: Int = 0,
)

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
