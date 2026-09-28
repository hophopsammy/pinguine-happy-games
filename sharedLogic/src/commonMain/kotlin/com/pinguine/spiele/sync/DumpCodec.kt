package com.pinguine.spiele.sync

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.model.PointsRound
import com.pinguine.spiele.model.Usernames
import com.pinguine.spiele.model.WizardRound
import com.pinguine.spiele.persistence.AppJson
import com.pinguine.spiele.persistence.SavedData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

enum class ImportError { MALFORMED, NEWER_SCHEMA, INCONSISTENT }

internal sealed interface DecodeResult {
    data class Success(val data: SavedData) : DecodeResult
    data class Failure(val error: ImportError) : DecodeResult
}

/** Reads and writes `.pinguine` dumps and iCloud snapshots, rejecting anything that isn't safe to merge. */
internal object DumpCodec {
    fun encode(data: SavedData): String = AppJson.encodeToString(SavedData.serializer(), data)

    fun decode(json: String): DecodeResult {
        val element = try {
            AppJson.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return DecodeResult.Failure(ImportError.MALFORMED)
        }
        if (element !is JsonObject) return DecodeResult.Failure(ImportError.MALFORMED)
        val schemaVersion = try {
            element["schemaVersion"]?.jsonPrimitive?.int ?: SavedData.CURRENT_SCHEMA_VERSION
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Failure(ImportError.MALFORMED)
        }
        if (schemaVersion > SavedData.CURRENT_SCHEMA_VERSION) return DecodeResult.Failure(ImportError.NEWER_SCHEMA)
        val data = try {
            AppJson.decodeFromJsonElement(SavedData.serializer(), element)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            return DecodeResult.Failure(ImportError.MALFORMED)
        }
        return if (isConsistent(data)) DecodeResult.Success(data) else DecodeResult.Failure(ImportError.INCONSISTENT)
    }

    /** Usernames are unique and normalized, and every game only references known players. */
    fun isConsistent(data: SavedData): Boolean {
        val names = mutableSetOf<String>()
        for (player in data.players) {
            if (player.id.isEmpty() || player.id != Usernames.normalize(player.id)) return false
            if (!names.add(player.id)) return false
            for (alias in player.aliases) if (!names.add(alias)) return false
        }
        val playerIds = data.players.map { it.id }.toSet()
        val gameIds = mutableSetOf<String>()
        for (game in data.games) {
            if (!gameIds.add(game.id)) return false
            if (game.playerIds.isEmpty() || game.playerIds.toSet().size != game.playerIds.size) return false
            if (!playerIds.containsAll(game.playerIds)) return false
            val seats = game.playerIds.toSet()
            for (round in game.rounds) {
                val expectsWizard = game.type == GameType.WIZARD
                if ((round is WizardRound) != expectsWizard) return false
                val referenced = when (round) {
                    is PointsRound -> round.points.keys + setOfNotNull(round.roundEnderId)
                    is WizardRound -> round.bids.keys + round.tricks.orEmpty().keys
                }
                if (!seats.containsAll(referenced)) return false
            }
        }
        return true
    }
}
