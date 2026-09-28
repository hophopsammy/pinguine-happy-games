package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.redux.AppAction
import com.pinguine.spiele.redux.EntryPhase
import com.pinguine.spiele.selectors.EntryIssue
import com.pinguine.spiele.selectors.NewGameIssue
import com.pinguine.spiele.selectors.NextStep
import com.pinguine.spiele.selectors.RowState
import com.pinguine.spiele.selectors.Selectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoreSheetSelectorTest {
    private val players = listOf(player("Anna"), player("Ben"), player("Carla"), player("David"))

    @Test
    fun skyjoShowsRunningTotalsDoublingAndAtLeastTenRows() {
        val state = readyState(
            players = players,
            games = listOf(
                game(
                    "s", GameType.SKYJO, listOf("anna", "ben"),
                    listOf(points("anna" to 10, "ben" to 5, ender = "anna"), points("anna" to 3, "ben" to 7)),
                ),
            ),
        )
        val sheet = assertNotNull(Selectors.scoreSheet(state, "s"))
        assertEquals(10, sheet.rows.size)
        val first = sheet.rows[0].cells[0]
        assertTrue(first.isDoubled)
        assertTrue(first.isRoundEnder)
        assertEquals(20, first.roundScore)
        assertEquals(23, sheet.rows[1].cells[0].runningTotal)
        assertEquals(RowState.NEXT, sheet.rows[2].state)
        assertEquals(NextStep.ENTER_POINTS, sheet.nextStep)
        assertEquals(listOf(23, 12), sheet.columns.map { it.total })
        assertTrue(sheet.columns[1].isLeader)
        assertEquals(100, sheet.endScore)
    }

    @Test
    fun wizardWithFourPlayersHasFifteenRowsAndShowsPendingBids() {
        val state = readyState(
            players = players,
            games = listOf(
                game(
                    "w", GameType.WIZARD, listOf("anna", "ben", "carla", "david"),
                    listOf(
                        wizard(mapOf("anna" to 1, "ben" to 0, "carla" to 0, "david" to 0), mapOf("anna" to 1, "ben" to 0, "carla" to 0, "david" to 0)),
                        wizard(mapOf("anna" to 0, "ben" to 1, "carla" to 1, "david" to 0)),
                    ),
                    plannedRounds = 15,
                ),
            ),
        )
        val sheet = assertNotNull(Selectors.scoreSheet(state, "w"))
        assertEquals(15, sheet.rows.size)
        assertEquals(RowState.DONE, sheet.rows[0].state)
        assertEquals(30, sheet.rows[0].cells[0].roundScore)
        assertTrue(sheet.rows[0].cells[0].madeBid)
        assertEquals(RowState.BIDS_PLACED, sheet.rows[1].state)
        assertTrue(sheet.rows[1].cells[1].hasBid)
        assertFalse(sheet.rows[1].cells[1].hasScore)
        assertEquals(NextStep.ENTER_TRICKS, sheet.nextStep)
        assertEquals(1, sheet.nextRoundIndex)
        assertEquals(1, sheet.rows[1].dealerSeat)
        assertEquals(RowState.PLANNED, sheet.rows[2].state)
    }

    @Test
    fun finishedBiberbandeMarksTheWinnerAndOffersAnotherRound() {
        val rounds = listOf(5 to 17, 14 to 7, 13 to 7, 16 to 13).map { (b, c) -> points("ben" to b, "carla" to c) }
        val state = readyState(players = players, games = listOf(game("b", GameType.BIBERBANDE, listOf("ben", "carla"), rounds, plannedRounds = 4)))
        val sheet = assertNotNull(Selectors.scoreSheet(state, "b"))
        assertEquals(4, sheet.rows.size)
        assertEquals(listOf(48, 44), sheet.columns.map { it.total })
        assertEquals(listOf(false, true), sheet.columns.map { it.isWinner })
        assertEquals(NextStep.FINISHED, sheet.nextStep)
        assertTrue(sheet.canPlayExtraRound)
    }
}

class EndedEarlySelectorTest {
    @Test
    fun aGameEndedEarlyShowsOnlyThePlayedRounds() {
        val running = readyState(
            players = listOf(player("Ben"), player("Carla")),
            games = listOf(game("b", GameType.BIBERBANDE, listOf("ben", "carla"), listOf(points("ben" to 5, "carla" to 17)), plannedRounds = 4)),
        )
        assertEquals(4, Selectors.scoreSheet(running, "b")!!.rows.size)
        val ended = running.reduce(AppAction.EndGameNow("b", 99))
        val sheet = assertNotNull(Selectors.scoreSheet(ended, "b"))
        assertEquals(1, sheet.rows.size)
        // Lowest total wins Biberbande: Ben's 5 beats Carla's 17.
        assertEquals(listOf(true, false), sheet.columns.map { it.isWinner })
    }
}

class EntrySelectorTest {
    private val players = listOf(player("Anna"), player("Ben"), player("Carla"))

    @Test
    fun wizardRowsFollowTheBiddingOrderAndPreviewScores() {
        val state = readyState(
            players = players,
            games = listOf(game("w", GameType.WIZARD, listOf("anna", "ben", "carla"), plannedRounds = 20)),
        ).reduce(
            AppAction.BeginRoundEntry("w", 0),
            AppAction.SetEntryBid("anna", 1),
            AppAction.SetEntryBid("ben", 0),
            AppAction.SetEntryBid("carla", 0),
            AppAction.SubmitRoundEntry(10, allowTrickMismatch = false),
            AppAction.BeginRoundEntry("w", 0),
            AppAction.SetEntryTricks("anna", 0),
            AppAction.SetEntryTricks("ben", 1),
            AppAction.SetEntryTricks("carla", 1),
        )
        val entry = assertNotNull(Selectors.roundEntry(state))
        assertEquals(EntryPhase.TRICKS, entry.phase)
        assertEquals(listOf("ben", "carla", "anna"), entry.rows.map { it.playerId })
        assertTrue(entry.rows.last().isDealer)
        assertTrue(entry.rows.first().isFirstBidder)
        assertEquals(listOf(-10, -10, -10), entry.rows.map { it.previewScore })
        assertEquals(listOf(EntryIssue.TRICKS_SUM_MISMATCH), entry.issues)
        assertTrue(entry.needsMismatchConfirmation)
        assertEquals(1, entry.cards)
    }

    @Test
    fun skyjoPreviewShowsTheDoubling() {
        val state = readyState(players = players, games = listOf(game("s", GameType.SKYJO, listOf("anna", "ben", "carla")))).reduce(
            AppAction.BeginRoundEntry("s", 0),
            AppAction.SetEntryPoints("anna", 9),
            AppAction.SetEntryPoints("ben", 4),
            AppAction.SetRoundEnder("anna"),
        )
        val entry = assertNotNull(Selectors.roundEntry(state))
        val anna = entry.rows.first { it.playerId == "anna" }
        assertTrue(anna.isDoubled)
        assertEquals(18, anna.previewScore)
        assertEquals(listOf(EntryIssue.MISSING_VALUES), entry.issues)
        assertFalse(entry.canSubmit)
    }
}

class StatisticsSelectorTest {
    private val players = listOf(player("Anna"), player("Ben"), player("Carla"))

    private val state = readyState(
        players = players,
        games = listOf(
            game("b1", GameType.BIBERBANDE, listOf("anna", "ben"), listOf(points("anna" to 3, "ben" to 3)), plannedRounds = 1, startedAt = 1, updatedAt = 11),
            game("b2", GameType.BIBERBANDE, listOf("anna", "carla"), listOf(points("anna" to 2, "carla" to 5)), plannedRounds = 1, startedAt = 2, updatedAt = 12),
            game("w", GameType.WIZARD, listOf("anna", "ben", "carla"), listOf(wizard(mapOf("anna" to 1, "ben" to 0, "carla" to 0), mapOf("anna" to 1, "ben" to 0, "carla" to 0))), plannedRounds = 1, startedAt = 3, updatedAt = 13),
            game("running", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 1, "ben" to 2)), startedAt = 4, updatedAt = 14),
        ),
    )

    @Test
    fun sharedWinsCountForEveryoneAndRunningGamesDont() {
        val stats = Selectors.statistics(state, filter = null)
        assertEquals(3, stats.gamesPlayed)
        // Anna and Ben tied in b1, so both get the win.
        assertEquals(listOf("anna" to 3, "ben" to 1, "carla" to 0), stats.leaderboard.map { it.playerId to it.wins })
        assertEquals(listOf(1, 2, 3), stats.leaderboard.map { it.rank })
        assertEquals(100, stats.leaderboard.first().winRatePercent)
    }

    @Test
    fun filterByGame() {
        val stats = Selectors.statistics(state, filter = GameType.BIBERBANDE)
        assertEquals(2, stats.gamesPlayed)
        assertEquals("anna", stats.leaderboard.first().playerId)
        assertEquals(2, stats.records.single().total)
    }

    @Test
    fun historyIsNewestFirst() {
        assertEquals(listOf("w", "b2", "b1"), Selectors.history(state).map { it.gameId })
    }

    @Test
    fun homeListsRunningGamesAndTopPlayers() {
        val home = Selectors.home(state)
        assertEquals(listOf("running"), home.inProgress.map { it.gameId })
        assertEquals("anna", home.topPlayers.first().playerId)
        assertEquals(listOf(0, 2, 1), home.tiles.map { it.finishedCount })
    }

    @Test
    fun newGameExplainsWhatIsMissing() {
        val setup = state.reduce(AppAction.StartNewGame(GameType.WIZARD))
        val full = assertNotNull(Selectors.gameSetup(setup))
        assertEquals(listOf("anna", "ben", "carla"), full.seats.map { it.id })
        assertEquals(20, full.plannedRounds)
        assertTrue(full.canStart)
        val two = assertNotNull(Selectors.gameSetup(setup.reduce(AppAction.ToggleNewGamePlayer("carla"))))
        assertEquals(NewGameIssue.TOO_FEW_PLAYERS, two.issue)
        assertFalse(two.canStart)
    }

    @Test
    fun importPreviewCountsChanges() {
        val incoming = data(
            players = players + player("David"),
            games = listOf(game("new", GameType.SKYJO, listOf("anna", "david"), startedAt = 9)),
        )
        val preview = assertNotNull(Selectors.importPreview(state.reduce(AppAction.ImportDumpParsed(incoming))))
        assertEquals(1, preview.report.newGames)
        assertEquals(listOf("David"), preview.report.newPlayers)
        assertEquals(3, preview.report.matchedPlayers)
        assertTrue(preview.report.hasChanges)
    }
}
