// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.data.database.migration.Migration6
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

/**
 * Migration test fixture for [AppDatabase].
 *
 * Each test seeds a v(N) DB through raw SQL, runs the matching `Migration` object via
 * [MigrationTestHelper.runMigrationsAndValidate], and asserts the resulting v(N+1) DB
 * has the expected shape and data. Room 3 removed the `validateDroppedTables` argument
 * from `runMigrationsAndValidate`; a device probe (a stale unregistered table injected at
 * v5) confirmed Room 3's `runMigrationsAndValidate` does NOT validate dropped/extra tables
 * by default — it passes. So [migrate5to6_validatesNoUnregisteredTablesSurvive]'s explicit
 * `sqlite_master` assertion is the ONLY guard for unregistered-table drift, not
 * belt-and-braces. (Column / index / FK drift is still caught by `runMigrationsAndValidate`'s
 * own schema validation against `6.json` — see that test's KDoc.)
 */
@Regression
internal class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB),
        BundledSQLiteDriver(),
        AppDatabase::class,
    )

    // Room 3's File-based MigrationTestHelper points at a real on-disk file that persists
    // between test methods; a stale file makes the next createDatabase() try to migrate an
    // existing DB ("A migration should never occur while creating a new database"). Delete
    // it (+ WAL/SHM sidecars) before and after each test.
    @Before
    fun clearDbFile() = deleteTestDb()

    @After
    fun cleanUpDbFile() = deleteTestDb()

    private fun deleteTestDb() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB)
        base.delete()
        File("${base.path}-wal").delete()
        File("${base.path}-shm").delete()
    }

    @Test
    fun migrate5to6_addsIsAdhocColumnDefaultZero() = runTest {
        val exerciseUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets)
                VALUES ('$exerciseUuid', 'Bench Press', 'WEIGHTED', NULL, NULL, 0,
                        $SEED_TIMESTAMP, NULL, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            db.prepare("SELECT is_adhoc FROM exercise_table WHERE uuid = '$exerciseUuid'")
                .use { stmt ->
                    assertTrue(stmt.step())
                    assertEquals(0, stmt.getInt(0))
                }
        }
    }

    @Test
    fun migrate5to6_deletesOrphanAdhocTrainingRow() = runTest {
        val orphanTrainingUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$orphanTrainingUuid', 'Track now: Squat', NULL, 1, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            db.prepare(
                "SELECT COUNT(*) FROM training_table WHERE uuid = '$orphanTrainingUuid'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals(0, stmt.getInt(0))
            }
        }
    }

    @Test
    fun migrate5to6_preservesAdhocTrainingWithLiveSession() = runTest {
        val trainingUuid = Uuid.random().toString()
        val sessionUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$trainingUuid', 'Track now: Squat', NULL, 1, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_table
                    (uuid, training_uuid, state, started_at, finished_at)
                VALUES ('$sessionUuid', '$trainingUuid', 'IN_PROGRESS', $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            db.prepare(
                "SELECT COUNT(*) FROM training_table WHERE uuid = '$trainingUuid'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals(1, stmt.getInt(0))
            }
            db.prepare(
                "SELECT COUNT(*) FROM session_table WHERE uuid = '$sessionUuid'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals(1, stmt.getInt(0))
            }
        }
    }

    @Test
    fun migrate5to6_preservesLibraryTrainingEvenWithoutSession() = runTest {
        val libraryTrainingUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$libraryTrainingUuid', 'Push day', NULL, 0, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            db.prepare(
                "SELECT name, is_adhoc FROM training_table WHERE uuid = '$libraryTrainingUuid'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals("Push day", stmt.getText(0))
                assertEquals(0, stmt.getInt(1))
            }
        }
    }

    @Test
    fun migrate5to6_existingExerciseDataIsPreserved() = runTest {
        val exerciseUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets)
                VALUES ('$exerciseUuid', 'Deadlift', 'WEIGHTED', 'desc', 'img.png', 0,
                        $SEED_TIMESTAMP, NULL, '[]')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            db.prepare(
                "SELECT name, type, description, image_path, last_adhoc_sets, is_adhoc " +
                    "FROM exercise_table WHERE uuid = '$exerciseUuid'",
            ).use { stmt ->
                assertTrue(stmt.step())
                assertEquals("Deadlift", stmt.getText(0))
                assertEquals("WEIGHTED", stmt.getText(1))
                assertEquals("desc", stmt.getText(2))
                assertEquals("img.png", stmt.getText(3))
                assertEquals("[]", stmt.getText(4))
                assertEquals(0, stmt.getInt(5))
            }
        }
    }

    @Test
    fun migrate5to6_pickerCanFilterIsAdhoc() = runTest {
        val libraryUuid = Uuid.random().toString()
        val adhocUuid = Uuid.random().toString()
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets)
                VALUES ('$libraryUuid', 'Bench Press', 'WEIGHTED', NULL, NULL, 0,
                        $SEED_TIMESTAMP, NULL, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets)
                VALUES ('$adhocUuid', 'Skull Crushers', 'WEIGHTED', NULL, NULL, 0,
                        $SEED_TIMESTAMP, NULL, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            // Both rows now have is_adhoc = 0 by default. Flip one to simulate an inline-create
            // and verify the picker filter excludes it.
            db.execSQL(
                "UPDATE exercise_table SET is_adhoc = 1 WHERE uuid = '$adhocUuid'",
            )
            db.prepare(
                "SELECT uuid FROM exercise_table WHERE archived = 0 AND is_adhoc = 0",
            ).use { stmt ->
                val visible = mutableListOf<String>()
                while (stmt.step()) visible += stmt.getText(0)
                assertEquals(listOf(libraryUuid), visible)
            }
        }
    }

    /**
     * Unregistered-table survival guard. Room 2's `runMigrationsAndValidate(...,
     * validateDroppedTables = true, ...)` failed the build if the post-migration DB carried
     * tables absent from the exported schema; Room 3 dropped that parameter. This test
     * reproduces THAT specific guarantee explicitly: after the migration, the set of user
     * tables in `sqlite_master` must equal the tables declared by [AppDatabase]'s exported v6
     * schema — excluding SQLite internals and Room's own bookkeeping table. It catches only
     * TABLE add/drop drift (e.g. a stray table left by a future migration). Column / index /
     * foreign-key drift is NOT covered here — that is caught separately by
     * `runMigrationsAndValidate`'s core schema validation against the exported `6.json`.
     *
     * NOTE: [EXPECTED_V6_TABLES] is a manual maintenance point — it must be kept in sync with
     * the `tableName`s in `schemas/.../AppDatabase/6.json` (updated whenever the entity set changes).
     */
    @Test
    fun migrate5to6_validatesNoUnregisteredTablesSurvive() = runTest {
        helper.createDatabase(5).use { /* empty v5 */ }

        helper.runMigrationsAndValidate(6, listOf(Migration6)).use { db ->
            val actual = mutableSetOf<String>()
            db.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata' " +
                    "AND name != 'room_master_table'",
            ).use { stmt ->
                while (stmt.step()) actual += stmt.getText(0)
            }
            assertEquals(EXPECTED_V6_TABLES, actual)
        }
    }

    private companion object {

        const val TEST_DB = "migration-test.db"
        const val SEED_TIMESTAMP = 1_700_000_000_000L

        // The user tables declared by AppDatabase's v6 exported schema (6.json tableNames).
        val EXPECTED_V6_TABLES = setOf(
            "exercise_table",
            "exercise_tag_table",
            "performed_exercise_table",
            "session_table",
            "set_table",
            "tag_table",
            "training_exercise_table",
            "training_table",
            "training_tag_table",
        )
    }
}
