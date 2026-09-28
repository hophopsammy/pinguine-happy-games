package com.pinguine.spiele.redux

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.sync.ImportError

/** Everything that can happen in the app. Swift creates these through [Actions]. */
sealed interface AppAction {
    // Loading and saving
    data object LoadRequested : AppAction
    data class DataLoaded(val data: SavedData) : AppAction
    data class DataLoadFailed(val message: String) : AppAction
    data class StartFresh(val timestamp: Long) : AppAction
    data class PersistFailed(val message: String) : AppAction
    data object DismissErrors : AppAction

    // Players
    data class AddPlayer(val name: String, val timestamp: Long, val selectForNewGame: Boolean) : AppAction
    data class RenamePlayer(val playerId: String, val newName: String, val timestamp: Long) : AppAction
    data class SetPlayerArchived(val playerId: String, val archived: Boolean, val timestamp: Long) : AppAction
    data class MergePlayers(val sourceId: String, val targetId: String, val timestamp: Long) : AppAction
    data class DeletePlayer(val playerId: String, val timestamp: Long) : AppAction

    // New game setup
    data class StartNewGame(val type: GameType) : AppAction
    data class Rematch(val gameId: String) : AppAction
    data class ToggleNewGamePlayer(val playerId: String) : AppAction
    data class MoveNewGamePlayer(val fromIndex: Int, val toIndex: Int) : AppAction
    data class SetNewGameRounds(val rounds: Int) : AppAction
    data object CancelNewGame : AppAction
    data class CreateGame(val gameId: String, val timestamp: Long) : AppAction

    // Round entry
    data class BeginRoundEntry(val gameId: String, val roundIndex: Int) : AppAction
    data class SetEntryPoints(val playerId: String, val points: Int) : AppAction
    data class ClearEntryPoints(val playerId: String) : AppAction
    data class SetRoundEnder(val playerId: String?) : AppAction
    data class SetEntryBid(val playerId: String, val bid: Int) : AppAction
    data class SetEntryTricks(val playerId: String, val tricks: Int) : AppAction
    data class SubmitRoundEntry(val timestamp: Long, val allowTrickMismatch: Boolean) : AppAction
    data object CancelRoundEntry : AppAction

    // Running games
    data class DeleteLastRound(val gameId: String, val timestamp: Long) : AppAction
    data class EndGameNow(val gameId: String, val timestamp: Long) : AppAction
    data class PlayExtraRound(val gameId: String, val timestamp: Long) : AppAction
    data class DeleteGame(val gameId: String, val timestamp: Long) : AppAction

    // Dump import
    data class ImportDumpSelected(val json: String) : AppAction
    data class ImportDumpParsed(val data: SavedData) : AppAction
    data class ImportDumpFailed(val error: ImportError) : AppAction
    data class ConfirmImport(val timestamp: Long) : AppAction
    data object CancelImport : AppAction
}
