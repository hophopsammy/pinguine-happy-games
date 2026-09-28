package com.pinguine.spiele

import com.pinguine.spiele.persistence.InMemoryGameRepository
import com.pinguine.spiele.persistence.KStoreGameRepository
import com.pinguine.spiele.persistence.SampleData
import com.pinguine.spiele.redux.AppStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path
import org.reduxkotlin.concurrent.NotificationContext
import org.reduxkotlin.concurrent.coalescingNotificationContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSUserDomainMask
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Builds the app's store for Swift: `AppStoreFactory.shared.create()`. */
object AppStoreFactory {
    fun create(): AppStore = AppStore.create(
        repository = KStoreGameRepository(Path(directory(NSApplicationSupportDirectory))),
        notificationContext = mainThreadNotifications,
        effectScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        ioDispatcher = Dispatchers.IO,
        decodeDispatcher = Dispatchers.Default,
        snapshotDirectory = Path(directory(NSCachesDirectory), "snapshots"),
    )

    /** A store filled with sample players and games, for SwiftUI previews. */
    fun preview(): AppStore = AppStore.create(
        repository = InMemoryGameRepository(SampleData.build()),
        notificationContext = mainThreadNotifications,
        effectScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        ioDispatcher = Dispatchers.Main,
        decodeDispatcher = Dispatchers.Main,
        snapshotDirectory = Path(directory(NSCachesDirectory), "preview-snapshots"),
    )

    // Store listeners (and with them all SwiftUI updates) always run on the main thread.
    private val mainThreadNotifications: NotificationContext = coalescingNotificationContext(
        isOnTargetThread = { NSThread.isMainThread },
        post = { block -> dispatch_async(dispatch_get_main_queue()) { block() } },
    )

    @OptIn(ExperimentalForeignApi::class)
    private fun directory(kind: NSSearchPathDirectory): String {
        val url = NSFileManager.defaultManager.URLForDirectory(
            directory = kind,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        return checkNotNull(url?.path) { "Missing directory $kind" }
    }
}
