package com.pinguine.spiele

import com.pinguine.spiele.persistence.GameRepository
import com.pinguine.spiele.persistence.InMemoryGameRepository
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.redux.Actions
import com.pinguine.spiele.redux.AppStore
import com.pinguine.spiele.redux.LoadStatus
import com.pinguine.spiele.sync.CloudSnapshotDecoder
import com.pinguine.spiele.sync.CloudWarning
import com.pinguine.spiele.sync.DumpCodec
import com.pinguine.spiele.sync.ImportError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.reduxkotlin.concurrent.NotificationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MiddlewareTest {
    // Effects must not run in backgroundScope itself: advanceUntilIdle() skips background work. Parenting
    // them to it still cancels the endless save loop when the test ends.
    private fun TestScope.store(repository: GameRepository): AppStore {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppStore.create(
            repository = repository,
            notificationContext = NotificationContext.Inline,
            effectScope = CoroutineScope(dispatcher + SupervisorJob(backgroundScope.coroutineContext.job)),
            ioDispatcher = dispatcher,
            decodeDispatcher = dispatcher,
            snapshotDirectory = null,
        )
    }

    @Test
    fun loadsOnStartAndSavesOnlyAfterChanges() = runTest {
        val repository = InMemoryGameRepository(data(players = listOf(player("Anna"))))
        val store = store(repository)
        advanceUntilIdle()
        assertEquals(LoadStatus.READY, store.state.loadStatus)
        assertEquals(listOf("anna"), store.state.players.map { it.id })
        assertEquals(0, repository.saveCount)

        store.dispatch(Actions.addPlayer("Ben"))
        store.dispatch(Actions.addPlayer("Carla"))
        advanceUntilIdle()
        assertEquals(listOf("anna", "ben", "carla"), repository.data!!.players.map { it.id })

        val saves = repository.saveCount
        store.dispatch(Actions.addPlayer("anna")) // taken: nothing changes
        store.dispatch(Actions.importDump(DumpCodec.encode(data(players = listOf(player("Anna"))))))
        advanceUntilIdle()
        store.dispatch(Actions.confirmImport())
        advanceUntilIdle()
        assertEquals(saves, repository.saveCount)
    }

    @Test
    fun nothingIsSavedBeforeTheFileWasRead() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = object : GameRepository {
            var saved: SavedData? = null
            override suspend fun load(): SavedData? {
                release.await()
                return data(players = listOf(player("Anna")))
            }
            override suspend fun save(data: SavedData) { saved = data }
            override suspend fun quarantine(timestamp: Long) = Unit
        }
        val store = store(repository)
        store.dispatch(Actions.addPlayer("Ben"))
        advanceUntilIdle()
        assertNull(repository.saved)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("anna"), store.state.players.map { it.id })
        assertNull(repository.saved)
    }

    @Test
    fun unreadableFileIsKeptAsideWhenStartingFresh() = runTest {
        var quarantined = false
        val repository = object : GameRepository {
            override suspend fun load(): SavedData? = throw IllegalStateException("corrupt")
            override suspend fun save(data: SavedData) = error("must not be called")
            override suspend fun quarantine(timestamp: Long) { quarantined = true }
        }
        val store = store(repository)
        advanceUntilIdle()
        assertEquals(LoadStatus.FAILED, store.state.loadStatus)
        store.dispatch(Actions.startFresh())
        advanceUntilIdle()
        assertEquals(true, quarantined)
        assertEquals(LoadStatus.READY, store.state.loadStatus)
    }

    @Test
    fun saveFailuresAreReported() = runTest {
        val repository = object : GameRepository {
            override suspend fun load(): SavedData? = null
            override suspend fun save(data: SavedData) = throw IllegalStateException("disk full")
            override suspend fun quarantine(timestamp: Long) = Unit
        }
        val store = store(repository)
        advanceUntilIdle()
        store.dispatch(Actions.addPlayer("Anna"))
        advanceUntilIdle()
        assertEquals("disk full", store.state.persistError)
    }

    @Test
    fun dumpImportShowsAPreviewAndRejectsBadFiles() = runTest {
        val store = store(InMemoryGameRepository())
        advanceUntilIdle()
        store.dispatch(Actions.importDump("{broken"))
        advanceUntilIdle()
        assertEquals(ImportError.MALFORMED, store.state.importError)

        store.dispatch(Actions.importDump(DumpCodec.encode(data(players = listOf(player("Ben"))))))
        advanceUntilIdle()
        assertNotNull(store.state.pendingImport)
        store.dispatch(Actions.confirmImport())
        assertEquals(listOf("ben"), store.state.players.map { it.id })
    }

    @Test
    fun cloudSnapshotsAreMergedAndSavedWithoutCountingAsLocalChanges() = runTest {
        val repository = InMemoryGameRepository(data(players = listOf(player("Anna"))))
        val store = store(repository)
        advanceUntilIdle()
        val newer = """{"schemaVersion":99}"""
        val batch = CloudSnapshotDecoder.decode(listOf(DumpCodec.encode(data(players = listOf(player("Ben")))), newer))
        store.dispatch(Actions.applyCloudSnapshots(batch))
        // The merge is done when dispatch returns, so iCloud can upload right after it.
        assertEquals(listOf("anna", "ben"), store.state.players.map { it.id })
        advanceUntilIdle()
        assertEquals(0, store.state.localRevision)
        assertEquals(CloudWarning.NEWER_SCHEMA_ON_OTHER_DEVICE, store.state.cloudWarning)
        assertEquals(listOf("anna", "ben"), repository.data!!.players.map { it.id })
    }

    @Test
    fun observersOnlyHearAboutRealChanges() = runTest {
        val store = store(InMemoryGameRepository())
        advanceUntilIdle()
        val seen = mutableListOf<Int>()
        val handle = store.observe { seen += it.players.size }
        store.dispatch(Actions.addPlayer("Anna"))
        store.dispatch(Actions.addPlayer("anna")) // no-op
        handle.cancel()
        store.dispatch(Actions.addPlayer("Ben"))
        assertEquals(listOf(0, 1), seen)
    }
}
