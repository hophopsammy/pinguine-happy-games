package com.pinguine.spiele

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.Player
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.Round
import com.pinguine.spiele.model.Usernames
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.redux.AppAction
import com.pinguine.spiele.redux.AppState
import com.pinguine.spiele.redux.appReducer
import com.pinguine.spiele.rules.withRecomputedEnd

internal fun player(
    name: String,
    updatedAt: Long = 1,
    aliases: Set<String> = emptySet(),
    archived: Boolean = false,
) = Player(
    id = Usernames.normalize(name),
    name = Usernames.clean(name),
    aliases = aliases,
    archived = archived,
    createdAt = 1,
    updatedAt = updatedAt,
)

internal fun game(
    id: String,
    type: GameType,
    players: List<String>,
    rounds: List<Round> = emptyList(),
    plannedRounds: Int? = null,
    startedAt: Long = 10,
    updatedAt: Long = 20,
) = Game(
    id = id,
    type = type,
    playerIds = players,
    plannedRounds = plannedRounds,
    rounds = rounds,
    startedAt = startedAt,
    updatedAt = updatedAt,
).withRecomputedEnd(updatedAt)

internal fun points(vararg entries: Pair<String, Int>, ender: String? = null) = PointsRound(entries.toMap(), ender)

internal fun wizard(bids: Map<String, Int>, tricks: Map<String, Int>? = null) = WizardRound(bids, tricks)

internal fun data(players: List<Player> = emptyList(), games: List<Game> = emptyList()) =
    SavedData(players = players.sortedBy { it.id }, games = games.sortedWith(compareBy({ it.startedAt }, { it.id })))

internal fun readyState(players: List<Player> = emptyList(), games: List<Game> = emptyList()): AppState =
    appReducer(AppState(), AppAction.DataLoaded(data(players, games)))

internal fun AppState.reduce(vararg actions: AppAction): AppState = actions.fold(this) { state, action -> appReducer(state, action) }
