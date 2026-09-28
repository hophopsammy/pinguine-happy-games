package com.pinguine.spiele.redux

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.Player
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.sync.ImportError

enum class LoadStatus { LOADING, READY, FAILED }

enum class EntryPhase { POINTS, BIDS, TRICKS }

/** Players picked for a new game, in seat order. [roundsOverride] is only used by Biberbande. */
data class NewGameDraft(
    val type: GameType,
    val selectedPlayerIds: List<String>,
    val roundsOverride: Int? = null,
)

/** Values typed so far for a new round or a correction of an existing one. */
data class RoundEntryDraft(
    val gameId: String,
    val roundIndex: Int,
    val phase: EntryPhase,
    val isEditing: Boolean,
    val points: Map<String, Int> = emptyMap(),
    val roundEnderId: String? = null,
    val bids: Map<String, Int> = emptyMap(),
    val tricks: Map<String, Int> = emptyMap(),
)

/** The whole app state. [players], [games] and the deletion markers are the stored data; the rest is UI flow state. */
data class AppState(
    val loadStatus: LoadStatus = LoadStatus.LOADING,
    val loadError: String? = null,
    val players: List<Player> = emptyList(),
    val games: List<Game> = emptyList(),
    val deletedGames: Map<String, Long> = emptyMap(),
    val deletedPlayers: Map<String, Long> = emptyMap(),
    val newGame: NewGameDraft? = null,
    val roundEntry: RoundEntryDraft? = null,
    val pendingImport: SavedData? = null,
    val importError: ImportError? = null,
    val persistError: String? = null,
)

internal fun AppState.toSavedData(exportedAt: Long? = null) = SavedData(
    exportedAt = exportedAt,
    players = players,
    games = games,
    deletedGames = deletedGames,
    deletedPlayers = deletedPlayers,
)

internal fun AppState.hasSameDataAs(other: AppState): Boolean =
    players === other.players &&
        games === other.games &&
        deletedGames === other.deletedGames &&
        deletedPlayers === other.deletedPlayers

/** Replaces the stored data, keeping the existing instances for parts that didn't change. */
internal fun AppState.withData(data: SavedData): AppState {
    if (data.players == players && data.games == games &&
        data.deletedGames == deletedGames && data.deletedPlayers == deletedPlayers
    ) {
        return this
    }
    return copy(
        players = if (data.players == players) players else data.players,
        games = if (data.games == games) games else data.games,
        deletedGames = if (data.deletedGames == deletedGames) deletedGames else data.deletedGames,
        deletedPlayers = if (data.deletedPlayers == deletedPlayers) deletedPlayers else data.deletedPlayers,
    )
}

internal fun AppState.game(id: String): Game? = games.firstOrNull { it.id == id }

internal fun AppState.player(id: String): Player? = players.firstOrNull { it.id == id }
