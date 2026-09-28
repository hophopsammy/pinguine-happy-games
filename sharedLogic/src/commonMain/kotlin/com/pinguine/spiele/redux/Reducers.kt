package com.pinguine.spiele.redux

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.Player
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.Round
import com.pinguine.spiele.model.Usernames
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.model.remapPlayers
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.persistence.inCanonicalOrder
import com.pinguine.spiele.rules.BiberbandeRules
import com.pinguine.spiele.rules.WizardRules
import com.pinguine.spiele.rules.completedRoundCount
import com.pinguine.spiele.rules.rules
import com.pinguine.spiele.rules.withRecomputedEnd
import com.pinguine.spiele.sync.DataMerger
import org.reduxkotlin.Reducer
import org.reduxkotlin.typedReducer

internal val rootReducer: Reducer<AppState> = typedReducer<AppState, AppAction> { state, action -> appReducer(state, action) }

internal const val MIN_POINTS = -999
internal const val MAX_POINTS = 9999

enum class UsernameIssue { EMPTY, TAKEN }

internal fun appReducer(state: AppState, action: AppAction): AppState = when (action) {
    AppAction.LoadRequested -> state.copy(loadStatus = LoadStatus.LOADING, loadError = null)
    is AppAction.DataLoaded -> dataLoaded(state, action.data)
    is AppAction.DataLoadFailed -> state.copy(loadStatus = LoadStatus.FAILED, loadError = action.message)
    is AppAction.StartFresh -> state.copy(loadStatus = LoadStatus.LOADING, loadError = null)
    is AppAction.PersistFailed -> state.copy(persistError = action.message)
    AppAction.DismissErrors -> state.copy(persistError = null, importError = null)

    is AppAction.AddPlayer -> addPlayer(state, action)
    is AppAction.RenamePlayer -> renamePlayer(state, action)
    is AppAction.SetPlayerArchived -> setPlayerArchived(state, action)
    is AppAction.MergePlayers -> mergePlayers(state, action)
    is AppAction.DeletePlayer -> deletePlayer(state, action)

    is AppAction.StartNewGame -> startNewGame(state, action.type, template = null)
    is AppAction.Rematch -> state.game(action.gameId)?.let { startNewGame(state, it.type, template = it) } ?: state
    is AppAction.ToggleNewGamePlayer -> toggleNewGamePlayer(state, action.playerId)
    is AppAction.MoveNewGamePlayer -> moveNewGamePlayer(state, action.fromIndex, action.toIndex)
    is AppAction.SetNewGameRounds -> setNewGameRounds(state, action.rounds)
    AppAction.CancelNewGame -> state.copy(newGame = null)
    is AppAction.CreateGame -> createGame(state, action)

    is AppAction.BeginRoundEntry -> beginRoundEntry(state, action.gameId, action.roundIndex)
    is AppAction.SetEntryPoints -> updateEntry(state, EntryPhase.POINTS) { it.copy(points = it.points + (action.playerId to action.points.coerceIn(MIN_POINTS, MAX_POINTS))) }
    is AppAction.ClearEntryPoints -> updateEntry(state, EntryPhase.POINTS) { it.copy(points = it.points - action.playerId) }
    is AppAction.SetRoundEnder -> setRoundEnder(state, action.playerId)
    is AppAction.SetEntryBid -> setWizardValue(state, action.playerId, action.bid, bids = true)
    is AppAction.SetEntryTricks -> setWizardValue(state, action.playerId, action.tricks, bids = false)
    is AppAction.SubmitRoundEntry -> submitRoundEntry(state, action)
    AppAction.CancelRoundEntry -> state.copy(roundEntry = null)

    is AppAction.DeleteLastRound -> deleteLastRound(state, action)
    is AppAction.EndGameNow -> endGameNow(state, action)
    is AppAction.PlayExtraRound -> playExtraRound(state, action)
    is AppAction.DeleteGame -> deleteGame(state, action)

    is AppAction.ImportDumpSelected -> state.copy(pendingImport = null, importError = null)
    is AppAction.ImportDumpParsed -> state.copy(pendingImport = action.data, importError = null)
    is AppAction.ImportDumpFailed -> state.copy(pendingImport = null, importError = action.error)
    is AppAction.ConfirmImport -> state.pendingImport?.let { state.mergedWith(it).copy(pendingImport = null) } ?: state
    AppAction.CancelImport -> state.copy(pendingImport = null)
}

// region Loading and merging

private fun dataLoaded(state: AppState, data: SavedData): AppState {
    val loaded = state
        .withData(data.copy(players = data.players.inCanonicalOrder(), games = data.games.inCanonicalOrder()))
        .copy(loadStatus = LoadStatus.READY, loadError = null)
    return loaded.withSanitizedDrafts()
}

internal fun AppState.mergedWith(incoming: SavedData): AppState {
    val merged = DataMerger.merge(toSavedData(), incoming).data
    val result = withData(merged.copy(exportedAt = null))
    return if (result === this) this else result.withSanitizedDrafts()
}

/** After data changed underneath, drop draft values that no longer point at known players or games. */
private fun AppState.withSanitizedDrafts(): AppState {
    val activeIds = players.filter { !it.archived }.map { it.id }.toSet()
    val draft = newGame
    val sanitizedDraft = draft?.let { d -> d.copy(selectedPlayerIds = d.selectedPlayerIds.filter { it in activeIds }) }
    val entry = roundEntry
    val entryGame = entry?.let { game(it.gameId) }
    val entryIsValid = entry != null && entryGame != null && entry.roundIndex <= entryGame.rounds.size &&
        (entry.points.keys + entry.bids.keys + entry.tricks.keys).all { it in entryGame.playerIds }
    val next = copy(
        newGame = if (sanitizedDraft == draft) draft else sanitizedDraft,
        roundEntry = if (entry == null || entryIsValid) entry else null,
    )
    return if (next == this) this else next
}

// endregion

// region Players

internal fun AppState.usernameIssue(name: String, editingPlayerId: String?): UsernameIssue? {
    val id = Usernames.normalize(name)
    if (id.isEmpty()) return UsernameIssue.EMPTY
    val owner = players.firstOrNull { it.id == id || id in it.aliases }
    return if (owner == null || owner.id == editingPlayerId) null else UsernameIssue.TAKEN
}

private fun addPlayer(state: AppState, action: AppAction.AddPlayer): AppState {
    if (state.usernameIssue(action.name, editingPlayerId = null) != null) return state
    val player = Player(
        id = Usernames.normalize(action.name),
        name = Usernames.clean(action.name),
        createdAt = action.timestamp,
        updatedAt = action.timestamp,
    )
    val draft = state.newGame
    val selectInDraft = action.selectForNewGame && draft != null && draft.selectedPlayerIds.size < draft.type.maxPlayers
    return state.copy(
        players = (state.players + player).inCanonicalOrder(),
        newGame = if (selectInDraft) draft.copy(selectedPlayerIds = draft.selectedPlayerIds + player.id) else draft,
    )
}

private fun renamePlayer(state: AppState, action: AppAction.RenamePlayer): AppState {
    val player = state.player(action.playerId) ?: return state
    if (state.usernameIssue(action.newName, editingPlayerId = player.id) != null) return state
    val newId = Usernames.normalize(action.newName)
    val newName = Usernames.clean(action.newName)
    if (newId == player.id && newName == player.name) return state
    val renamed = player.copy(
        id = newId,
        name = newName,
        aliases = if (newId == player.id) player.aliases else player.aliases + player.id - newId,
        updatedAt = action.timestamp,
    )
    val players = (state.players - player + renamed).inCanonicalOrder()
    if (newId == player.id) return state.copy(players = players)
    return state.withPlayerIdsRemapped(mapOf(player.id to newId)).copy(players = players)
}

private fun setPlayerArchived(state: AppState, action: AppAction.SetPlayerArchived): AppState {
    val player = state.player(action.playerId) ?: return state
    if (player.archived == action.archived) return state
    val updated = player.copy(archived = action.archived, updatedAt = action.timestamp)
    val draft = state.newGame
    return state.copy(
        players = state.players.map { if (it.id == player.id) updated else it },
        newGame = if (action.archived && draft != null) draft.copy(selectedPlayerIds = draft.selectedPlayerIds - player.id) else draft,
    )
}

internal fun AppState.playedTogether(firstId: String, secondId: String): Boolean =
    games.any { firstId in it.playerIds && secondId in it.playerIds }

private fun mergePlayers(state: AppState, action: AppAction.MergePlayers): AppState {
    val source = state.player(action.sourceId) ?: return state
    val target = state.player(action.targetId) ?: return state
    if (source.id == target.id || state.playedTogether(source.id, target.id)) return state
    val merged = target.copy(
        aliases = target.aliases + source.aliases + source.id - target.id,
        archived = target.archived && source.archived,
        createdAt = minOf(source.createdAt, target.createdAt),
        updatedAt = action.timestamp,
    )
    val players = (state.players - source - target + merged).inCanonicalOrder()
    return state.withPlayerIdsRemapped(mapOf(source.id to target.id)).copy(players = players)
}

private fun deletePlayer(state: AppState, action: AppAction.DeletePlayer): AppState {
    val player = state.player(action.playerId) ?: return state
    if (state.games.any { player.id in it.playerIds }) return state
    val draft = state.newGame
    return state.copy(
        players = state.players - player,
        deletedPlayers = state.deletedPlayers + (player.id to action.timestamp),
        newGame = draft?.copy(selectedPlayerIds = draft.selectedPlayerIds - player.id),
    )
}

/** Rewrites references to renamed or merged players in games and drafts. */
private fun AppState.withPlayerIdsRemapped(mapping: Map<String, String>): AppState {
    fun id(value: String) = mapping[value] ?: value
    fun <V> Map<String, V>.remapKeys() = entries.associate { (key, value) -> id(key) to value }
    val entry = roundEntry
    return copy(
        games = games.map { it.remapPlayers(mapping) },
        newGame = newGame?.let { it.copy(selectedPlayerIds = it.selectedPlayerIds.map(::id).distinct()) },
        roundEntry = entry?.copy(
            points = entry.points.remapKeys(),
            roundEnderId = entry.roundEnderId?.let(::id),
            bids = entry.bids.remapKeys(),
            tricks = entry.tricks.remapKeys(),
        ),
    )
}

// endregion

// region New game

private fun startNewGame(state: AppState, type: GameType, template: Game?): AppState {
    val activeIds = state.players.filter { !it.archived }.map { it.id }.toSet()
    val source = template ?: state.games.filter { it.type == type }.maxByOrNull { it.startedAt }
    val selected = source?.playerIds.orEmpty().filter { it in activeIds }.take(type.maxPlayers)
    // Keep a custom Biberbande round count (e.g. always 4 rounds) for the next game.
    val roundsOverride = source
        ?.takeIf { type == GameType.BIBERBANDE }
        ?.plannedRounds
        ?.takeIf { it != BiberbandeRules.defaultPlannedRounds(source.playerIds.size) }
    return state.copy(newGame = NewGameDraft(type, selected, roundsOverride))
}

private fun toggleNewGamePlayer(state: AppState, playerId: String): AppState {
    val draft = state.newGame ?: return state
    val selected = draft.selectedPlayerIds
    if (playerId in selected) return state.copy(newGame = draft.copy(selectedPlayerIds = selected - playerId))
    val player = state.player(playerId) ?: return state
    if (player.archived || selected.size >= draft.type.maxPlayers) return state
    return state.copy(newGame = draft.copy(selectedPlayerIds = selected + playerId))
}

private fun moveNewGamePlayer(state: AppState, fromIndex: Int, toIndex: Int): AppState {
    val draft = state.newGame ?: return state
    val seats = draft.selectedPlayerIds.toMutableList()
    if (fromIndex !in seats.indices || toIndex !in 0..seats.size) return state
    val moved = seats.removeAt(fromIndex)
    seats.add(if (toIndex > fromIndex) toIndex - 1 else toIndex, moved)
    if (seats == draft.selectedPlayerIds) return state
    return state.copy(newGame = draft.copy(selectedPlayerIds = seats))
}

private fun setNewGameRounds(state: AppState, rounds: Int): AppState {
    val draft = state.newGame ?: return state
    if (draft.type != GameType.BIBERBANDE) return state
    return state.copy(newGame = draft.copy(roundsOverride = rounds.coerceIn(1, BiberbandeRules.MAX_ROUNDS)))
}

internal fun NewGameDraft.plannedRounds(): Int? = when (type) {
    GameType.SKYJO -> null
    GameType.BIBERBANDE -> roundsOverride ?: BiberbandeRules.defaultPlannedRounds(selectedPlayerIds.size)
    GameType.WIZARD -> if (selectedPlayerIds.isEmpty()) null else WizardRules.roundsFor(selectedPlayerIds.size)
}

private fun createGame(state: AppState, action: AppAction.CreateGame): AppState {
    val draft = state.newGame ?: return state
    val seats = draft.selectedPlayerIds
    if (seats.size !in draft.type.minPlayers..draft.type.maxPlayers) return state
    if (state.games.any { it.id == action.gameId }) return state
    val game = Game(
        id = action.gameId,
        type = draft.type,
        playerIds = seats,
        plannedRounds = draft.plannedRounds(),
        startedAt = action.timestamp,
        updatedAt = action.timestamp,
    )
    return state.copy(games = (state.games + game).inCanonicalOrder(), newGame = null)
}

// endregion

// region Round entry

/** Whether a new round can be started: not finished, nothing half-entered, and rounds left. */
internal fun Game.canStartNewRound(): Boolean {
    if (isFinished) return false
    if (rounds.lastOrNull()?.let { !rules.isRoundComplete(it) } == true) return false
    val planned = plannedRounds
    return planned == null || rounds.size < planned
}

private fun beginRoundEntry(state: AppState, gameId: String, roundIndex: Int): AppState {
    val game = state.game(gameId) ?: return state
    val draft = when {
        roundIndex == game.rounds.size -> {
            if (!game.canStartNewRound()) return state
            val phase = if (game.type == GameType.WIZARD) EntryPhase.BIDS else EntryPhase.POINTS
            RoundEntryDraft(gameId, roundIndex, phase, isEditing = false)
        }
        roundIndex in game.rounds.indices -> when (val round = game.rounds[roundIndex]) {
            is PointsRound -> RoundEntryDraft(
                gameId, roundIndex, EntryPhase.POINTS, isEditing = true,
                points = round.points, roundEnderId = round.roundEnderId,
            )
            is WizardRound -> RoundEntryDraft(
                gameId, roundIndex, EntryPhase.TRICKS, isEditing = round.tricks != null,
                bids = round.bids, tricks = round.tricks.orEmpty(),
            )
        }
        else -> return state
    }
    return state.copy(roundEntry = draft)
}

private inline fun updateEntry(
    state: AppState,
    vararg phases: EntryPhase,
    update: (RoundEntryDraft) -> RoundEntryDraft,
): AppState {
    val draft = state.roundEntry ?: return state
    if (draft.phase !in phases) return state
    return state.copy(roundEntry = update(draft))
}

private fun setRoundEnder(state: AppState, playerId: String?): AppState {
    val draft = state.roundEntry ?: return state
    val game = state.game(draft.gameId) ?: return state
    if (game.type != GameType.SKYJO || draft.phase != EntryPhase.POINTS) return state
    if (playerId != null && playerId !in game.playerIds) return state
    return state.copy(roundEntry = draft.copy(roundEnderId = playerId))
}

private fun setWizardValue(state: AppState, playerId: String, value: Int, bids: Boolean): AppState {
    val draft = state.roundEntry ?: return state
    val game = state.game(draft.gameId) ?: return state
    if (playerId !in game.playerIds) return state
    val cards = draft.roundIndex + 1
    val clamped = value.coerceIn(0, cards)
    return when {
        bids && draft.phase in setOf(EntryPhase.BIDS, EntryPhase.TRICKS) ->
            state.copy(roundEntry = draft.copy(bids = draft.bids + (playerId to clamped)))
        !bids && draft.phase == EntryPhase.TRICKS ->
            state.copy(roundEntry = draft.copy(tricks = draft.tricks + (playerId to clamped)))
        else -> state
    }
}

private fun submitRoundEntry(state: AppState, action: AppAction.SubmitRoundEntry): AppState {
    val draft = state.roundEntry ?: return state
    val game = state.game(draft.gameId) ?: return state.copy(roundEntry = null)
    if (draft.roundIndex > game.rounds.size) return state
    val seats = game.playerIds
    val round: Round = when (draft.phase) {
        EntryPhase.POINTS -> {
            if (!seats.all { it in draft.points }) return state
            PointsRound(
                points = seats.associateWith { draft.points.getValue(it) },
                roundEnderId = draft.roundEnderId?.takeIf { game.type == GameType.SKYJO && it in seats },
            )
        }
        EntryPhase.BIDS -> {
            if (!seats.all { it in draft.bids }) return state
            WizardRound(bids = seats.associateWith { draft.bids.getValue(it) })
        }
        EntryPhase.TRICKS -> {
            if (!seats.all { it in draft.bids && it in draft.tricks }) return state
            val trickSum = seats.sumOf { draft.tricks.getValue(it) }
            if (trickSum != draft.roundIndex + 1 && !action.allowTrickMismatch) return state
            WizardRound(
                bids = seats.associateWith { draft.bids.getValue(it) },
                tricks = seats.associateWith { draft.tricks.getValue(it) },
            )
        }
    }
    val rounds = if (draft.roundIndex == game.rounds.size) {
        game.rounds + round
    } else {
        game.rounds.mapIndexed { index, existing -> if (index == draft.roundIndex) round else existing }
    }
    val updated = game.copy(rounds = rounds, updatedAt = action.timestamp).withRecomputedEnd(action.timestamp)
    return state.withGame(updated).copy(roundEntry = null)
}

// endregion

// region Running games

private fun AppState.withGame(updated: Game): AppState = copy(games = games.map { if (it.id == updated.id) updated else it })

private fun AppState.withoutEntryFor(gameId: String): AppState =
    if (roundEntry?.gameId == gameId) copy(roundEntry = null) else this

private fun deleteLastRound(state: AppState, action: AppAction.DeleteLastRound): AppState {
    val game = state.game(action.gameId) ?: return state
    if (game.rounds.isEmpty()) return state
    val updated = game
        .copy(rounds = game.rounds.dropLast(1), updatedAt = action.timestamp, endedAt = null, endedManually = false)
        .withRecomputedEnd(action.timestamp)
    return state.withGame(updated).withoutEntryFor(game.id)
}

private fun endGameNow(state: AppState, action: AppAction.EndGameNow): AppState {
    val game = state.game(action.gameId) ?: return state
    if (game.isFinished || game.completedRoundCount() == 0) return state
    val rounds = game.rounds.filter { game.rules.isRoundComplete(it) }
    val updated = game.copy(rounds = rounds, endedAt = action.timestamp, endedManually = true, updatedAt = action.timestamp)
    return state.withGame(updated).withoutEntryFor(game.id)
}

private fun playExtraRound(state: AppState, action: AppAction.PlayExtraRound): AppState {
    val game = state.game(action.gameId) ?: return state
    if (game.type != GameType.BIBERBANDE || !game.isFinished) return state
    val completed = game.completedRoundCount()
    if (completed >= BiberbandeRules.MAX_ROUNDS) return state
    val updated = game.copy(plannedRounds = completed + 1, endedAt = null, endedManually = false, updatedAt = action.timestamp)
    return state.withGame(updated)
}

private fun deleteGame(state: AppState, action: AppAction.DeleteGame): AppState {
    val game = state.game(action.gameId) ?: return state
    return state.copy(
        games = state.games - game,
        deletedGames = state.deletedGames + (game.id to action.timestamp),
    ).withoutEntryFor(game.id)
}

// endregion
