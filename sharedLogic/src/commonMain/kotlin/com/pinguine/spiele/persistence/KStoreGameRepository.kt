package com.pinguine.spiele.persistence

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Stores [SavedData] in one JSON file. KStore writes to a temp file and moves it into place, so a crash
 * mid-write never leaves a half-written file behind. KStore's versioned store is deliberately not used:
 * its default migration silently replaces unreadable data with the default value.
 */
internal class KStoreGameRepository(directory: Path) : GameRepository {
    private val file = Path(directory, FILE_NAME)
    private val directory = directory
    private val store: KStore<SavedData> = storeOf(file = file, default = null, enableCache = false, json = AppJson)

    override suspend fun load(): SavedData? = store.get()

    override suspend fun save(data: SavedData) {
        if (!SystemFileSystem.exists(directory)) SystemFileSystem.createDirectories(directory)
        store.set(data)
    }

    override suspend fun quarantine(timestamp: Long) {
        if (SystemFileSystem.exists(file)) {
            SystemFileSystem.atomicMove(file, Path(directory, "pinguine-spiele.corrupt-$timestamp.json"))
        }
    }

    private companion object {
        const val FILE_NAME = "pinguine-spiele.json"
    }
}
