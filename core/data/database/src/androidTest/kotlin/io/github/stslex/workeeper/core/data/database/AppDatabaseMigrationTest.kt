// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.data.database.migration.Migration6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Migration test fixture for [AppDatabase].
 *
 * Each test seeds a v(N) DB through raw SQL, runs the matching `Migration` object via
 * [MigrationTestHelper.runMigrationsAndValidate], and asserts the resulting v(N+1) DB
 * has the expected shape and data. `validateDroppedTables = true` so any inadvertent
 * structural drift fails the build.
 */
internal class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5to6_addsIsAdhocColumnDefaultZero() {
        val exerciseUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
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

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            db.query("SELECT is_adhoc FROM exercise_table WHERE uuid = '$exerciseUuid'")
                .useCursor { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
        }
    }

    @Test
    fun migrate5to6_deletesOrphanAdhocTrainingRow() {
        val orphanTrainingUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$orphanTrainingUuid', 'Track now: Squat', NULL, 1, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            db.query(
                "SELECT COUNT(*) FROM training_table WHERE uuid = '$orphanTrainingUuid'",
            ).useCursor { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate5to6_preservesAdhocTrainingWithLiveSession() {
        val trainingUuid = Uuid.random().toString()
        val sessionUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
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

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            db.query(
                "SELECT COUNT(*) FROM training_table WHERE uuid = '$trainingUuid'",
            ).useCursor { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query(
                "SELECT COUNT(*) FROM session_table WHERE uuid = '$sessionUuid'",
            ).useCursor { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate5to6_preservesLibraryTrainingEvenWithoutSession() {
        val libraryTrainingUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$libraryTrainingUuid', 'Push day', NULL, 0, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            db.query(
                "SELECT name, is_adhoc FROM training_table WHERE uuid = '$libraryTrainingUuid'",
            ).useCursor { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Push day", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migrate5to6_existingExerciseDataIsPreserved() {
        val exerciseUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
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

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            db.query(
                "SELECT name, type, description, image_path, last_adhoc_sets, is_adhoc " +
                    "FROM exercise_table WHERE uuid = '$exerciseUuid'",
            ).useCursor { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Deadlift", cursor.getString(0))
                assertEquals("WEIGHTED", cursor.getString(1))
                assertEquals("desc", cursor.getString(2))
                assertEquals("img.png", cursor.getString(3))
                assertEquals("[]", cursor.getString(4))
                assertEquals(0, cursor.getInt(5))
            }
        }
    }

    @Test
    fun migrate5to6_pickerCanFilterIsAdhoc() {
        val libraryUuid = Uuid.random().toString()
        val adhocUuid = Uuid.random().toString()
        helper.createDatabase(TEST_DB, 5).useDb { db ->
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

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration6).useDb { db ->
            // Both rows now have is_adhoc = 0 by default. Flip one to simulate an inline-create
            // and verify the picker filter excludes it.
            db.execSQL(
                "UPDATE exercise_table SET is_adhoc = 1 WHERE uuid = '$adhocUuid'",
            )
            db.query(
                "SELECT uuid FROM exercise_table WHERE archived = 0 AND is_adhoc = 0",
            ).useCursor { cursor ->
                val visible = mutableListOf<String>()
                while (cursor.moveToNext()) visible += cursor.getString(0)
                assertEquals(listOf(libraryUuid), visible)
            }
        }
    }

    private companion object {

        const val TEST_DB = "migration-test.db"
        const val SEED_TIMESTAMP = 1_700_000_000_000L
    }
}

private inline fun <R> SupportSQLiteDatabase.useDb(block: (SupportSQLiteDatabase) -> R): R = try {
    block(this)
} finally {
    close()
}

private inline fun <R> Cursor.useCursor(block: (Cursor) -> R): R = try {
    block(this)
} finally {
    close()
}
