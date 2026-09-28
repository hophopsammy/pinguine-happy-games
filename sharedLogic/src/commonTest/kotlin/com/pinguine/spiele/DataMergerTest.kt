package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.redux.AppAction
import com.pinguine.spiele.selectors.Selectors
import com.pinguine.spiele.sync.DataMerger
import com.pinguine.spiele.sync.PlayerConflict
import com.pinguine.spiele.sync.RenamedPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataMergerTest {
    private fun merge(local: SavedData, incoming: SavedData) = DataMerger.merge(local, incoming)

    @Test
    fun mergingIntoAnEmptyDeviceRestoresEverything() {
        val backup = data(
            players = listOf(player("Anna"), player("Ben")),
            games = listOf(game("g", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 3, "ben" to 4)))),
        )
        val result = merge(SavedData(), backup)
        assertEquals(backup, result.data)
        assertEquals(1, result.report.newGames)
        assertEquals(listOf("Anna", "Ben"), result.report.newPlayers)
        assertTrue(result.report.hasChanges)
    }

    @Test
    fun mergingTheSameDataAgainChangesNothing() {
        val local = data(players = listOf(player("Anna")), games = listOf(game("a", GameType.SKYJO, listOf("anna"))))
        val incoming = data(players = listOf(player("Ben")), games = listOf(game("b", GameType.SKYJO, listOf("ben"), startedAt = 11)))
        val once = merge(local, incoming).data
        val twice = merge(once, incoming)
        assertEquals(once, twice.data)
        assertFalse(twice.report.hasChanges)
    }

    @Test
    fun bothDirectionsGiveTheSameData() {
        val a = data(
            players = listOf(player("Anna", updatedAt = 5), player("Ben")),
            games = listOf(game("g1", GameType.SKYJO, listOf("anna", "ben"), startedAt = 1, updatedAt = 30)),
        )
        val b = data(
            players = listOf(player("anna", updatedAt = 9), player("Ben"), player("Carla")),
            games = listOf(
                game("g1", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 1, "ben" to 2)), startedAt = 1, updatedAt = 40),
                game("g2", GameType.WIZARD, listOf("anna", "ben", "carla"), plannedRounds = 20, startedAt = 2),
            ),
        )
        assertEquals(merge(a, b).data, merge(b, a).data)
    }

    @Test
    fun theSameUsernameOnTwoDevicesIsOnePlayerAndStatsCombine() {
        val phone = data(
            players = listOf(player("Anna", updatedAt = 3), player("Ben")),
            games = listOf(game("p", GameType.BIBERBANDE, listOf("anna", "ben"), listOf(points("anna" to 1, "ben" to 9)), plannedRounds = 1, startedAt = 1)),
        )
        val tablet = data(
            players = listOf(player("ANNA ", updatedAt = 4), player("Ben")),
            games = listOf(game("t", GameType.BIBERBANDE, listOf("anna", "ben"), listOf(points("anna" to 2, "ben" to 9)), plannedRounds = 1, startedAt = 2)),
        )
        val merged = merge(phone, tablet)
        assertEquals(listOf("anna", "ben"), merged.data.players.map { it.id })
        assertEquals(2, merged.report.matchedPlayers)

        val state = readyState().reduce(AppAction.DataLoaded(merged.data))
        val anna = Selectors.statistics(state, filter = null).leaderboard.first()
        assertEquals("anna", anna.playerId)
        assertEquals(2, anna.wins)
        assertEquals(2, anna.played)
    }

    @Test
    fun theMostRecentlyUpdatedGameWins() {
        val running = game("g", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 50, "ben" to 1)), updatedAt = 10)
        val finished = game("g", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 50, "ben" to 1), points("anna" to 60, "ben" to 1)), updatedAt = 20)
        val players = listOf(player("Anna"), player("Ben"))
        val result = merge(data(players, listOf(running)), data(players, listOf(finished)))
        assertNotNull(result.data.games.single().endedAt)
        assertEquals(1, result.report.updatedGames)
        assertNotNull(merge(data(players, listOf(finished)), data(players, listOf(running))).data.games.single().endedAt)
    }

    @Test
    fun deletedGamesStayDeletedInBothDirections() {
        val players = listOf(player("Anna"))
        val g = game("g", GameType.SKYJO, listOf("anna"), updatedAt = 20)
        val deletedHere = data(players).copy(deletedGames = mapOf("g" to 30))
        val stillThere = data(players, listOf(g))

        val intoDeleted = merge(deletedHere, stillThere)
        assertTrue(intoDeleted.data.games.isEmpty())
        assertFalse(intoDeleted.report.hasChanges)

        val intoStale = merge(stillThere, deletedHere)
        assertTrue(intoStale.data.games.isEmpty())
        assertEquals(1, intoStale.report.removedGames)

        val editedAfterDeletion = data(players, listOf(g.copy(updatedAt = 40)))
        assertEquals(1, merge(deletedHere, editedAfterDeletion).data.games.size)
    }

    @Test
    fun renamesCarryOverInsteadOfCreatingDuplicates() {
        val renamedHere = data(
            players = listOf(player("Sammy", updatedAt = 50, aliases = setOf("sammi"))),
            games = listOf(game("a", GameType.SKYJO, listOf("sammy"), startedAt = 1)),
        )
        val old = data(
            players = listOf(player("Sammi", updatedAt = 5)),
            games = listOf(game("b", GameType.SKYJO, listOf("sammi"), startedAt = 2)),
        )
        val onOldDevice = merge(old, renamedHere)
        assertEquals(listOf("sammy"), onOldDevice.data.players.map { it.id })
        assertTrue(onOldDevice.data.games.all { it.playerIds == listOf("sammy") })
        assertEquals(listOf(RenamedPlayer(from = "Sammi", to = "Sammy")), onOldDevice.report.renamedPlayers)

        val backHere = merge(renamedHere, old)
        assertEquals(listOf("sammy"), backHere.data.players.map { it.id })
        assertEquals(onOldDevice.data, backHere.data)
    }

    @Test
    fun conflictingRenamesResolveTheSameWayEverywhere() {
        val a = data(players = listOf(player("Sam", updatedAt = 50, aliases = setOf("sammi"))))
        val b = data(players = listOf(player("Sammy", updatedAt = 60, aliases = setOf("sammi"))))
        val ab = merge(a, b).data
        assertEquals(ab, merge(b, a).data)
        val merged = ab.players.single()
        assertEquals("sammy", merged.id)
        assertEquals(setOf("sammi", "sam"), merged.aliases)
    }

    @Test
    fun mergedDuplicatesCarryOverToOtherDevices() {
        val merged = data(
            players = listOf(player("Mama", updatedAt = 50, aliases = setOf("mami")), player("Ben")),
            games = listOf(game("g1", GameType.SKYJO, listOf("mama", "ben"), startedAt = 1)),
        )
        val separate = data(
            players = listOf(player("Mama", updatedAt = 5), player("Mami", updatedAt = 70), player("Ben")),
            games = listOf(game("g2", GameType.SKYJO, listOf("mami", "ben"), startedAt = 2)),
        )
        val result = merge(separate, merged)
        assertEquals(listOf("ben", "mama"), result.data.players.map { it.id })
        assertTrue(result.data.games.all { "mama" in it.playerIds })
        assertEquals(listOf("Mama"), result.report.combinedPlayers)
    }

    @Test
    fun playersWhoSatTogetherAreNeverMerged() {
        val merged = data(players = listOf(player("A", updatedAt = 50, aliases = setOf("b"))))
        val together = data(
            players = listOf(player("A"), player("B")),
            games = listOf(game("g", GameType.SKYJO, listOf("a", "b"))),
        )
        val result = merge(together, merged)
        assertEquals(listOf("a", "b"), result.data.players.map { it.id })
        assertEquals(emptySet(), result.data.players.first { it.id == "a" }.aliases)
        assertEquals(listOf(PlayerConflict("A", "B")), result.report.conflicts)
    }

    @Test
    fun deletedPlayersStayDeletedUnlessSomeoneStillPlaysWithThem() {
        val deletedHere = data(players = listOf(player("Anna"))).copy(deletedPlayers = mapOf("tset" to 30))
        val stale = data(players = listOf(player("Anna"), player("Tset", updatedAt = 10)))
        assertEquals(listOf("anna"), merge(deletedHere, stale).data.players.map { it.id })

        val playedMeanwhile = data(
            players = listOf(player("Anna"), player("Tset", updatedAt = 10)),
            games = listOf(game("g", GameType.SKYJO, listOf("anna", "tset"))),
        )
        assertEquals(listOf("anna", "tset"), merge(deletedHere, playedMeanwhile).data.players.map { it.id })
    }

    @Test
    fun staleSnapshotsFromSeveralDevicesConverge() {
        val phone = data(players = listOf(player("Anna", updatedAt = 1)), games = listOf(game("p", GameType.SKYJO, listOf("anna"), startedAt = 1)))
        val tablet = data(players = listOf(player("Anna", updatedAt = 2), player("Ben")), games = listOf(game("t", GameType.SKYJO, listOf("anna", "ben"), startedAt = 2)))
        val laptop = data(players = listOf(player("Carla")), games = listOf(game("l", GameType.SKYJO, listOf("carla"), startedAt = 3)))
        val one = listOf(tablet, laptop).fold(phone) { acc, it -> merge(acc, it).data }
        val two = listOf(phone, tablet).fold(laptop) { acc, it -> merge(acc, it).data }
        assertEquals(one, two)
        assertEquals(listOf("p", "t", "l"), one.games.map { it.id })
        assertNull(one.exportedAt)
    }
}
