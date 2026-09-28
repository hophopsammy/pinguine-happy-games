package com.pinguine.spiele.persistence

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.Player
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the app stores. The same shape is used for the local file, the `.pinguine` dump and the
 * iCloud snapshot. Deleted games and players are remembered (with the deletion time) so they don't come
 * back when data from another device is merged.
 */
@Serializable
data class SavedData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long? = null,
    val players: List<Player> = emptyList(),
    val games: List<Game> = emptyList(),
    val deletedGames: Map<String, Long> = emptyMap(),
    val deletedPlayers: Map<String, Long> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Canonical ordering so equal data compares equal regardless of how it was built. */
internal fun List<Player>.inCanonicalOrder(): List<Player> = sortedBy { it.id }

internal fun List<Game>.inCanonicalOrder(): List<Game> = sortedWith(compareBy({ it.startedAt }, { it.id }))
