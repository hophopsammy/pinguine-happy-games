package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.sync.DecodeResult
import com.pinguine.spiele.sync.DumpCodec
import com.pinguine.spiele.sync.ImportError
import kotlin.test.Test
import kotlin.test.assertEquals

class DumpCodecTest {
    private val sample = data(
        players = listOf(player("Anna", aliases = setOf("anni")), player("Ben")),
        games = listOf(
            game("s", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to -2, "ben" to 14, ender = "anna")), startedAt = 1),
            game("w", GameType.WIZARD, listOf("anna", "ben"), listOf(wizard(mapOf("anna" to 1, "ben" to 0), mapOf("anna" to 1, "ben" to 0))), plannedRounds = 30, startedAt = 2),
        ),
    ).copy(deletedGames = mapOf("old" to 5), exportedAt = 99)

    private fun failure(json: String) = (DumpCodec.decode(json) as DecodeResult.Failure).error

    @Test
    fun roundTrip() {
        assertEquals(DecodeResult.Success(sample), DumpCodec.decode(DumpCodec.encode(sample)))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = DumpCodec.encode(sample).replaceFirst("{", "{\"futureField\":{\"x\":1},")
        assertEquals(DecodeResult.Success(sample), DumpCodec.decode(json))
    }

    @Test
    fun newerSchemaIsRejected() {
        assertEquals(ImportError.NEWER_SCHEMA, failure("""{"schemaVersion":2,"players":[]}"""))
    }

    @Test
    fun malformedInputIsRejected() {
        assertEquals(ImportError.MALFORMED, failure("{not json"))
        assertEquals(ImportError.MALFORMED, failure("[1,2]"))
        assertEquals(ImportError.MALFORMED, failure("""{"players":"nope"}"""))
    }

    @Test
    fun nonNumericSchemaVersionIsRejectedRatherThanCrashing() {
        assertEquals(ImportError.MALFORMED, failure("""{"schemaVersion":{},"players":[]}"""))
        assertEquals(ImportError.MALFORMED, failure("""{"schemaVersion":[1],"players":[]}"""))
    }

    @Test
    fun deeplyNestedInputIsRejectedRatherThanCrashing() {
        val depth = 1_000
        val json = "{\"players\":" + "[".repeat(depth) + "]".repeat(depth) + "}"
        assertEquals(ImportError.MALFORMED, failure(json))
    }

    @Test
    fun inconsistentDataIsRejected() {
        assertEquals(ImportError.INCONSISTENT, failure(DumpCodec.encode(sample.copy(players = sample.players.take(1)))))
        assertEquals(ImportError.INCONSISTENT, failure(DumpCodec.encode(sample.copy(players = listOf(player("Anna").copy(id = "Anna"))))))
        assertEquals(ImportError.INCONSISTENT, failure(DumpCodec.encode(data(players = listOf(player("Anna"), player("Ben", aliases = setOf("anna")))))))
        val skyjoWithWizardRound = game("x", GameType.SKYJO, listOf("anna"), listOf(wizard(mapOf("anna" to 0))))
        assertEquals(ImportError.INCONSISTENT, failure(DumpCodec.encode(data(listOf(player("Anna")), listOf(skyjoWithWizardRound)))))
    }
}
