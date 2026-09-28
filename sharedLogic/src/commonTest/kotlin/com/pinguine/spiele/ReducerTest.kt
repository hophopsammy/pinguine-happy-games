package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.redux.AppAction
import com.pinguine.spiele.redux.AppState
import com.pinguine.spiele.redux.EntryPhase
import com.pinguine.spiele.redux.LoadStatus
import com.pinguine.spiele.redux.UsernameIssue
import com.pinguine.spiele.redux.appReducer
import com.pinguine.spiele.redux.game
import com.pinguine.spiele.redux.usernameIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerReducerTest {
    @Test
    fun addsPlayersWithNormalizedIds() {
        val state = readyState().reduce(AppAction.AddPlayer("  Anna   Maria ", 5, selectForNewGame = false))
        val anna = state.players.single()
        assertEquals("anna maria", anna.id)
        assertEquals("Anna Maria", anna.name)
    }

    @Test
    fun usernamesAreUniqueIgnoringCaseAndAliases() {
        val state = readyState(players = listOf(player("Anna"), player("Sammy", aliases = setOf("sammi"))))
        assertEquals(UsernameIssue.TAKEN, state.usernameIssue("ANNA ", editingPlayerId = null))
        assertEquals(UsernameIssue.TAKEN, state.usernameIssue("Sammi", editingPlayerId = null))
        assertEquals(UsernameIssue.EMPTY, state.usernameIssue("   ", editingPlayerId = null))
        assertSame(state, state.reduce(AppAction.AddPlayer("anna", 5, selectForNewGame = false)))
    }

    @Test
    fun renameRewritesGamesAndKeepsTheOldNameAsAlias() {
        val skyjo = game("g", GameType.SKYJO, listOf("sammi", "ben"), listOf(points("sammi" to 3, "ben" to 4, ender = "sammi")))
        val state = readyState(players = listOf(player("Sammi"), player("Ben")), games = listOf(skyjo))
            .reduce(AppAction.RenamePlayer("sammi", "Sammy", 50))
        val sammy = state.players.first { it.id == "sammy" }
        assertEquals(setOf("sammi"), sammy.aliases)
        val round = state.games.single().rounds.single() as PointsRound
        assertEquals(listOf("sammy", "ben"), state.games.single().playerIds)
        assertEquals("sammy", round.roundEnderId)
        assertEquals(setOf("sammy", "ben"), round.points.keys)
    }

    @Test
    fun capitalizationChangeKeepsTheId() {
        val state = readyState(players = listOf(player("anna"))).reduce(AppAction.RenamePlayer("anna", "Anna", 5))
        assertEquals("Anna", state.players.single().name)
        assertEquals(emptySet(), state.players.single().aliases)
    }

    @Test
    fun cannotRenameToSomeoneElsesName() {
        val state = readyState(players = listOf(player("Anna"), player("Ben")))
        assertSame(state, state.reduce(AppAction.RenamePlayer("ben", "anna", 5)))
    }

    @Test
    fun mergePlayersMovesGamesAndRemembersTheName() {
        val games = listOf(
            game("g1", GameType.SKYJO, listOf("mami", "ben"), startedAt = 1),
            game("g2", GameType.SKYJO, listOf("mama", "ben"), startedAt = 2),
        )
        val state = readyState(players = listOf(player("Mami"), player("Mama"), player("Ben")), games = games)
            .reduce(AppAction.MergePlayers(sourceId = "mami", targetId = "mama", timestamp = 50))
        assertEquals(listOf("ben", "mama"), state.players.map { it.id })
        assertEquals(setOf("mami"), state.players.first { it.id == "mama" }.aliases)
        assertTrue(state.games.all { "mama" in it.playerIds })
    }

    @Test
    fun playersWhoSatAtTheSameTableCannotBeMerged() {
        val state = readyState(
            players = listOf(player("Anna"), player("Ben")),
            games = listOf(game("g", GameType.SKYJO, listOf("anna", "ben"))),
        )
        assertSame(state, state.reduce(AppAction.MergePlayers("anna", "ben", 5)))
    }

    @Test
    fun onlyPlayersWithoutGamesCanBeDeletedAndDeletionIsRemembered() {
        val state = readyState(
            players = listOf(player("Anna"), player("Ben"), player("Carla")),
            games = listOf(game("g", GameType.SKYJO, listOf("anna", "ben"))),
        )
        assertSame(state, state.reduce(AppAction.DeletePlayer("anna", 5)))
        val deleted = state.reduce(AppAction.DeletePlayer("carla", 7))
        assertEquals(listOf("anna", "ben"), deleted.players.map { it.id })
        assertEquals(mapOf("carla" to 7L), deleted.deletedPlayers)
    }
}

class NewGameReducerTest {
    private val players = listOf(player("Anna"), player("Ben"), player("Carla"), player("David"))

    @Test
    fun wizardNeedsThreePlayersAndPlansSixtyDividedByPlayers() {
        val setup = readyState(players = players).reduce(
            AppAction.StartNewGame(GameType.WIZARD),
            AppAction.ToggleNewGamePlayer("anna"),
            AppAction.ToggleNewGamePlayer("ben"),
        )
        assertSame(setup, setup.reduce(AppAction.CreateGame("w", 100)))
        val created = setup.reduce(AppAction.ToggleNewGamePlayer("carla"), AppAction.ToggleNewGamePlayer("david"), AppAction.CreateGame("w", 100))
        val wizard = assertNotNull(created.game("w"))
        assertEquals(15, wizard.plannedRounds)
        assertEquals(listOf("anna", "ben", "carla", "david"), wizard.playerIds)
        assertNull(created.newGame)
    }

    @Test
    fun selectionRespectsTheMaximum() {
        val many = (1..9).map { player("P$it") }
        val state = many.fold(readyState(players = many).reduce(AppAction.StartNewGame(GameType.SKYJO))) { acc, p ->
            acc.reduce(AppAction.ToggleNewGamePlayer(p.id))
        }
        assertEquals(8, state.newGame!!.selectedPlayerIds.size)
    }

    @Test
    fun seatsCanBeReorderedLikeSwiftUiOnMove() {
        val state = readyState(players = players).reduce(
            AppAction.StartNewGame(GameType.SKYJO),
            AppAction.ToggleNewGamePlayer("anna"),
            AppAction.ToggleNewGamePlayer("ben"),
            AppAction.ToggleNewGamePlayer("carla"),
            AppAction.MoveNewGamePlayer(fromIndex = 0, toIndex = 3),
        )
        assertEquals(listOf("ben", "carla", "anna"), state.newGame!!.selectedPlayerIds)
    }

    @Test
    fun nextGamePreselectsTheLastPlayersAndKeepsCustomBiberbandeRounds() {
        val last = game("b", GameType.BIBERBANDE, listOf("ben", "carla"), plannedRounds = 4)
        val state = readyState(players = players, games = listOf(last)).reduce(AppAction.StartNewGame(GameType.BIBERBANDE))
        val draft = state.newGame!!
        assertEquals(listOf("ben", "carla"), draft.selectedPlayerIds)
        assertEquals(4, draft.roundsOverride)
        val created = state.reduce(AppAction.CreateGame("b2", 100))
        assertEquals(4, created.game("b2")!!.plannedRounds)
    }

    @Test
    fun biberbandeDefaultsToOneRoundPerPlayer() {
        val state = readyState(players = players).reduce(
            AppAction.StartNewGame(GameType.BIBERBANDE),
            AppAction.ToggleNewGamePlayer("anna"),
            AppAction.ToggleNewGamePlayer("ben"),
            AppAction.ToggleNewGamePlayer("carla"),
            AppAction.CreateGame("b", 100),
        )
        assertEquals(3, state.game("b")!!.plannedRounds)
    }

    @Test
    fun addingAPlayerFromTheSetupSelectsThem() {
        val state = readyState(players = players).reduce(
            AppAction.StartNewGame(GameType.SKYJO),
            AppAction.AddPlayer("Emil", 5, selectForNewGame = true),
        )
        assertEquals(listOf("emil"), state.newGame!!.selectedPlayerIds)
    }
}

class RoundEntryReducerTest {
    private val players = listOf(player("Anna"), player("Ben"), player("Carla"))

    private fun started(type: GameType, plannedRounds: Int? = null): AppState = readyState(
        players = players,
        games = listOf(game("g", type, listOf("anna", "ben", "carla"), plannedRounds = plannedRounds)),
    )

    @Test
    fun skyjoRoundNeedsAllPointsAndStoresTheRoundEnder() {
        val entering = started(GameType.SKYJO).reduce(
            AppAction.BeginRoundEntry("g", 0),
            AppAction.SetEntryPoints("anna", 10),
            AppAction.SetEntryPoints("ben", 4),
            AppAction.SetRoundEnder("anna"),
        )
        assertSame(entering, entering.reduce(AppAction.SubmitRoundEntry(100, allowTrickMismatch = false)))
        val saved = entering.reduce(AppAction.SetEntryPoints("carla", 12), AppAction.SubmitRoundEntry(100, allowTrickMismatch = false))
        val round = saved.game("g")!!.rounds.single() as PointsRound
        assertEquals(mapOf("anna" to 10, "ben" to 4, "carla" to 12), round.points)
        assertEquals("anna", round.roundEnderId)
        assertNull(saved.roundEntry)
        assertEquals(100, saved.game("g")!!.updatedAt)
    }

    @Test
    fun wizardBidsAreSavedBeforeTricks() {
        val bids = started(GameType.WIZARD, plannedRounds = 20).reduce(
            AppAction.BeginRoundEntry("g", 0),
            AppAction.SetEntryBid("anna", 1),
            AppAction.SetEntryBid("ben", 0),
            AppAction.SetEntryBid("carla", 5), // clamped to the single card of round 1
            AppAction.SubmitRoundEntry(100, allowTrickMismatch = false),
        )
        val placed = bids.game("g")!!.rounds.single() as WizardRound
        assertEquals(mapOf("anna" to 1, "ben" to 0, "carla" to 1), placed.bids)
        assertNull(placed.tricks)

        val entering = bids.reduce(
            AppAction.BeginRoundEntry("g", 0),
            AppAction.SetEntryTricks("anna", 1),
            AppAction.SetEntryTricks("ben", 0),
            AppAction.SetEntryTricks("carla", 1),
        )
        assertEquals(EntryPhase.TRICKS, entering.roundEntry!!.phase)
        // Two tricks in a one-card round needs confirmation.
        assertSame(entering, entering.reduce(AppAction.SubmitRoundEntry(200, allowTrickMismatch = false)))
        val fixed = entering.reduce(AppAction.SetEntryTricks("carla", 0), AppAction.SubmitRoundEntry(200, allowTrickMismatch = false))
        assertEquals(mapOf("anna" to 1, "ben" to 0, "carla" to 0), (fixed.game("g")!!.rounds.single() as WizardRound).tricks)
        val forced = entering.reduce(AppAction.SubmitRoundEntry(200, allowTrickMismatch = true))
        assertNotNull((forced.game("g")!!.rounds.single() as WizardRound).tricks)
    }

    @Test
    fun noNewRoundWhileBidsArePending() {
        val bids = started(GameType.WIZARD, plannedRounds = 20).reduce(
            AppAction.BeginRoundEntry("g", 0),
            AppAction.SetEntryBid("anna", 0),
            AppAction.SetEntryBid("ben", 0),
            AppAction.SetEntryBid("carla", 0),
            AppAction.SubmitRoundEntry(100, allowTrickMismatch = false),
        )
        assertSame(bids, bids.reduce(AppAction.BeginRoundEntry("g", 1)))
    }

    @Test
    fun correctingAPastRoundCanReopenAFinishedGame() {
        val finished = readyState(
            players = players,
            games = listOf(
                game("g", GameType.SKYJO, listOf("anna", "ben", "carla"), listOf(points("anna" to 60, "ben" to 1, "carla" to 1), points("anna" to 40, "ben" to 1, "carla" to 1))),
            ),
        )
        assertNotNull(finished.game("g")!!.endedAt)
        val corrected = finished.reduce(
            AppAction.BeginRoundEntry("g", 1),
            AppAction.SetEntryPoints("anna", 30),
            AppAction.SubmitRoundEntry(300, allowTrickMismatch = false),
        )
        assertNull(corrected.game("g")!!.endedAt)
    }

    @Test
    fun finishingTheLastPlannedRoundEndsTheGameAndAnExtraRoundReopensIt() {
        val oneRoundLeft = readyState(
            players = players,
            games = listOf(game("g", GameType.BIBERBANDE, listOf("anna", "ben"), listOf(points("anna" to 3, "ben" to 4)), plannedRounds = 2)),
        )
        val finished = oneRoundLeft.reduce(
            AppAction.BeginRoundEntry("g", 1),
            AppAction.SetEntryPoints("anna", 5),
            AppAction.SetEntryPoints("ben", 6),
            AppAction.SubmitRoundEntry(300, allowTrickMismatch = false),
        )
        assertEquals(300, finished.game("g")!!.endedAt)
        val extended = finished.reduce(AppAction.PlayExtraRound("g", 400))
        assertEquals(3, extended.game("g")!!.plannedRounds)
        assertNull(extended.game("g")!!.endedAt)
    }

    @Test
    fun undoLastRoundAndEndNow() {
        val running = readyState(
            players = players,
            games = listOf(game("g", GameType.WIZARD, listOf("anna", "ben", "carla"), listOf(wizard(mapOf("anna" to 0, "ben" to 0, "carla" to 0), mapOf("anna" to 0, "ben" to 1, "carla" to 0)), wizard(mapOf("anna" to 1, "ben" to 1, "carla" to 0))), plannedRounds = 20)),
        )
        assertEquals(1, running.reduce(AppAction.DeleteLastRound("g", 50)).game("g")!!.rounds.size)
        val ended = running.reduce(AppAction.EndGameNow("g", 60)).game("g")!!
        assertEquals(60, ended.endedAt)
        assertTrue(ended.endedManually)
        assertEquals(1, ended.rounds.size, "the half-entered round is dropped")
    }

    @Test
    fun deletingAGameRemembersTheDeletion() {
        val state = started(GameType.SKYJO).reduce(AppAction.DeleteGame("g", 70))
        assertTrue(state.games.isEmpty())
        assertEquals(mapOf("g" to 70L), state.deletedGames)
    }
}

class RevisionReducerTest {
    @Test
    fun localChangesBumpTheRevisionButLoadingAndCloudMergesDont() {
        val loaded = appReducer(AppState(), AppAction.DataLoaded(data(players = listOf(player("Anna")))))
        assertEquals(LoadStatus.READY, loaded.loadStatus)
        assertEquals(0, loaded.localRevision)
        val added = loaded.reduce(AppAction.AddPlayer("Ben", 5, selectForNewGame = false))
        assertEquals(1, added.localRevision)
        val merged = added.reduce(AppAction.CloudSnapshotsDecoded(listOf(data(players = listOf(player("Carla")))), warning = null))
        assertEquals(listOf("anna", "ben", "carla"), merged.players.map { it.id })
        assertEquals(1, merged.localRevision)
    }

    @Test
    fun aCloudMergeWithNothingNewReturnsTheSameState() {
        val state = readyState(players = listOf(player("Anna")))
        assertSame(state, state.reduce(AppAction.CloudSnapshotsDecoded(listOf(data(players = listOf(player("Anna")))), warning = null)))
    }

    @Test
    fun cloudDataArrivingBeforeTheLocalFileIsMergedAfterLoading() {
        val early = AppState().reduce(AppAction.CloudSnapshotsDecoded(listOf(data(players = listOf(player("Carla")))), warning = null))
        assertTrue(early.players.isEmpty())
        val loaded = early.reduce(AppAction.DataLoaded(data(players = listOf(player("Anna")))))
        assertEquals(listOf("anna", "carla"), loaded.players.map { it.id })
    }

    @Test
    fun importingADumpCountsAsALocalChange() {
        val state = readyState(players = listOf(player("Anna"))).reduce(
            AppAction.ImportDumpParsed(SavedData(players = listOf(player("Ben")))),
            AppAction.ConfirmImport(10),
        )
        assertEquals(listOf("anna", "ben"), state.players.map { it.id })
        assertEquals(1, state.localRevision)
        assertNull(state.pendingImport)
    }
}
