package com.pinguine.spiele.persistence

interface GameRepository {
    /** The stored data, or null when nothing has been saved yet. Throws when the file can't be read. */
    suspend fun load(): SavedData?

    suspend fun save(data: SavedData)

    /** Moves an unreadable data file aside so the app can start fresh without losing it. */
    suspend fun quarantine(timestamp: Long)
}
