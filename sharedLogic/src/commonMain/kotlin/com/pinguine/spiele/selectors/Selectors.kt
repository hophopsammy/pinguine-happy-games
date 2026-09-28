package com.pinguine.spiele.selectors

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.redux.AppState
import com.pinguine.spiele.redux.EntryPhase
import com.pinguine.spiele.redux.MAX_POINTS
import com.pinguine.spiele.redux.MIN_POINTS
import com.pinguine.spiele.redux.UsernameIssue
import com.pinguine.spiele.redux.canStartNewRound
import com.pinguine.spiele.redux.game
import com.pinguine.spiele.redux.playedTogether
import com.pinguine.spiele.redux.plannedRounds
import com.pinguine.spiele.redux.toSavedData
import com.pinguine.spiele.redux.usernameIssue
import com.pinguine.spiele.rules.BiberbandeRules
import com.pinguine.spiele.rules.SkyjoRules
import com.pinguine.spiele.rules.WizardRules
import com.pinguine.spiele.rules.completedRoundCount
import com.pinguine.spiele.rules.countsForStatistics
import com.pinguine.spiele.rules.leaderIds
import com.pinguine.spiele.rules.rules
import com.pinguine.spiele.rules.standings
import com.pinguine.spiele.rules.winnerIds
import com.pinguine.spiele.sync.DataMerger

/** Turns the state into what each screen shows (`Selectors.shared.home(state:)` from Swift). */
object Selectors {
    private const val SKYJO_MIN_ROWS = 10
    private const val HOME_TOP_PLAYERS = 3

    fun home(state: AppState): HomeModel {
        val finished = state.games.filter { it.countsForStatistics() }
        val names = state.names()
        return HomeModel(
            tiles = GameType.entries.map { type ->
                GameTile(type, type.minPlayers, type.maxPlayers, finishedCount = finished.count { it.type == type })
            },
            inProgress = state.games.filter { !it.isFinished }.sortedByDescending { it.updatedAt }.map { game ->
                GameSummary(
                    gameId = game.id,
                    type = game.type,
                    playerNames = game.playerIds.map(names),
                    completedRounds = game.completedRoundCount(),
                    plannedRounds = game.plannedRounds ?: 0,
                    startedAt = game.startedAt,
                    updatedAt = game.updatedAt,
                    leaderNames = game.leaderIds().map(names),
                )
            },
            topPlayers = leaderboard(state, finished).take(HOME_TOP_PLAYERS),
            activePlayerCount = state.players.count { !it.archived },
        )
    }

    fun usernameIssue(state: AppState, name: String, editingPlayerId: String?): UsernameIssue? =
        state.usernameIssue(name, editingPlayerId)

    fun players(state: AppState): PlayersModel {
        val finished = state.games.filter { it.countsForStatistics() }
        val winners = finished.associate { it.id to it.winnerIds() }
        val rows = state.players.map { player ->
            PlayerRow(
                id = player.id,
                name = player.name,
                gamesPlayed = finished.count { player.id in it.playerIds },
                wins = winners.values.count { player.id in it },
                isArchived = player.archived,
                canDelete = state.games.none { player.id in it.playerIds },
                mergeTargets = state.players
                    .filter { it.id != player.id && !state.playedTogether(player.id, it.id) }
                    .sortedBy { it.name.lowercase() }
                    .map { PlayerRef(it.id, it.name) },
                formerNames = player.aliases.sorted(),
            )
        }.sortedBy { it.name.lowercase() }
        return PlayersModel(active = rows.filter { !it.isArchived }, archived = rows.filter { it.isArchived })
    }

    fun gameSetup(state: AppState): NewGameModel? {
        val draft = state.newGame ?: return null
        val type = draft.type
        val selected = draft.selectedPlayerIds
        val names = state.names()
        val issue = when {
            selected.size < type.minPlayers -> NewGameIssue.TOO_FEW_PLAYERS
            selected.size > type.maxPlayers -> NewGameIssue.TOO_MANY_PLAYERS
            else -> null
        }
        return NewGameModel(
            type = type,
            seats = selected.map { PlayerRef(it, names(it)) },
            choices = state.players.filter { !it.archived }.sortedBy { it.name.lowercase() }.map { player ->
                val seat = selected.indexOf(player.id)
                PlayerChoice(
                    id = player.id,
                    name = player.name,
                    isSelected = seat >= 0,
                    seatNumber = seat + 1,
                    canSelect = seat >= 0 || selected.size < type.maxPlayers,
                )
            },
            plannedRounds = draft.plannedRounds() ?: 0,
            roundsAdjustable = type == GameType.BIBERBANDE,
            minRounds = 1,
            maxRounds = BiberbandeRules.MAX_ROUNDS,
            minPlayers = type.minPlayers,
            maxPlayers = type.maxPlayers,
            issue = issue,
            canStart = issue == null,
        )
    }

    fun scoreSheet(state: AppState, gameId: String): ScoreSheetModel? {
        val game = state.game(gameId) ?: return null
        val names = state.names()
        val seats = game.playerIds
        val running = seats.associateWith { 0 }.toMutableMap()
        val rows = mutableListOf<SheetRow>()

        game.rounds.forEachIndexed { index, round ->
            val complete = game.rules.isRoundComplete(round)
            val scores = game.rules.roundScores(round, seats)
            val cells = seats.map { id ->
                val score = scores[id]
                if (score != null) running[id] = running.getValue(id) + score
                when (round) {
                    is PointsRound -> SheetCell(
                        playerId = id,
                        hasScore = true,
                        roundScore = score ?: 0,
                        runningTotal = running.getValue(id),
                        hasBid = false,
                        bid = 0,
                        tricks = 0,
                        madeBid = false,
                        isDoubled = game.type == GameType.SKYJO && SkyjoRules.isDoubled(round, id),
                        isRoundEnder = game.type == GameType.SKYJO && round.roundEnderId == id,
                    )
                    is WizardRound -> SheetCell(
                        playerId = id,
                        hasScore = complete,
                        roundScore = score ?: 0,
                        runningTotal = running.getValue(id),
                        hasBid = id in round.bids,
                        bid = round.bids[id] ?: 0,
                        tricks = round.tricks?.get(id) ?: 0,
                        madeBid = complete && round.bids[id] == round.tricks?.get(id),
                        isDoubled = false,
                        isRoundEnder = false,
                    )
                }
            }
            val rowState = if (complete) RowState.DONE else RowState.BIDS_PLACED
            rows += SheetRow(index, index + 1, rowState, cells, game.dealerSeat(index + 1), canEdit = true)
        }

        val canStart = game.canStartNewRound()
        val rowCount = when (val planned = game.plannedRounds) {
            null -> maxOf(SKYJO_MIN_ROWS, game.rounds.size + if (canStart) 1 else 0)
            // A game ended early shows only the rounds that were played.
            else -> if (game.isFinished) game.rounds.size else maxOf(planned, game.rounds.size)
        }
        for (index in game.rounds.size until rowCount) {
            val rowState = if (index == game.rounds.size && canStart) RowState.NEXT else RowState.PLANNED
            val cells = seats.map { id -> emptyCell(id, running.getValue(id)) }
            rows += SheetRow(index, index + 1, rowState, cells, game.dealerSeat(index + 1), canEdit = false)
        }

        val lastRound = game.rounds.lastOrNull()
        val nextStep = when {
            game.isFinished -> NextStep.FINISHED
            lastRound is WizardRound && lastRound.tricks == null -> NextStep.ENTER_TRICKS
            canStart && game.type == GameType.WIZARD -> NextStep.ENTER_BIDS
            canStart -> NextStep.ENTER_POINTS
            else -> NextStep.FINISHED
        }
        val completed = game.completedRoundCount()
        val standings = game.standings().associateBy { it.playerId }
        val leaders = game.leaderIds()
        return ScoreSheetModel(
            gameId = game.id,
            type = game.type,
            columns = seats.map { id ->
                val standing = standings.getValue(id)
                SheetColumn(
                    playerId = id,
                    name = names(id),
                    total = standing.total,
                    rank = standing.rank,
                    isLeader = id in leaders,
                    isWinner = game.isFinished && id in leaders,
                )
            },
            rows = rows,
            isFinished = game.isFinished,
            nextStep = nextStep,
            nextRoundIndex = if (nextStep == NextStep.ENTER_TRICKS) game.rounds.lastIndex else game.rounds.size,
            completedRounds = completed,
            plannedRounds = game.plannedRounds ?: 0,
            canPlayExtraRound = game.type == GameType.BIBERBANDE && game.isFinished && completed < BiberbandeRules.MAX_ROUNDS,
            canEndNow = !game.isFinished && completed > 0,
            canUndoLastRound = game.rounds.isNotEmpty(),
            endScore = if (game.type == GameType.SKYJO) SkyjoRules.END_SCORE else 0,
            startedAt = game.startedAt,
            endedAt = game.endedAt ?: 0,
        )
    }

    fun roundEntry(state: AppState): RoundEntryModel? {
        val draft = state.roundEntry ?: return null
        val game = state.game(draft.gameId) ?: return null
        val names = state.names()
        val seats = game.playerIds
        val roundNumber = draft.roundIndex + 1
        val isWizard = game.type == GameType.WIZARD
        val dealerSeat = game.dealerSeat(roundNumber)
        val firstBidderSeat = if (isWizard) WizardRules.firstBidderIndex(roundNumber, seats.size) else -1
        val order = if (isWizard) seats.indices.map { (firstBidderSeat + it) % seats.size } else seats.indices.toList()

        val totalsBefore = seats.associateWith { 0 }.toMutableMap()
        game.rounds.take(draft.roundIndex).forEach { round ->
            game.rules.roundScores(round, seats).forEach { (id, score) -> totalsBefore[id] = totalsBefore.getValue(id) + score }
        }
        val partialRound = PointsRound(draft.points, draft.roundEnderId)
        val preview: Map<String, Int> = when (draft.phase) {
            EntryPhase.POINTS -> draft.points.mapValues { (id, points) ->
                if (game.type == GameType.SKYJO && SkyjoRules.isDoubled(partialRound, id)) points * 2 else points
            }
            EntryPhase.BIDS -> emptyMap()
            EntryPhase.TRICKS -> seats.filter { it in draft.bids && it in draft.tricks }
                .associateWith { WizardRules.score(draft.bids.getValue(it), draft.tricks.getValue(it)) }
        }
        val values = when (draft.phase) {
            EntryPhase.POINTS -> draft.points
            EntryPhase.BIDS -> draft.bids
            EntryPhase.TRICKS -> draft.tricks
        }
        val (minValue, maxValue) = if (draft.phase == EntryPhase.POINTS) MIN_POINTS to MAX_POINTS else 0 to roundNumber
        val rows = order.map { seat ->
            val id = seats[seat]
            EntryRow(
                playerId = id,
                name = names(id),
                hasValue = id in values,
                value = values[id] ?: 0,
                minValue = minValue,
                maxValue = maxValue,
                hasBid = id in draft.bids,
                bid = draft.bids[id] ?: 0,
                hasPreview = id in preview,
                previewScore = preview[id] ?: 0,
                totalBefore = totalsBefore.getValue(id),
                isDoubled = game.type == GameType.SKYJO && SkyjoRules.isDoubled(partialRound, id),
                isRoundEnder = draft.roundEnderId == id,
                isDealer = seat == dealerSeat,
                isFirstBidder = seat == firstBidderSeat,
            )
        }

        val missing = when (draft.phase) {
            EntryPhase.POINTS -> seats.any { it !in draft.points }
            EntryPhase.BIDS -> seats.any { it !in draft.bids }
            EntryPhase.TRICKS -> seats.any { it !in draft.bids || it !in draft.tricks }
        }
        val trickSum = seats.sumOf { draft.tricks[it] ?: 0 }
        val mismatch = draft.phase == EntryPhase.TRICKS && !missing && trickSum != roundNumber
        val issues = buildList {
            if (missing) add(EntryIssue.MISSING_VALUES)
            if (mismatch) add(EntryIssue.TRICKS_SUM_MISMATCH)
        }
        return RoundEntryModel(
            gameId = game.id,
            type = game.type,
            roundIndex = draft.roundIndex,
            roundNumber = roundNumber,
            phase = draft.phase,
            isEditing = draft.isEditing,
            rows = rows,
            cards = if (isWizard) roundNumber else 0,
            bidSum = seats.sumOf { draft.bids[it] ?: 0 },
            trickSum = trickSum,
            roundEnderId = draft.roundEnderId,
            issues = issues,
            canSubmit = !missing,
            needsMismatchConfirmation = mismatch,
        )
    }

    fun statistics(state: AppState, filter: GameType?): StatisticsModel {
        val finished = state.games.filter { it.countsForStatistics() }
        val filtered = if (filter == null) finished else finished.filter { it.type == filter }
        val names = state.names()
        val records = (filter?.let { listOf(it) } ?: GameType.entries).mapNotNull { type ->
            val candidates = finished.filter { it.type == type }.map { game ->
                val best = game.standings().first().total
                game to best
            }
            val sign = if (type.lowestTotalWins) 1 else -1
            val (game, total) = candidates.minWithOrNull(compareBy({ sign * it.second }, { it.first.endedAt })) ?: return@mapNotNull null
            RecordRow(
                type = type,
                playerNames = game.winnerIds().map(names).sorted(),
                total = total,
                gameId = game.id,
                date = game.endedAt ?: game.startedAt,
            )
        }
        return StatisticsModel(
            filter = filter,
            gamesPlayed = filtered.size,
            perType = GameType.entries.map { type -> GameTypeCount(type, finished.count { it.type == type }) },
            leaderboard = leaderboard(state, filtered),
            records = records,
        )
    }

    fun history(state: AppState): List<HistoryRow> {
        val names = state.names()
        return state.games
            .filter { it.countsForStatistics() }
            .sortedByDescending { it.endedAt }
            .map { game ->
                HistoryRow(
                    gameId = game.id,
                    type = game.type,
                    endedAt = game.endedAt ?: game.startedAt,
                    playerNames = game.playerIds.map(names),
                    winnerNames = game.winnerIds().map(names).sorted(),
                    winningTotal = game.standings().first().total,
                    rounds = game.completedRoundCount(),
                )
            }
    }

    fun importPreview(state: AppState): ImportPreviewModel? {
        val incoming = state.pendingImport ?: return null
        return ImportPreviewModel(
            exportedAt = incoming.exportedAt ?: 0,
            incomingPlayers = incoming.players.size,
            incomingGames = incoming.games.size,
            report = DataMerger.merge(state.toSavedData(), incoming).report,
        )
    }

    private fun leaderboard(state: AppState, games: List<Game>): List<LeaderboardRow> {
        val played = mutableMapOf<String, Int>()
        val wins = mutableMapOf<String, Int>()
        games.forEach { game ->
            game.playerIds.forEach { played[it] = (played[it] ?: 0) + 1 }
            game.winnerIds().forEach { wins[it] = (wins[it] ?: 0) + 1 }
        }
        val names = state.names()
        val sorted = played.map { (id, count) ->
            val won = wins[id] ?: 0
            LeaderboardRow(id, names(id), won, count, winRatePercent = won * 100 / count, rank = 0)
        }.sortedWith(
            compareByDescending<LeaderboardRow> { it.wins }
                .thenByDescending { it.winRatePercent }
                .thenBy { it.name.lowercase() },
        )
        var previousWins = -1
        var previousRank = 0
        return sorted.mapIndexed { index, row ->
            val rank = if (row.wins == previousWins) previousRank else index + 1
            previousWins = row.wins
            previousRank = rank
            row.copy(rank = rank)
        }
    }

    private fun emptyCell(playerId: String, runningTotal: Int) = SheetCell(
        playerId = playerId,
        hasScore = false,
        roundScore = 0,
        runningTotal = runningTotal,
        hasBid = false,
        bid = 0,
        tricks = 0,
        madeBid = false,
        isDoubled = false,
        isRoundEnder = false,
    )

    private fun Game.dealerSeat(roundNumber: Int): Int =
        if (type == GameType.WIZARD) WizardRules.dealerIndex(roundNumber, playerIds.size) else -1

    private fun AppState.names(): (String) -> String {
        val byId = players.associate { it.id to it.name }
        return { id -> byId[id] ?: id }
    }
}
