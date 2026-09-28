package com.pinguine.spiele.sync

import com.pinguine.spiele.persistence.SavedData

enum class CloudWarning { NEWER_SCHEMA_ON_OTHER_DEVICE, UNREADABLE_SNAPSHOT }

/** iCloud snapshots that were read and checked; apply them with `Actions.applyCloudSnapshots`. */
class CloudSnapshotBatch internal constructor(
    internal val snapshots: List<SavedData>,
    internal val warning: CloudWarning?,
)

/** Reads iCloud snapshots. Pure and thread-safe, so Swift can run it away from the main thread. */
object CloudSnapshotDecoder {
    fun decode(jsons: List<String>): CloudSnapshotBatch {
        val decoded = jsons.map(DumpCodec::decode)
        val failures = decoded.filterIsInstance<DecodeResult.Failure>()
        val warning = when {
            failures.any { it.error == ImportError.NEWER_SCHEMA } -> CloudWarning.NEWER_SCHEMA_ON_OTHER_DEVICE
            failures.isNotEmpty() -> CloudWarning.UNREADABLE_SNAPSHOT
            else -> null
        }
        return CloudSnapshotBatch(decoded.filterIsInstance<DecodeResult.Success>().map { it.data }, warning)
    }
}
