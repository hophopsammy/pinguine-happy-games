package com.pinguine.spiele.sync

import com.pinguine.spiele.model.Game
import com.pinguine.spiele.model.Player
import com.pinguine.spiele.model.remapPlayers
import com.pinguine.spiele.persistence.SavedData
import com.pinguine.spiele.persistence.inCanonicalOrder

data class RenamedPlayer(val from: String, val to: String)

data class PlayerConflict(val firstName: String, val secondName: String)

/** What a merge changes, relative to the local data. Names are display names. */
data class MergeReport(
    val newGames: Int,
    val updatedGames: Int,
    val removedGames: Int,
    val newPlayers: List<String>,
    val matchedPlayers: Int,
    val renamedPlayers: List<RenamedPlayer>,
    val combinedPlayers: List<String>,
    val conflicts: List<PlayerConflict>,
    val hasChanges: Boolean,
)

internal data class MergeResult(val data: SavedData, val report: MergeReport)

/**
 * Merges data from another device (a dump or an iCloud snapshot) into the local data.
 *
 * - Players are the same person when their usernames or aliases overlap. The merged player keeps the
 *   username of the most recently updated record that no other record lists as a former name, so renames
 *   and merged duplicates win over stale copies. Players that sat at the same table are never merged.
 * - Games are matched by id; the most recently updated version wins.
 * - Deletions are remembered and win over any copy that wasn't changed after the deletion.
 *
 * Merging is idempotent and symmetric (A into B gives the same data as B into A), so devices converge
 * no matter in which order they exchange data.
 */
internal object DataMerger {
    fun merge(local: SavedData, incoming: SavedData): MergeResult {
        val identities = resolveIdentities(local, incoming)
        val mapping = identities.mapping

        val localGames = local.games.map { it.remapPlayers(mapping) }
        val incomingGames = incoming.games.map { it.remapPlayers(mapping) }
        val gamesById = LinkedHashMap<String, Game>()
        localGames.forEach { gamesById[it.id] = it }
        val fromIncoming = mutableSetOf<String>()
        incomingGames.forEach { game ->
            val existing = gamesById[game.id]
            if (existing == null || game.updatedAt > existing.updatedAt) {
                gamesById[game.id] = game
                fromIncoming += game.id
            }
        }

        val deletedGames = maxByKey(local.deletedGames, incoming.deletedGames)
        val games = gamesById.values.filter { game ->
            val deletedAt = deletedGames[game.id]
            deletedAt == null || deletedAt < game.updatedAt
        }

        val deletedPlayers = maxByKey(
            local.deletedPlayers.remapKeysKeepingLatest(mapping),
            incoming.deletedPlayers.remapKeysKeepingLatest(mapping),
        )
        val seated = games.flatMap { it.playerIds }.toSet()
        val players = identities.players.filter { player ->
            val deletedAt = deletedPlayers[player.id]
            deletedAt == null || deletedAt < player.updatedAt || player.id in seated
        }

        val merged = SavedData(
            schemaVersion = SavedData.CURRENT_SCHEMA_VERSION,
            exportedAt = local.exportedAt,
            players = players.inCanonicalOrder(),
            games = games.inCanonicalOrder(),
            deletedGames = deletedGames,
            deletedPlayers = deletedPlayers,
        )

        val localGameIds = local.games.map { it.id }.toSet()
        val mergedGameIds = merged.games.map { it.id }.toSet()
        val report = MergeReport(
            newGames = (mergedGameIds - localGameIds).size,
            updatedGames = mergedGameIds.count { it in localGameIds && it in fromIncoming },
            removedGames = (localGameIds - mergedGameIds).size,
            newPlayers = identities.newPlayerNames,
            matchedPlayers = identities.matchedCount,
            renamedPlayers = identities.renamed,
            combinedPlayers = identities.combined,
            conflicts = identities.conflicts,
            hasChanges = merged.withoutExportedAt() != local.inCanonicalForm().withoutExportedAt(),
        )
        return MergeResult(merged, report)
    }

    private class Identities(
        val players: List<Player>,
        val mapping: Map<String, String>,
        val newPlayerNames: List<String>,
        val matchedCount: Int,
        val renamed: List<RenamedPlayer>,
        val combined: List<String>,
        val conflicts: List<PlayerConflict>,
    )

    private class Record(val player: Player, val isLocal: Boolean)

    private fun resolveIdentities(local: SavedData, incoming: SavedData): Identities {
        val records = local.players.map { Record(it, isLocal = true) } + incoming.players.map { Record(it, isLocal = false) }
        val allGames = local.games + incoming.games

        val unionFind = UnionFind()
        records.forEach { record ->
            unionFind.find(record.player.id)
            record.player.aliases.forEach { unionFind.union(record.player.id, it) }
        }
        val linked = records.groupBy { unionFind.find(it.player.id) }.values

        val conflicts = mutableListOf<PlayerConflict>()
        val groups = linked.flatMap { group ->
            val memberIds = group.map { it.player.id }.toSet()
            val satTogether = memberIds.size > 1 && allGames.any { game -> game.playerIds.count { it in memberIds } > 1 }
            if (!satTogether) {
                listOf(group)
            } else {
                val split = group.groupBy { it.player.id }.values.toList()
                val names = split.map { it.first().player.name }.sorted()
                names.zipWithNext().forEach { (first, second) -> conflicts += PlayerConflict(first, second) }
                split
            }
        }

        val liveIds = groups.flatMap { group -> group.map { it.player.id } }.toSet()
        val players = mutableListOf<Player>()
        val mapping = mutableMapOf<String, String>()
        val newPlayerNames = mutableListOf<String>()
        val renamed = mutableListOf<RenamedPlayer>()
        val combined = mutableListOf<String>()
        var matchedCount = 0

        groups.forEach { group ->
            val ownIds = group.map { it.player.id }.toSet()
            val formerNames = group.flatMap { it.player.aliases }.toSet()
            val candidates = group.filter { it.player.id !in formerNames }.ifEmpty { group }
            val canonical = candidates
                .sortedWith(compareByDescending<Record> { it.player.updatedAt }.thenBy { it.player.id })
                .first().player
            // Names that belong to another live player (only possible after a conflict split) stay theirs.
            val foreignIds = liveIds - ownIds
            val aliases = (ownIds + formerNames) - canonical.id - foreignIds
            players += canonical.copy(
                aliases = aliases,
                createdAt = group.minOf { it.player.createdAt },
                updatedAt = group.maxOf { it.player.updatedAt },
            )
            (ownIds + formerNames - foreignIds).forEach { name -> if (name != canonical.id) mapping[name] = canonical.id }

            val localRecords = group.filter { it.isLocal }
            val localIds = localRecords.map { it.player.id }.toSet()
            when {
                localRecords.isEmpty() -> newPlayerNames += canonical.name
                group.any { !it.isLocal } -> matchedCount++
            }
            if (localIds.size > 1) {
                combined += canonical.name
            } else if (localIds.size == 1 && localIds.single() != canonical.id) {
                renamed += RenamedPlayer(from = localRecords.first().player.name, to = canonical.name)
            }
        }

        return Identities(
            players = players,
            mapping = mapping,
            newPlayerNames = newPlayerNames.sorted(),
            matchedCount = matchedCount,
            renamed = renamed.sortedBy { it.from },
            combined = combined.sorted(),
            conflicts = conflicts,
        )
    }

    private fun maxByKey(first: Map<String, Long>, second: Map<String, Long>): Map<String, Long> {
        if (second.isEmpty()) return first
        val result = first.toMutableMap()
        second.forEach { (key, value) -> result[key] = maxOf(result[key] ?: value, value) }
        return result
    }

    private fun Map<String, Long>.remapKeysKeepingLatest(mapping: Map<String, String>): Map<String, Long> {
        if (keys.none { it in mapping }) return this
        val result = mutableMapOf<String, Long>()
        forEach { (key, value) ->
            val id = mapping[key] ?: key
            result[id] = maxOf(result[id] ?: value, value)
        }
        return result
    }

    private fun SavedData.withoutExportedAt() = copy(exportedAt = null)

    private fun SavedData.inCanonicalForm() = copy(players = players.inCanonicalOrder(), games = games.inCanonicalOrder())

    private class UnionFind {
        private val parent = mutableMapOf<String, String>()

        fun find(name: String): String {
            val next = parent.getOrPut(name) { name }
            if (next == name) return name
            val root = find(next)
            parent[name] = root
            return root
        }

        fun union(first: String, second: String) {
            val a = find(first)
            val b = find(second)
            if (a != b) {
                if (a < b) parent[b] = a else parent[a] = b
            }
        }
    }
}
