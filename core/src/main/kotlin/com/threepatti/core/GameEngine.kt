package com.threepatti.core

import com.threepatti.core.GameAction.AddPlayer
import com.threepatti.core.GameAction.AdjustChips
import com.threepatti.core.GameAction.AnswerSideShow
import com.threepatti.core.GameAction.Bet
import com.threepatti.core.GameAction.Call
import com.threepatti.core.GameAction.CancelRound
import com.threepatti.core.GameAction.Check
import com.threepatti.core.GameAction.DeclareWinners
import com.threepatti.core.GameAction.ForceShow
import com.threepatti.core.GameAction.MoveSeat
import com.threepatti.core.GameAction.Pack
import com.threepatti.core.GameAction.RaiseTo
import com.threepatti.core.GameAction.RemovePlayer
import com.threepatti.core.GameAction.RenamePlayer
import com.threepatti.core.GameAction.RequestSideShow
import com.threepatti.core.GameAction.SeeCards
import com.threepatti.core.GameAction.SetSittingOut
import com.threepatti.core.GameAction.Show
import com.threepatti.core.GameAction.StartRound
import com.threepatti.core.GameAction.UpdateSettings

/**
 * Pure game logic. Every function takes a state and returns a new one, or throws
 * [GameRuleException] with a message that can be shown to the player.
 */
object GameEngine {
    const val MAX_LOG = 1000
    const val MAX_NAME_LENGTH = 20
    const val MAX_TABLE_NAME_LENGTH = 40

    fun newTable(tableName: String, settings: TableSettings, hostName: String): GameState {
        settings.validationError()?.let { fail(it) }
        val name = cleanName(hostName) ?: "Host"
        val host = Player(
            id = "p1",
            name = name,
            balance = settings.startingBalance,
            buyIn = settings.startingBalance,
            isHost = true,
            hasDevice = true,
            connected = true,
        )
        val title = collapseSpaces(tableName).take(MAX_TABLE_NAME_LENGTH).trim().ifEmpty { "$name's table" }
        return GameState(tableName = title, settings = settings, players = listOf(host), nextPlayerNumber = 2)
            .withLog(
                "$name opened a ${settings.gameName()} table (${settings.summary()}). " +
                    "Everyone starts with ${formatMoney(settings.startingBalance, settings.currency)}",
            )
    }

    /** Seats a new player and returns the new state together with the player's id. */
    fun addPlayer(state: GameState, name: String, hasDevice: Boolean, onBrowser: Boolean = false): Pair<GameState, String> {
        val (next, id) = seatPlayer(state, name, hasDevice, onBrowser)
        return next.bumped() to id
    }

    /**
     * The seat of a player the host added without a phone whose name matches [name], if any.
     * Joining with that name lets the player take over the seat from their own phone.
     */
    fun claimableSeat(state: GameState, name: String): String? {
        val clean = cleanName(name) ?: return null
        return state.players.firstOrNull { !it.hasDevice && it.name.equals(clean, ignoreCase = true) }?.id
    }

    /** Hands a seat to the phone or browser that just joined, keeping its chips and history. */
    fun claimSeat(state: GameState, playerId: String, onBrowser: Boolean): GameState {
        val player = state.player(playerId) ?: return state
        return state.updatePlayer(playerId) { it.copy(hasDevice = true, onBrowser = onBrowser) }
            .withLog("${player.name} now plays from their own ${if (onBrowser) "browser" else "phone"}", LogKind.SEAT)
            .bumped()
    }

    /** Records whether a returning player uses a browser or the app. */
    fun setOnBrowser(state: GameState, playerId: String, onBrowser: Boolean): GameState {
        val player = state.player(playerId) ?: return state
        if (player.onBrowser == onBrowser) return state
        return state.updatePlayer(playerId) { it.copy(onBrowser = onBrowser) }.bumped()
    }

    fun setConnected(state: GameState, playerId: String, connected: Boolean): GameState {
        val player = state.player(playerId) ?: return state
        if (player.connected == connected) return state
        return state.updatePlayer(playerId) { it.copy(connected = connected) }.bumped()
    }

    /**
     * Goes back to [previous] after the host pressed undo, while keeping what undo must not
     * change: who is online, players whose phones joined after that point, and the history.
     * Nothing is erased from the log or the round results: undone lines are only marked as undone.
     */
    fun restoreForUndo(previous: GameState, current: GameState, undoneText: String?): GameState {
        val currentById = current.players.associateBy { it.id }
        // Which phone owns a seat is not part of the game, so undo keeps it as it is now.
        val restored = previous.players.map { player ->
            val now = currentById[player.id]
            player.copy(
                connected = now?.connected ?: false,
                hasDevice = now?.hasDevice ?: player.hasDevice,
                onBrowser = now?.onBrowser ?: player.onBrowser,
            )
        }
        val knownIds = previous.players.map { it.id }.toSet()
        val start = previous.settings.startingBalance
        val joinedSince = current.players
            .filter { it.id !in knownIds && it.hasDevice }
            .map { it.copy(balance = start, buyIn = start, sittingOut = false) }
        val lastSeq = previous.log.lastOrNull()?.seq ?: 0
        val log = current.log.map { if (it.seq > lastSeq && it.kind == LogKind.MOVE) it.copy(undone = true) else it }
        // Results are only ever appended, so everything past the old list is what's being undone.
        val results = current.results.mapIndexed { i, r -> if (i >= previous.results.size) r.copy(undone = true) else r }
        return previous.copy(
            players = restored + joinedSince,
            log = log,
            results = results,
            undoCount = current.undoCount + 1,
            nextPlayerNumber = maxOf(previous.nextPlayerNumber, current.nextPlayerNumber),
            version = current.version + 1,
        ).withLog(if (undoneText != null) "Host undid: $undoneText" else "Host undid the last change", LogKind.UNDO)
    }

    fun apply(state: GameState, action: GameAction, actor: Actor = Actor.Host): GameState {
        authorize(state, action, actor)
        val poker = state.settings.isPoker
        val next = when (action) {
            is SeeCards -> teenPattiOnly(poker) { seeCards(state, action.playerId) }
            is Bet -> teenPattiOnly(poker) { bet(state, action.playerId, action.raise) }
            is Show -> teenPattiOnly(poker) { show(state, action.playerId) }
            is RequestSideShow -> teenPattiOnly(poker) { requestSideShow(state, action.playerId) }
            is AnswerSideShow -> teenPattiOnly(poker) { answerSideShow(state, action.playerId, action.accept) }
            is Check -> pokerOnly(poker) { PokerEngine.check(state, action.playerId) }
            is Call -> pokerOnly(poker) { PokerEngine.call(state, action.playerId) }
            is RaiseTo -> pokerOnly(poker) { PokerEngine.raiseTo(state, action.playerId, action.amount) }
            is Pack -> if (poker) PokerEngine.fold(state, action.playerId) else pack(state, action.playerId)
            StartRound -> if (poker) PokerEngine.startHand(state) else startRound(state)
            is DeclareWinners ->
                if (poker) PokerEngine.declarePotWinners(state, action.winnerIds) else declareWinners(state, action.winnerIds)
            ForceShow -> if (poker) PokerEngine.showdown(state, "Host called the showdown") else forceShow(state)
            CancelRound -> cancelRound(state)
            is AddPlayer -> seatPlayer(state, action.name, hasDevice = false).first
            is RemovePlayer -> removePlayer(state, action.playerId)
            is RenamePlayer -> renamePlayer(state, action.playerId, action.name)
            is AdjustChips -> adjustChips(state, action.playerId, action.amount)
            is SetSittingOut -> setSittingOut(state, action.playerId, action.sittingOut)
            is MoveSeat -> moveSeat(state, action.playerId, action.offset)
            is UpdateSettings -> updateSettings(state, action.settings)
        }
        return next.bumped()
    }

    private inline fun teenPattiOnly(poker: Boolean, move: () -> GameState): GameState =
        if (poker) fail("That move is not part of poker") else move()

    private inline fun pokerOnly(poker: Boolean, move: () -> GameState): GameState =
        if (poker) move() else fail("That move is not part of 3 Patti")

    private fun authorize(state: GameState, action: GameAction, actor: Actor) {
        val playerId = (actor as? Actor.Remote)?.playerId ?: return
        if (state.player(playerId) == null) fail("You are no longer at this table")
        if (action !is SeatAction) fail("Only the host can do that")
        if (action.playerId != playerId) fail("You can only play for yourself")
    }

    // Round flow.

    private fun startRound(state: GameState): GameState {
        if (state.isRoundActive) fail("Finish the current round first")
        val boot = state.settings.bootAmount
        val eligible = state.players.filter { !it.sittingOut && it.balance >= boot }
        if (eligible.size < 2) fail("Need at least 2 players with ${state.money(boot)} or more to start a round")
        val eligibleIds = eligible.map { it.id }.toSet()
        val dealerId = nextDealer(state.players, state.lastDealerId, eligibleIds)
        val hands = eligible.map { Hand(playerId = it.id, status = HandStatus.BLIND, invested = boot) }
        val number = state.nextRoundNumber
        val draft = Round(
            number = number,
            dealerId = dealerId,
            previousDealerId = state.lastDealerId,
            hands = hands,
            pot = boot * hands.size,
            stake = boot,
            turnId = null,
            phase = RoundPhase.BETTING,
        )
        val round = draft.copy(turnId = Rules.nextActive(draft, dealerId)?.playerId)
        var next = state.copy(
            players = state.players.map { if (it.id in eligibleIds) it.copy(balance = it.balance - boot) else it },
            round = round,
            lastDealerId = dealerId,
        ).withLog("Round $number started. ${state.nameOf(dealerId)} deals, boot ${state.money(boot)} from ${hands.size} players")
        val short = state.players.filter { !it.sittingOut && it.balance < boot }
        if (short.isNotEmpty()) {
            next = next.withLog("${short.joinToString { it.name }} can't pay the boot and sit out this round")
        }
        if (Rules.potLimitReached(next.settings, round)) {
            next = showdownAll(next, "Pot limit reached. Everyone must show")
        }
        return next
    }

    private fun seeCards(state: GameState, playerId: String): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No round is running")
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: fail("$name is not in this round")
        when (hand.status) {
            HandStatus.SEEN, HandStatus.ACTIVE -> fail("$name has already seen their cards")
            HandStatus.PACKED -> fail("$name has packed")
            HandStatus.BLIND -> Unit
        }
        return state.updateHand(playerId) { it.copy(status = HandStatus.SEEN) }.withLog("$name saw their cards")
    }

    private fun bet(state: GameState, playerId: String, raise: Boolean): GameState {
        Rules.betError(state, playerId, raise)?.let { fail(it) }
        val round = state.round!!
        val hand = round.hand(playerId)!!
        val amount = Rules.betAmount(round, hand, raise)
        val blind = hand.status == HandStatus.BLIND
        var next = state.pay(playerId, amount).updateRound { if (raise) it.copy(stake = it.stake * 2) else it }
        if (blind) next = next.updateHand(playerId) { it.copy(blindTurns = it.blindTurns + 1) }
        val kind = if (blind) "blind" else "chaal"
        val name = state.nameOf(playerId)
        next = next.withLog(
            if (raise) "$name raised, $kind ${state.money(amount)}" else "$name played $kind ${state.money(amount)}",
        )
        return afterPayment(next, playerId)
    }

    private fun pack(state: GameState, playerId: String): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No round is running")
        if (round.phase != RoundPhase.BETTING) fail("Packing is allowed only while betting is open")
        val name = state.nameOf(playerId)
        val hand = round.hand(playerId) ?: fail("$name is not in this round")
        if (hand.status == HandStatus.PACKED) fail("$name has already packed")
        var next = state.updateHand(playerId) { it.copy(status = HandStatus.PACKED) }.withLog("$name packed")
        val active = next.round!!.activeHands
        if (active.size == 1) return finishRound(next, listOf(active.single().playerId))
        if (round.turnId == playerId) next = advanceTurn(next, playerId)
        return next
    }

    private fun show(state: GameState, playerId: String): GameState {
        Rules.showError(state, playerId)?.let { fail(it) }
        val round = state.round!!
        val amount = Rules.callAmount(round, round.hand(playerId)!!)
        val opponent = round.activeHands.first { it.playerId != playerId }
        return state.pay(playerId, amount)
            .withLog("${state.nameOf(playerId)} paid ${state.money(amount)} and asked ${state.nameOf(opponent.playerId)} for a show")
            .updateRound {
                it.copy(phase = RoundPhase.SHOWDOWN, turnId = null, showdownIds = it.activeHands.map { h -> h.playerId })
            }
    }

    private fun requestSideShow(state: GameState, playerId: String): GameState {
        Rules.sideShowError(state, playerId)?.let { fail(it) }
        val round = state.round!!
        val target = Rules.previousActive(round, playerId)!!
        val amount = round.stake * 2
        val next = state.pay(playerId, amount)
            .withLog("${state.nameOf(playerId)} paid ${state.money(amount)} and asked ${state.nameOf(target.playerId)} for a side show")
        if (Rules.potLimitReached(next.settings, next.round!!)) {
            return showdownAll(next, "Pot limit reached. Everyone still playing must show")
        }
        return next.updateRound {
            it.copy(phase = RoundPhase.SIDE_SHOW_REQUESTED, sideShow = SideShow(playerId, target.playerId))
        }
    }

    private fun answerSideShow(state: GameState, playerId: String, accept: Boolean): GameState {
        val round = state.round
        if (round == null || round.phase != RoundPhase.SIDE_SHOW_REQUESTED) fail("There is no side show request to answer")
        val sideShow = round.sideShow!!
        if (sideShow.targetId != playerId) fail("Only ${state.nameOf(sideShow.targetId)} can answer this side show")
        val name = state.nameOf(playerId)
        return if (accept) {
            state.updateRound { it.copy(phase = RoundPhase.SIDE_SHOW_COMPARE) }
                .withLog("$name accepted the side show with ${state.nameOf(sideShow.requesterId)}")
        } else {
            val next = state.updateRound { it.copy(phase = RoundPhase.BETTING, sideShow = null) }
                .withLog("$name refused the side show")
            advanceTurn(next, sideShow.requesterId)
        }
    }

    private fun declareWinners(state: GameState, winnerIds: List<String>): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No round is running")
        return when (round.phase) {
            RoundPhase.SIDE_SHOW_COMPARE -> {
                val sideShow = round.sideShow!!
                val winner = winnerIds.singleOrNull() ?: fail("Pick one winner for the side show")
                val loser = when (winner) {
                    sideShow.requesterId -> sideShow.targetId
                    sideShow.targetId -> sideShow.requesterId
                    else -> fail(
                        "The winner must be ${state.nameOf(sideShow.requesterId)} or ${state.nameOf(sideShow.targetId)}",
                    )
                }
                val next = state.updateHand(loser) { it.copy(status = HandStatus.PACKED) }
                    .updateRound { it.copy(phase = RoundPhase.BETTING, sideShow = null) }
                    .withLog("Side show: ${state.nameOf(winner)} won, ${state.nameOf(loser)} packed")
                val active = next.round!!.activeHands
                if (active.size == 1) {
                    finishRound(next, listOf(active.single().playerId))
                } else {
                    advanceTurn(next, sideShow.requesterId)
                }
            }
            RoundPhase.SHOWDOWN -> {
                if (winnerIds.isEmpty()) fail("Pick the winner")
                if (winnerIds.toSet().size != winnerIds.size) fail("A player was picked twice")
                winnerIds.firstOrNull { it !in round.showdownIds }?.let { fail("${state.nameOf(it)} is not part of this show") }
                finishRound(state, winnerIds)
            }
            else -> fail("There is no show waiting for a result")
        }
    }

    private fun forceShow(state: GameState): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No round is running")
        if (round.phase == RoundPhase.SHOWDOWN) fail("A show is already waiting for its result")
        return showdownAll(state, "Host called a show for everyone still playing")
    }

    private fun cancelRound(state: GameState): GameState {
        val round = state.round?.takeIf { it.isActive } ?: fail("No round is running")
        var next = state
        for (hand in round.hands) {
            next = next.updatePlayer(hand.playerId) { it.copy(balance = it.balance + hand.invested) }
        }
        return next.copy(round = null, lastDealerId = round.previousDealerId)
            .withLog("${state.roundWord.replaceFirstChar { it.uppercase() }} ${round.number} cancelled, all bets returned")
    }

    private fun afterPayment(state: GameState, playerId: String): GameState =
        if (Rules.potLimitReached(state.settings, state.round!!)) {
            showdownAll(state, "Pot limit reached. Everyone still playing must show")
        } else {
            advanceTurn(state, playerId)
        }

    private fun advanceTurn(state: GameState, fromId: String): GameState {
        val next = Rules.nextActive(state.round!!, fromId)
        return state.updateRound { it.copy(turnId = next?.playerId) }
    }

    private fun showdownAll(state: GameState, message: String): GameState =
        state.updateRound {
            it.copy(
                phase = RoundPhase.SHOWDOWN,
                turnId = null,
                sideShow = null,
                showdownIds = it.activeHands.map { h -> h.playerId },
            )
        }.withLog(message)

    private fun finishRound(state: GameState, winnerIds: List<String>): GameState {
        val round = state.round!!
        val ordered = round.hands.map { it.playerId }.filter { it in winnerIds }
        val share = round.pot / ordered.size
        val extra = round.pot % ordered.size
        // Any odd chip from a split pot goes to the first winner after the dealer.
        val winnings = ordered.mapIndexed { i, id -> id to (share + (if (i == 0) extra else 0)) }.toMap()
        val players = state.players.map { p -> winnings[p.id]?.let { p.copy(balance = p.balance + it) } ?: p }
        val changes = round.hands.associate { it.playerId to ((winnings[it.playerId] ?: 0) - it.invested) }
        val names = ordered.map { state.nameOf(it) }
        val result = RoundResult(round.number, round.pot, ordered, names, changes)
        val text = if (ordered.size == 1) {
            "${names.single()} won the pot of ${state.money(round.pot)}"
        } else {
            "Pot of ${state.money(round.pot)} split between ${names.joinToString(" and ")}"
        }
        return state.copy(
            players = players,
            round = round.copy(phase = RoundPhase.FINISHED, turnId = null, sideShow = null, winnerIds = ordered),
            results = state.results + result,
        ).withLog(text)
    }

    // Table management.

    private fun seatPlayer(
        state: GameState,
        requestedName: String,
        hasDevice: Boolean,
        onBrowser: Boolean = false,
    ): Pair<GameState, String> {
        val number = state.nextPlayerNumber
        val id = "p$number"
        val name = uniqueName(state, cleanName(requestedName) ?: "Player $number", exceptId = null)
        val start = state.settings.startingBalance
        val player = Player(
            id = id,
            name = name,
            balance = start,
            buyIn = start,
            hasDevice = hasDevice,
            onBrowser = onBrowser,
            connected = false,
        )
        val next = state.copy(players = state.players + player, nextPlayerNumber = number + 1)
            .withLog(
                if (hasDevice) "$name joined the table" else "$name was seated by the host",
                if (hasDevice) LogKind.SEAT else LogKind.MOVE,
            )
        return next to id
    }

    private fun removePlayer(state: GameState, playerId: String): GameState {
        val player = state.player(playerId) ?: fail("Player not found")
        if (player.isHost) fail("The host can't be removed")
        if (state.round?.let { it.isActive && it.hand(playerId) != null } == true) fail("${player.name} is playing this ${state.roundWord}")
        if (player.net != 0) {
            fail("${player.name} is at ${formatSignedMoney(player.net, state.settings.currency)}. Settle up first, or let them sit out")
        }
        return state.copy(players = state.players.filter { it.id != playerId })
            .withLog("${player.name} was removed from the table")
    }

    private fun renamePlayer(state: GameState, playerId: String, newName: String): GameState {
        val player = state.player(playerId) ?: fail("Player not found")
        val name = cleanName(newName) ?: fail("Name can't be empty")
        state.players.firstOrNull { it.id != playerId && it.name.equals(name, ignoreCase = true) }
            ?.let { fail("Someone is already called ${it.name}") }
        if (name == player.name) return state
        return state.updatePlayer(playerId) { it.copy(name = name) }.withLog("${player.name} is now called $name")
    }

    private fun adjustChips(state: GameState, playerId: String, amount: Int): GameState {
        val player = state.player(playerId) ?: fail("Player not found")
        if (amount == 0) fail("Enter an amount")
        if (player.balance + amount < 0) fail("${player.name} has only ${state.money(player.balance)}")
        return state.updatePlayer(playerId) { it.copy(balance = it.balance + amount, buyIn = it.buyIn + amount) }
            .withLog(
                if (amount > 0) {
                    "${player.name} bought ${state.money(amount)} more chips"
                } else {
                    "${player.name} cashed out ${state.money(-amount)}"
                },
            )
    }

    private fun setSittingOut(state: GameState, playerId: String, sittingOut: Boolean): GameState {
        val player = state.player(playerId) ?: fail("Player not found")
        if (player.sittingOut == sittingOut) return state
        return state.updatePlayer(playerId) { it.copy(sittingOut = sittingOut) }
            .withLog(if (sittingOut) "${player.name} will sit out from the next ${state.roundWord}" else "${player.name} is back in the game")
    }

    private fun moveSeat(state: GameState, playerId: String, offset: Int): GameState {
        if (state.isRoundActive) fail("Change seats between ${state.roundWord}s")
        val index = state.players.indexOfFirst { it.id == playerId }
        if (index < 0) fail("Player not found")
        val target = (index + offset).coerceIn(0, state.players.lastIndex)
        if (target == index) return state
        val players = state.players.toMutableList()
        players.add(target, players.removeAt(index))
        return state.copy(players = players)
    }

    private fun updateSettings(state: GameState, settings: TableSettings): GameState {
        if (state.isRoundActive) fail("Change settings between ${state.roundWord}s")
        if (settings.game != state.settings.game) {
            fail("This table plays ${state.settings.gameName()}. Open a new table to play ${settings.gameName()}")
        }
        settings.validationError()?.let { fail(it) }
        if (settings == state.settings) return state
        return state.copy(settings = settings).withLog("Table settings changed: ${settings.summary()}")
    }

    // Helpers.

    fun cleanName(raw: String): String? =
        collapseSpaces(raw).take(MAX_NAME_LENGTH).trim().ifEmpty { null }

    private fun collapseSpaces(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    private fun uniqueName(state: GameState, name: String, exceptId: String?): String {
        val taken = state.players.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var i = 2
        while ("$name $i".lowercase() in taken) i++
        return "$name $i"
    }
}
