package com.pinguine.spiele.selectors

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.redux.EntryPhase
import com.pinguine.spiele.sync.MergeReport

data class PlayerRef(val id: String, val name: String)

// Home

data class HomeModel(
    val tiles: List<GameTile>,
    val inProgress: List<GameSummary>,
    val topPlayers: List<LeaderboardRow>,
    val activePlayerCount: Int,
)

data class GameTile(val type: GameType, val minPlayers: Int, val maxPlayers: Int, val finishedCount: Int)

/** [plannedRounds] is 0 for open-ended games (Skyjo). */
data class GameSummary(
    val gameId: String,
    val type: GameType,
    val playerNames: List<String>,
    val completedRounds: Int,
    val plannedRounds: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val leaderNames: List<String>,
)

// Players

data class PlayersModel(val active: List<PlayerRow>, val archived: List<PlayerRow>)

data class PlayerRow(
    val id: String,
    val name: String,
    val gamesPlayed: Int,
    val wins: Int,
    val isArchived: Boolean,
    val canDelete: Boolean,
    val mergeTargets: List<PlayerRef>,
    val formerNames: List<String>,
)

// New game

enum class NewGameIssue { TOO_FEW_PLAYERS, TOO_MANY_PLAYERS }

/** [plannedRounds] is 0 when the game is open-ended or no player is picked yet. */
data class NewGameModel(
    val type: GameType,
    val seats: List<PlayerRef>,
    val choices: List<PlayerChoice>,
    val plannedRounds: Int,
    val roundsAdjustable: Boolean,
    val minRounds: Int,
    val maxRounds: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val issue: NewGameIssue?,
    val canStart: Boolean,
)

/** [seatNumber] is 1-based and 0 when the player isn't picked. */
data class PlayerChoice(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val seatNumber: Int,
    val canSelect: Boolean,
)

// Score sheet

enum class NextStep { ENTER_POINTS, ENTER_BIDS, ENTER_TRICKS, FINISHED }

/** DONE: scored. BIDS_PLACED: Wizard round being played. NEXT: the round to enter now. PLANNED: later. */
enum class RowState { DONE, BIDS_PLACED, NEXT, PLANNED }

data class ScoreSheetModel(
    val gameId: String,
    val type: GameType,
    val columns: List<SheetColumn>,
    val rows: List<SheetRow>,
    val isFinished: Boolean,
    val nextStep: NextStep,
    val nextRoundIndex: Int,
    val completedRounds: Int,
    val plannedRounds: Int,
    val canPlayExtraRound: Boolean,
    val canEndNow: Boolean,
    val canUndoLastRound: Boolean,
    val endScore: Int,
    val startedAt: Long,
    val endedAt: Long,
)

data class SheetColumn(
    val playerId: String,
    val name: String,
    val total: Int,
    val rank: Int,
    val isLeader: Boolean,
    val isWinner: Boolean,
)

/** [dealerSeat] is the Wizard dealer's column index, -1 for other games. */
data class SheetRow(
    val roundIndex: Int,
    val roundNumber: Int,
    val state: RowState,
    val cells: List<SheetCell>,
    val dealerSeat: Int,
    val canEdit: Boolean,
)

data class SheetCell(
    val playerId: String,
    val hasScore: Boolean,
    val roundScore: Int,
    val runningTotal: Int,
    val hasBid: Boolean,
    val bid: Int,
    val tricks: Int,
    val madeBid: Boolean,
    val isDoubled: Boolean,
    val isRoundEnder: Boolean,
)

// Round entry

enum class EntryIssue { MISSING_VALUES, TRICKS_SUM_MISMATCH }

/**
 * [cards] is the number of cards (and tricks) in a Wizard round, 0 otherwise. Rows are in bidding order
 * for Wizard (starting left of the dealer) and in seat order otherwise.
 */
data class RoundEntryModel(
    val gameId: String,
    val type: GameType,
    val roundIndex: Int,
    val roundNumber: Int,
    val phase: EntryPhase,
    val isEditing: Boolean,
    val rows: List<EntryRow>,
    val cards: Int,
    val bidSum: Int,
    val trickSum: Int,
    val roundEnderId: String?,
    val issues: List<EntryIssue>,
    val canSubmit: Boolean,
    val needsMismatchConfirmation: Boolean,
)

data class EntryRow(
    val playerId: String,
    val name: String,
    val hasValue: Boolean,
    val value: Int,
    val minValue: Int,
    val maxValue: Int,
    val hasBid: Boolean,
    val bid: Int,
    val hasPreview: Boolean,
    val previewScore: Int,
    val totalBefore: Int,
    val isDoubled: Boolean,
    val isRoundEnder: Boolean,
    val isDealer: Boolean,
    val isFirstBidder: Boolean,
)

// Statistics and history

data class StatisticsModel(
    val filter: GameType?,
    val gamesPlayed: Int,
    val perType: List<GameTypeCount>,
    val leaderboard: List<LeaderboardRow>,
    val records: List<RecordRow>,
)

data class GameTypeCount(val type: GameType, val count: Int)

data class LeaderboardRow(
    val playerId: String,
    val name: String,
    val wins: Int,
    val played: Int,
    val winRatePercent: Int,
    val rank: Int,
)

/** The best final score ever reached in a game type (lowest for Skyjo and Biberbande, highest for Wizard). */
data class RecordRow(
    val type: GameType,
    val playerNames: List<String>,
    val total: Int,
    val gameId: String,
    val date: Long,
)

data class HistoryRow(
    val gameId: String,
    val type: GameType,
    val endedAt: Long,
    val playerNames: List<String>,
    val winnerNames: List<String>,
    val winningTotal: Int,
    val rounds: Int,
)

// Import

/** [exportedAt] is 0 when the file doesn't say when it was exported. */
data class ImportPreviewModel(
    val exportedAt: Long,
    val incomingPlayers: Int,
    val incomingGames: Int,
    val report: MergeReport,
)
