package com.pinguine.spiele.persistence

/** Keeps data in memory; used for SwiftUI previews and tests. */
internal class InMemoryGameRepository(initial: SavedData? = null) : GameRepository {
    var data: SavedData? = initial
        private set
    var saveCount = 0
        private set

    override suspend fun load(): SavedData? = data

    override suspend fun save(data: SavedData) {
        this.data = data
        saveCount++
    }

    override suspend fun quarantine(timestamp: Long) {
        data = null
    }
}
