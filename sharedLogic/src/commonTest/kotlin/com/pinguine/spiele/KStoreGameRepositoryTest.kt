package com.pinguine.spiele

import com.pinguine.spiele.model.GameType
import com.pinguine.spiele.persistence.KStoreGameRepository
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KStoreGameRepositoryTest {
    private val directory = Path(SystemTemporaryDirectory, "pinguine-test-${Random.nextLong().toULong()}")
    private val file = Path(directory, "pinguine-spiele.json")

    @AfterTest
    fun cleanUp() {
        if (SystemFileSystem.exists(directory)) {
            SystemFileSystem.list(directory).forEach { SystemFileSystem.delete(it) }
            SystemFileSystem.delete(directory)
        }
    }

    private fun write(text: String) {
        SystemFileSystem.createDirectories(directory)
        SystemFileSystem.sink(file).buffered().use { it.writeString(text) }
    }

    private fun read(path: Path = file) = SystemFileSystem.source(path).buffered().use { it.readString() }

    @Test
    fun roundTrip() = runTest {
        val repository = KStoreGameRepository(directory)
        assertNull(repository.load())
        val saved = data(
            players = listOf(player("Anna"), player("Ben")),
            games = listOf(game("g", GameType.SKYJO, listOf("anna", "ben"), listOf(points("anna" to 1, "ben" to 2, ender = "ben")))),
        )
        repository.save(saved)
        assertEquals(saved, KStoreGameRepository(directory).load())
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() = runTest {
        write("""{"schemaVersion":1,"players":[{"id":"anna","name":"Anna","createdAt":1,"updatedAt":1,"color":"red"}],"theme":"dark"}""")
        assertEquals(listOf("anna"), KStoreGameRepository(directory).load()!!.players.map { it.id })
    }

    @Test
    fun anUnreadableFileFailsAndIsOnlyMovedAsideOnRequest() = runTest {
        write("{not json")
        val repository = KStoreGameRepository(directory)
        assertFailsWith<Exception> { repository.load() }
        assertEquals("{not json", read())

        repository.quarantine(timestamp = 42)
        assertFalse(SystemFileSystem.exists(file))
        val kept = Path(directory, "pinguine-spiele.corrupt-42.json")
        assertTrue(SystemFileSystem.exists(kept))
        assertEquals("{not json", read(kept))
    }
}
