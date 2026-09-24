package com.threepatti.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Actions a player is allowed to take for their own seat. Everything else is host only. */
interface SeatAction {
    val playerId: String
}

@Serializable
sealed class GameAction {
    // Moves during a round.

    @Serializable
    @SerialName("see")
    data class SeeCards(override val playerId: String) : GameAction(), SeatAction

    /** Blind or chaal at the current stake, or double the stake when [raise] is true. */
    @Serializable
    @SerialName("bet")
    data class Bet(override val playerId: String, val raise: Boolean = false) : GameAction(), SeatAction

    @Serializable
    @SerialName("pack")
    data class Pack(override val playerId: String) : GameAction(), SeatAction

    @Serializable
    @SerialName("show")
    data class Show(override val playerId: String) : GameAction(), SeatAction

    @Serializable
    @SerialName("side_show")
    data class RequestSideShow(override val playerId: String) : GameAction(), SeatAction

    @Serializable
    @SerialName("side_show_answer")
    data class AnswerSideShow(override val playerId: String, val accept: Boolean) : GameAction(), SeatAction

    // Host controls.

    @Serializable
    @SerialName("start_round")
    data object StartRound : GameAction()

    /** Host enters the result of a show (one or more winners) or a side show (exactly one winner). */
    @Serializable
    @SerialName("declare_winners")
    data class DeclareWinners(val winnerIds: List<String>) : GameAction()

    /** Everyone still in the round shows their cards. */
    @Serializable
    @SerialName("force_show")
    data object ForceShow : GameAction()

    /** Misdeal: every bet of the current round goes back to its player. */
    @Serializable
    @SerialName("cancel_round")
    data object CancelRound : GameAction()

    /** Seat a player who doesn't have a phone. The host plays their moves. */
    @Serializable
    @SerialName("add_player")
    data class AddPlayer(val name: String) : GameAction()

    @Serializable
    @SerialName("remove_player")
    data class RemovePlayer(val playerId: String) : GameAction()

    @Serializable
    @SerialName("rename")
    data class RenamePlayer(override val playerId: String, val name: String) : GameAction(), SeatAction

    /** Positive amount: player buys more chips. Negative amount: player cashes chips out. */
    @Serializable
    @SerialName("adjust_chips")
    data class AdjustChips(val playerId: String, val amount: Int) : GameAction()

    @Serializable
    @SerialName("sit_out")
    data class SetSittingOut(override val playerId: String, val sittingOut: Boolean) : GameAction(), SeatAction

    /** Move a player [offset] seats (negative is towards the first seat). */
    @Serializable
    @SerialName("move_seat")
    data class MoveSeat(val playerId: String, val offset: Int) : GameAction()

    @Serializable
    @SerialName("settings")
    data class UpdateSettings(val settings: TableSettings) : GameAction()
}

/** Who is asking for an action: the host device, or a connected player's device. */
sealed interface Actor {
    data object Host : Actor
    data class Remote(val playerId: String) : Actor
}

class GameRuleException(message: String) : Exception(message)
