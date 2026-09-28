package com.pinguine.spiele.redux

import com.pinguine.spiele.persistence.GameRepository
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.sync.DecodeResult
import com.pinguine.spiele.sync.DumpCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.Middleware

/**
 * Loads the data file on [AppAction.LoadRequested] and saves the stored data whenever it changes.
 * Saves go through a conflated channel read by a single writer, so they stay in order and bursts of
 * changes collapse into one write. Nothing is written before a successful load, so a file that couldn't
 * be read is never overwritten.
 */
internal fun persistenceMiddleware(
    repository: GameRepository,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
): Middleware<AppState> = { store ->
    val pendingSaves = Channel<SavedData>(Channel.CONFLATED)
    scope.launch {
        for (data in pendingSaves) {
            try {
                withContext(ioDispatcher) { repository.save(data) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                store.dispatch(AppAction.PersistFailed(e.message ?: e.toString()))
            }
        }
    }

    fun load() = scope.launch {
        val result = try {
            AppAction.DataLoaded(withContext(ioDispatcher) { repository.load() } ?: SavedData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppAction.DataLoadFailed(e.message ?: e.toString())
        }
        store.dispatch(result)
    }

    fun startFresh(timestamp: Long) = scope.launch {
        val result = try {
            withContext(ioDispatcher) { repository.quarantine(timestamp) }
            AppAction.DataLoaded(SavedData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppAction.DataLoadFailed(e.message ?: e.toString())
        }
        store.dispatch(result)
    }

    val chain: (Dispatcher) -> (Any) -> Any = { next ->
        { action ->
            val before = store.state
            val result = next(action)
            val after = store.state
            when (action) {
                AppAction.LoadRequested -> load()
                is AppAction.StartFresh -> startFresh(action.timestamp)
                is AppAction.DataLoaded -> Unit
                else -> if (after.loadStatus == LoadStatus.READY && !after.hasSameDataAs(before)) {
                    pendingSaves.trySend(after.toSavedData())
                }
            }
            result
        }
    }
    chain
}

/** Decodes `.pinguine` dumps away from the main thread and feeds the result back as an action. */
internal fun importMiddleware(
    scope: CoroutineScope,
    decodeDispatcher: CoroutineDispatcher,
): Middleware<AppState> = { store ->
    val chain: (Dispatcher) -> (Any) -> Any = { next ->
        { action ->
            val result = next(action)
            when (action) {
                is AppAction.ImportDumpSelected -> scope.launch {
                    val decoded = withContext(decodeDispatcher) { DumpCodec.decode(action.json) }
                    store.dispatch(
                        when (decoded) {
                            is DecodeResult.Success -> AppAction.ImportDumpParsed(decoded.data)
                            is DecodeResult.Failure -> AppAction.ImportDumpFailed(decoded.error)
                        },
                    )
                }
                else -> Unit
            }
            result
        }
    }
    chain
}
