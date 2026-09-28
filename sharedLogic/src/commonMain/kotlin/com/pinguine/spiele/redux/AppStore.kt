package com.pinguine.spiele.redux

import com.pinguine.spiele.persistence.GameRepository
import com.pinguine.spiele.sync.DumpCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.createConcurrentStore

/** Cancels an [AppStore.observe] registration. */
class ObservationHandle internal constructor(private val unsubscribe: () -> Unit) {
    private var isCancelled = false

    fun cancel() {
        if (isCancelled) return
        isCancelled = true
        unsubscribe()
    }
}

/**
 * The only Redux type Swift sees: read [state], [dispatch] actions made by [Actions], and [observe]
 * changes (always delivered on the main thread on iOS).
 */
class AppStore internal constructor(
    private val store: Store<AppState>,
    private val snapshotDirectory: Path?,
) {
    val state: AppState get() = store.state

    fun dispatch(action: AppAction) {
        store.dispatch(action)
    }

    /** Calls [listener] now and after every change of the state. */
    fun observe(listener: (AppState) -> Unit): ObservationHandle {
        var last: AppState? = null
        val notify = {
            val current = store.state
            if (current !== last) {
                last = current
                listener(current)
            }
        }
        val unsubscribe = store.subscribe(notify)
        notify()
        return ObservationHandle(unsubscribe)
    }

    /**
     * Writes the stored data to a new file named [fileName] and returns its path. Used for the
     * `.pinguine` export; safe to call from any thread.
     */
    fun writeSnapshotFile(fileName: String): String {
        val root = checkNotNull(snapshotDirectory) { "No snapshot directory configured" }
        val now = currentTimeMillis()
        deleteOldSnapshots(root, now)
        val folder = Path(root, "$now-${newId().take(8)}")
        SystemFileSystem.createDirectories(folder)
        val file = Path(folder, fileName)
        val json = DumpCodec.encode(state.toSavedData(exportedAt = now))
        SystemFileSystem.sink(file).buffered().use { it.writeString(json) }
        return file.toString()
    }

    private fun deleteOldSnapshots(root: Path, now: Long) {
        if (!SystemFileSystem.exists(root)) return
        SystemFileSystem.list(root).forEach { folder ->
            val createdAt = folder.name.substringBefore('-').toLongOrNull() ?: return@forEach
            if (now - createdAt > SNAPSHOT_RETENTION_MILLIS) {
                SystemFileSystem.list(folder).forEach { SystemFileSystem.delete(it, mustExist = false) }
                SystemFileSystem.delete(folder, mustExist = false)
            }
        }
    }

    internal companion object {
        // Keep files long enough for the share sheet to finish reading them.
        private const val SNAPSHOT_RETENTION_MILLIS = 24L * 60 * 60 * 1000

        fun create(
            repository: GameRepository,
            notificationContext: NotificationContext,
            effectScope: CoroutineScope,
            ioDispatcher: CoroutineDispatcher,
            decodeDispatcher: CoroutineDispatcher,
            snapshotDirectory: Path?,
            initialState: AppState = AppState(),
        ): AppStore {
            val store = createConcurrentStore(
                reducer = rootReducer,
                preloadedState = initialState,
                notificationContext = notificationContext,
                enhancer = applyMiddleware(
                    persistenceMiddleware(repository, effectScope, ioDispatcher),
                    importMiddleware(effectScope, decodeDispatcher),
                ),
            )
            return AppStore(store, snapshotDirectory).also { it.dispatch(AppAction.LoadRequested) }
        }
    }
}
