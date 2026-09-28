package com.pinguine.spiele.redux

import com.pinguine.spiele.model.GameType

/**
 * Action creators for Swift (`Actions.shared.addPlayer(name:)`). They stamp ids and timestamps so the
 * reducers stay pure.
 */
object Actions {
    fun load(): AppAction = AppAction.LoadRequested
    fun startFresh(): AppAction = AppAction.StartFresh(currentTimeMillis())
    fun dismissErrors(): AppAction = AppAction.DismissErrors

    fun addPlayer(name: String): AppAction = AppAction.AddPlayer(name, currentTimeMillis(), selectForNewGame = false)
    fun addPlayerToNewGame(name: String): AppAction = AppAction.AddPlayer(name, currentTimeMillis(), selectForNewGame = true)
    fun renamePlayer(playerId: String, newName: String): AppAction = AppAction.RenamePlayer(playerId, newName, currentTimeMillis())
    fun setPlayerArchived(playerId: String, archived: Boolean): AppAction =
        AppAction.SetPlayerArchived(playerId, archived, currentTimeMillis())
    fun mergePlayers(sourceId: String, targetId: String): AppAction =
        AppAction.MergePlayers(sourceId, targetId, currentTimeMillis())
    fun deletePlayer(playerId: String): AppAction = AppAction.DeletePlayer(playerId, currentTimeMillis())

    fun startNewGame(type: GameType): AppAction = AppAction.StartNewGame(type)
    fun rematch(gameId: String): AppAction = AppAction.Rematch(gameId)
    fun toggleNewGamePlayer(playerId: String): AppAction = AppAction.ToggleNewGamePlayer(playerId)
    /** Same meaning as SwiftUI's `onMove`: [toIndex] is the position before which the player is inserted. */
    fun moveNewGamePlayer(fromIndex: Int, toIndex: Int): AppAction = AppAction.MoveNewGamePlayer(fromIndex, toIndex)
    fun setNewGameRounds(rounds: Int): AppAction = AppAction.SetNewGameRounds(rounds)
    fun cancelNewGame(): AppAction = AppAction.CancelNewGame
    fun createGame(): AppAction.CreateGame = AppAction.CreateGame(newId(), currentTimeMillis())

    fun beginRoundEntry(gameId: String, roundIndex: Int): AppAction = AppAction.BeginRoundEntry(gameId, roundIndex)
    fun setEntryPoints(playerId: String, points: Int): AppAction = AppAction.SetEntryPoints(playerId, points)
    fun clearEntryPoints(playerId: String): AppAction = AppAction.ClearEntryPoints(playerId)
    fun setRoundEnder(playerId: String?): AppAction = AppAction.SetRoundEnder(playerId)
    fun setEntryBid(playerId: String, bid: Int): AppAction = AppAction.SetEntryBid(playerId, bid)
    fun setEntryTricks(playerId: String, tricks: Int): AppAction = AppAction.SetEntryTricks(playerId, tricks)
    fun submitRoundEntry(allowTrickMismatch: Boolean): AppAction =
        AppAction.SubmitRoundEntry(currentTimeMillis(), allowTrickMismatch)
    fun cancelRoundEntry(): AppAction = AppAction.CancelRoundEntry

    fun deleteLastRound(gameId: String): AppAction = AppAction.DeleteLastRound(gameId, currentTimeMillis())
    fun endGameNow(gameId: String): AppAction = AppAction.EndGameNow(gameId, currentTimeMillis())
    fun playExtraRound(gameId: String): AppAction = AppAction.PlayExtraRound(gameId, currentTimeMillis())
    fun deleteGame(gameId: String): AppAction = AppAction.DeleteGame(gameId, currentTimeMillis())

    fun importDump(json: String): AppAction = AppAction.ImportDumpSelected(json)
    fun confirmImport(): AppAction = AppAction.ConfirmImport(currentTimeMillis())
    fun cancelImport(): AppAction = AppAction.CancelImport
}
