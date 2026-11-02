// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.stslex.workeeper.core.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.stslex.workeeper.core.data.database.IosDatabaseTestSandbox.Companion.CURRENT_IDENTITY
import io.github.stslex.workeeper.core.data.database.IosDatabaseTestSandbox.Companion.SENTINEL_NAME
import io.github.stslex.workeeper.core.data.database.IosDatabaseTestSandbox.Companion.SENTINEL_UUID
import io.github.stslex.workeeper.core.data.database.di.DbCascadeBindingContainer
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IosAppDatabaseTest {

    @Test
    fun defaultLocationUsesApplicationSupport(): Unit = runBlocking(Dispatchers.IO) {
        val device = checkNotNull(testEnvironment("WORKEEPER_DATABASE_TEST_SIMULATOR"))
        assertEquals(device, testEnvironment("SIMULATOR_UDID"))
        assertTrue(NSHomeDirectory().contains("/$device/data"), "A newly provisioned simulator is required")
        val manager = NSFileManager.defaultManager
        val support = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        assertEquals(support.path, iosApplicationSupportDirectory().path)
        val supportPath = checkNotNull(support.path)
        assertTrue(supportPath.startsWith(NSHomeDirectory() + "/"))
        createTestDirectory(supportPath)
        val owned = "$supportPath/Workeeper"
        // Refuse any pre-existing directory before calling the default factory or deleting anything.
        assertFalse(manager.fileExistsAtPath(owned), "The default database directory is not exclusively owned")
        check(manager.createDirectoryAtPath(owned, false, null, null))
        try {
            val expected = TagEntity(name = "Default path ${Uuid.random()}")
            val database = buildIosAppDatabase()
            try {
                database.tagDao.insert(expected)
                assertEquals(expected, database.tagDao.findByName(expected.name))
                assertTrue(manager.fileExistsAtPath("$owned/${AppDatabase.NAME}"))
            } finally {
                database.close()
            }
        } finally {
            check(manager.removeItemAtPath(owned, null))
        }
    }

    @Test
    fun tagSurvivesCloseAndReopen(): Unit = iosDatabaseTest { sandbox ->
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(sandbox.support))
        val expected = TagEntity(uuid = Uuid.parse(SENTINEL_UUID), name = SENTINEL_NAME)
        sandbox.open { database ->
            database.tagDao.insert(expected)
            assertEquals(expected, database.tagDao.findByName(expected.name))
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(sandbox.file))
        }
        sandbox.open { database ->
            assertEquals(expected, database.tagDao.findByName(expected.name))
            assertNull(database.tagDao.findByName("Never written"))
            val rows = database.tagDao.observeAll().first()
            assertEquals(listOf(expected), rows)
            assertFalse(rows.any { it.uuid == Uuid.parse("99999999-9999-9999-9999-999999999999") })
        }
    }

    @Test
    fun upgradesV5ThroughV7(): Unit = iosDatabaseTest { sandbox ->
        sandbox.historical(5) { connection ->
            connection.seedSentinel()
            connection.execSQL(
                "INSERT INTO exercise_table " +
                    "(uuid, name, type, description, image_path, archived, created_at, archived_at, last_adhoc_sets) " +
                    "VALUES ('$EXERCISE', 'Historical exercise', 'WEIGHTED', NULL, NULL, 0, 1700000000000, NULL, NULL)",
            )
        }
        sandbox.observeBeforeOpen(5)
        sandbox.open { database ->
            assertEquals(SENTINEL_UUID, database.tagDao.findByName(SENTINEL_NAME)?.uuid.toString())
            val exercise = checkNotNull(database.exerciseDao.getById(Uuid.parse(EXERCISE)))
            assertEquals("Historical exercise", exercise.name)
            assertFalse(exercise.isAdhoc)
        }
        sandbox.assertCurrentIdentity()
        sandbox.open { database -> assertEquals(SENTINEL_NAME, database.tagDao.findByName(SENTINEL_NAME)?.name) }
    }

    @Test
    fun upgradesV6WithDuplicateTargets(): Unit = iosDatabaseTest { sandbox ->
        sandbox.historical(6) { connection ->
            connection.seedSentinel()
            connection.seedDuplicateTargets()
        }
        sandbox.observeBeforeOpen(6)
        sandbox.raw { connection ->
            assertEquals(2L, connection.readLong("SELECT COUNT(*) FROM set_table WHERE position = 0"))
        }
        sandbox.open { database -> assertEquals(SENTINEL_NAME, database.tagDao.findByName(SENTINEL_NAME)?.name) }
        sandbox.assertCurrentIdentity()
        sandbox.raw { connection ->
            val positions = mutableMapOf<String, Long>()
            connection.prepare(
                "SELECT uuid, position, reps, weight, type FROM set_table ORDER BY rowid",
            ).use { statement ->
                while (statement.step()) {
                    positions[statement.getText(0)] = statement.getLong(1)
                    assertEquals(8, statement.getInt(2))
                    assertEquals(100.0, statement.getDouble(3))
                    assertEquals("WORK", statement.getText(4))
                }
            }
            assertEquals(mapOf(SET_A to 0L, SET_B to 6L, SET_C to 5L), positions)
            assertEquals(3, positions.values.toSet().size)
            connection.prepare("PRAGMA index_list('set_table')").use { statement ->
                var found = false
                while (statement.step()) {
                    if (statement.getText(1) == "index_set_table_performed_exercise_uuid_position") {
                        assertEquals(1, statement.getInt(2))
                        found = true
                    }
                }
                assertTrue(found)
            }
        }
        sandbox.open { database -> assertEquals(SENTINEL_NAME, database.tagDao.findByName(SENTINEL_NAME)?.name) }
    }

    @Test
    fun unsupportedVersionKeepsOriginalRow(): Unit = iosDatabaseTest { sandbox ->
        sandbox.historical(6) { connection -> connection.seedSentinel() }
        sandbox.raw { connection -> connection.execSQL("PRAGMA user_version = 8") }
        sandbox.observeBeforeOpen(version = 8, schemaVersion = 6)
        val failure = runCatching {
            sandbox.open { database -> database.tagDao.findByName(SENTINEL_NAME) }
        }.exceptionOrNull()
        assertIs<IllegalStateException>(failure)
        assertTrue(failure.message.orEmpty().contains("migration", ignoreCase = true))
        assertTrue(failure.message.orEmpty().contains("8 to 7"), failure.message)
        sandbox.raw { connection ->
            assertEquals(8L, connection.readLong("PRAGMA user_version"))
            assertEquals(
                IosHistoricalSchemas.identities.getValue(6),
                connection.readText("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            )
            assertEquals(SENTINEL_NAME, connection.readText("SELECT name FROM tag_table WHERE uuid = '$SENTINEL_UUID'"))
        }
    }

    @Test
    fun unusableDirectoryFailsWithoutFallback(): Unit = iosDatabaseTest { sandbox ->
        val blocked = "${sandbox.owned}/blocked"
        writeTestFile(blocked, "original directory blocker")
        val failure = runCatching {
            buildIosAppDatabase(NSURL.fileURLWithPath(blocked, true)).close()
        }.exceptionOrNull()
        assertIs<IllegalStateException>(failure)
        assertEquals("original directory blocker", readTestFile(blocked))
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(sandbox.file))
    }

    @Test
    fun childWritesRollBackTogether(): Unit = iosDatabaseTest { sandbox ->
        sandbox.open { database ->
            val runner = DbCascadeBindingContainer.provideDbTransitionRunner(database, Dispatchers.IO)
            val control = TagEntity(name = "precommitted")
            database.tagDao.insert(control)
            val trigger = RollbackTrigger()
            val failure = runCatching {
                runner {
                    val first = async { database.tagDao.insert(TagEntity(name = "doomed-a")) }
                    val second = async { database.tagDao.insert(TagEntity(name = "doomed-b")) }
                    first.await()
                    second.await()
                    assertEquals(
                        listOf("doomed-a", "doomed-b"),
                        database.tagDao.searchByPrefix("doomed-").map { it.name },
                    )
                    throw trigger
                }
            }.exceptionOrNull()
            assertSame(trigger, failure)
            assertIs<RollbackTrigger>(failure)
            assertEquals(listOf(control), database.tagDao.observeAll().first())
            assertNull(database.tagDao.findByName("doomed-a"))
            assertNull(database.tagDao.findByName("doomed-b"))
        }
    }

    @Test
    fun activeTagFlowObservesCommittedWrite(): Unit = iosDatabaseTest { sandbox ->
        sandbox.open { database ->
            coroutineScope {
                val runner = DbCascadeBindingContainer.provideDbTransitionRunner(database, Dispatchers.IO)
                val emissions = Channel<List<TagEntity>>(Channel.UNLIMITED)
                val collector = launch { database.tagDao.observeAll().collect { emissions.send(it) } }
                try {
                    assertEquals(emptyList(), withTimeout(TIMEOUT_MS) { emissions.receive() })
                    val expected = TagEntity(name = "committed-live-tag")
                    runner.mutate { database.tagDao.insert(expected) }
                    withTimeout(TIMEOUT_MS) {
                        var rows = emissions.receive()
                        while (rows.none { it.uuid == expected.uuid }) rows = emissions.receive()
                        assertEquals(listOf(expected), rows)
                    }
                    assertTrue(collector.isActive)
                } finally {
                    collector.cancelAndJoin()
                    emissions.close()
                }
            }
        }
    }

    private class RollbackTrigger : Exception("Injected child-write rollback")

    private companion object {
        const val EXERCISE = "22222222-2222-2222-2222-222222222222"
        const val TRAINING = "33333333-3333-3333-3333-333333333333"
        const val SESSION = "44444444-4444-4444-4444-444444444444"
        const val PERFORMED = "55555555-5555-5555-5555-555555555555"
        const val SET_A = "aaaaaaaa-0000-4000-8000-000000000001"
        const val SET_B = "aaaaaaaa-0000-4000-8000-000000000002"
        const val SET_C = "aaaaaaaa-0000-4000-8000-000000000003"
        const val TIMEOUT_MS = 10_000L

        fun SQLiteConnection.seedSentinel() {
            execSQL("INSERT INTO tag_table(uuid, name) VALUES ('$SENTINEL_UUID', '$SENTINEL_NAME')")
        }

        fun SQLiteConnection.seedDuplicateTargets() {
            execSQL(
                "INSERT INTO training_table(uuid, name, description, is_adhoc, archived, created_at, archived_at) " +
                    "VALUES ('$TRAINING', 'Historical training', NULL, 0, 0, 1700000000000, NULL)",
            )
            execSQL(
                "INSERT INTO exercise_table(uuid, name, type, description, image_path, archived, created_at, " +
                    "archived_at, last_adhoc_sets, is_adhoc) " +
                    "VALUES ('$EXERCISE', 'Historical exercise', 'WEIGHTED', NULL, NULL, 0, " +
                    "1700000000000, NULL, NULL, 0)",
            )
            execSQL(
                "INSERT INTO session_table(uuid, training_uuid, state, started_at, finished_at) " +
                    "VALUES ('$SESSION', '$TRAINING', 'IN_PROGRESS', 1700000000000, NULL)",
            )
            execSQL(
                "INSERT INTO performed_exercise_table(uuid, session_uuid, exercise_uuid, position, skipped) " +
                    "VALUES ('$PERFORMED', '$SESSION', '$EXERCISE', 0, 0)",
            )
            for ((rowId, uuid, position) in listOf(Triple(10, SET_A, 0), Triple(20, SET_B, 0), Triple(30, SET_C, 5))) {
                execSQL(
                    "INSERT INTO set_table(rowid, uuid, performed_exercise_uuid, position, reps, weight, type) " +
                        "VALUES ($rowId, '$uuid', '$PERFORMED', $position, 8, 100.0, 'WORK')",
                )
            }
        }

        fun IosDatabaseTestSandbox.assertCurrentIdentity() = raw { connection ->
            assertEquals(7L, connection.readLong("PRAGMA user_version"))
            assertEquals(
                CURRENT_IDENTITY,
                connection.readText("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            )
        }
    }
}
