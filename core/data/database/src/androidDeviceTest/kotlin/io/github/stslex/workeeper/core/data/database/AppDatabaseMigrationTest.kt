// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.data.database.migration.Migration6
import io.github.stslex.workeeper.core.data.database.migration.Migration7
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
 * Migration fixture for [AppDatabase]: seed v(N) with raw SQL, run the `Migration`, assert the
 * v(N+1) shape. GUARD: Room 3 does not validate dropped/extra tables, so the `sqlite_master`
 * assertion below is the only guard against unregistered-table drift.
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

    // GUARD: the File-based MigrationTestHelper keeps a real file between test methods, and a
    // stale one makes the next createDatabase() try to migrate. Delete it and its sidecars.
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
     * Unregistered-table survival guard: the user tables in `sqlite_master` must equal the v6
     * exported schema. Table add/drop drift only; column/index/FK drift is validated separately.
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

    @Test
    fun migrate6to7_preservesWorkoutAndInstallsDurableWearVersioning() = runTest {
        val seed = seedV6Workout()

        helper.runMigrationsAndValidate(7, listOf(Migration7)).use { db ->
            db.prepare(
                "SELECT reps, weight, type FROM set_table WHERE uuid = '${seed.setUuid}'",
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals(8, statement.getInt(0))
                assertEquals(100.0, statement.getDouble(1), 0.0)
                assertEquals("WORK", statement.getText(2))
            }
            db.prepare(
                """
                SELECT wear_revision, wear_lease_generation,
                       wear_receipt_command_id, wear_receipt_attempt_fingerprint,
                       wear_receipt_database_epoch, wear_receipt_revision
                FROM session_table WHERE uuid = '${seed.sessionUuid}'
                """.trimIndent(),
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals(0L, statement.getLong(0))
                assertEquals(0L, statement.getLong(1))
                for (column in 2..5) assertTrue(statement.isNull(column))
            }
            db.prepare("SELECT database_epoch FROM wear_database_metadata WHERE singleton_id = 0")
                .use { statement ->
                    assertTrue(statement.step())
                    Uuid.parse(statement.getText(0))
                }
            db.prepare("PRAGMA index_list('set_table')").use { statement ->
                var uniqueTargetIndexFound = false
                while (statement.step()) {
                    if (statement.getText(1) == TARGET_INDEX_NAME) {
                        uniqueTargetIndexFound = statement.getInt(2) == 1
                    }
                }
                assertTrue(uniqueTargetIndexFound)
            }
        }
    }

    @Test
    fun migrate6to7_triggerBumpsRevisionAndClearsReceiptWithSetWrite() = runTest {
        val seed = seedV6Workout()

        helper.runMigrationsAndValidate(7, listOf(Migration7)).use { db ->
            val epoch = db.prepare(
                "SELECT database_epoch FROM wear_database_metadata WHERE singleton_id = 0",
            ).use { statement ->
                assertTrue(statement.step())
                statement.getText(0)
            }
            db.execSQL(
                """
                UPDATE session_table
                SET wear_receipt_command_id = '${Uuid.random()}',
                    wear_receipt_attempt_fingerprint = X'0102',
                    wear_receipt_database_epoch = '$epoch',
                    wear_receipt_revision = 0
                WHERE uuid = '${seed.sessionUuid}'
                """.trimIndent(),
            )
            db.execSQL("UPDATE set_table SET reps = 9 WHERE uuid = '${seed.setUuid}'")
            db.prepare(
                """
                SELECT wear_revision, wear_receipt_command_id,
                       wear_receipt_attempt_fingerprint, wear_receipt_database_epoch,
                       wear_receipt_revision
                FROM session_table WHERE uuid = '${seed.sessionUuid}'
                """.trimIndent(),
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
                for (column in 1..4) assertTrue(statement.isNull(column))
            }
        }
    }

    /**
     * The whole reconciliation contract in one case: nothing is lost, no duplicate survives, the
     * lowest-`rowid` row of each contested position keeps it, and the losers append after the
     * exercise's maximum position in ascending `rowid` order across both contested groups.
     */
    @Test
    fun migrate6to7_reconcilesDuplicateTargetsWithoutLosingRows() = runTest {
        seedV6Sets(
            listOf(
                SeedSet(rowId = 10, uuid = SET_A, performedUuid = PERFORMED_ONE, position = 0),
                SeedSet(rowId = 20, uuid = SET_B, performedUuid = PERFORMED_ONE, position = 1),
                SeedSet(rowId = 30, uuid = SET_C, performedUuid = PERFORMED_ONE, position = 0),
                SeedSet(rowId = 40, uuid = SET_D, performedUuid = PERFORMED_ONE, position = 1),
                SeedSet(rowId = 50, uuid = SET_E, performedUuid = PERFORMED_ONE, position = 2),
            ),
        )

        helper.runMigrationsAndValidate(7, listOf(Migration7)).use { db ->
            assertEquals(5L, db.countSets())
            assertEquals(0L, db.countDuplicateTargets())
            val positions = db.readSetPositions().toMap()
            assertEquals(setOf(SET_A, SET_B, SET_C, SET_D, SET_E), positions.keys)
            // The lowest rowid of each contested position keeps it; an uncontested row is untouched.
            assertEquals(0L, positions[SET_A])
            assertEquals(1L, positions[SET_B])
            assertEquals(2L, positions[SET_E])
            // Losers append after max(position) = 2, by rowid, counting across both groups.
            assertEquals(3L, positions[SET_C])
            assertEquals(4L, positions[SET_D])
        }
    }

    /**
     * Two performed exercises, each contested, reconciled independently — and the first one's
     * positions are sparse, so its single loser must clear `max(position)` rather than fill the
     * hole at 1.
     */
    @Test
    fun migrate6to7_reconcilesEachPerformedExerciseAgainstItsOwnSparseMaximum() = runTest {
        seedV6Sets(SPARSE_MULTI_GROUP_FIXTURE)

        helper.runMigrationsAndValidate(7, listOf(Migration7)).use { db ->
            assertEquals(6L, db.countSets())
            assertEquals(0L, db.countDuplicateTargets())
            val positions = db.readSetPositions().toMap()
            // Sparse exercise: 0 and 5 survive, the loser clears the maximum instead of taking 1.
            assertEquals(0L, positions[SET_A])
            assertEquals(5L, positions[SET_C])
            assertEquals(6L, positions[SET_B])
            // Three-way collision on the other exercise, counting from its own maximum of 3.
            assertEquals(3L, positions[SET_D])
            assertEquals(4L, positions[SET_E])
            assertEquals(5L, positions[SET_F])
        }
    }

    /**
     * Byte-identical inputs must reconcile identically. Only `set_table` is compared: the
     * migration mints a random Wear database epoch, so the files themselves never match.
     */
    @Test
    fun migrate6to7_reconciliationIsDeterministicForIdenticalDatabases() = runTest {
        seedV6Sets(SPARSE_MULTI_GROUP_FIXTURE)
        val first = helper.runMigrationsAndValidate(7, listOf(Migration7))
            .use { db -> db.readSetPositions() }

        deleteTestDb()
        seedV6Sets(SPARSE_MULTI_GROUP_FIXTURE)
        val second = helper.runMigrationsAndValidate(7, listOf(Migration7))
            .use { db -> db.readSetPositions() }

        assertEquals(first, second)
    }

    /** One seeded `set_table` row whose `rowid` is pinned, so the winner rule is stated, not implied. */
    private data class SeedSet(
        val rowId: Long,
        val uuid: String,
        val performedUuid: String,
        val position: Int,
    )

    /**
     * Seeds a v6 database holding exactly [sets], spreading them over one performed-exercise row
     * per distinct `performedUuid` inside a single IN_PROGRESS session.
     */
    private suspend fun seedV6Sets(sets: List<SeedSet>) {
        val performedUuids = sets.map(SeedSet::performedUuid).distinct()
        helper.createDatabase(6).use { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('$FIXED_TRAINING', 'Strength', NULL, 0, 0, $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets, is_adhoc)
                VALUES ('$FIXED_EXERCISE', 'Deadlift', 'WEIGHTED', NULL, NULL, 0,
                        $SEED_TIMESTAMP, NULL, NULL, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_table
                    (uuid, training_uuid, state, started_at, finished_at)
                VALUES ('$FIXED_SESSION', '$FIXED_TRAINING', 'IN_PROGRESS', $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
            performedUuids.forEachIndexed { index, performedUuid ->
                db.execSQL(
                    """
                    INSERT INTO performed_exercise_table
                        (uuid, session_uuid, exercise_uuid, position, skipped)
                    VALUES ('$performedUuid', '$FIXED_SESSION', '$FIXED_EXERCISE', $index, 0)
                    """.trimIndent(),
                )
            }
            sets.forEach { seedSet ->
                db.execSQL(
                    """
                    INSERT INTO set_table
                        (rowid, uuid, performed_exercise_uuid, position, reps, weight, type)
                    VALUES (${seedSet.rowId}, '${seedSet.uuid}', '${seedSet.performedUuid}',
                            ${seedSet.position}, 8, 100.0, 'WORK')
                    """.trimIndent(),
                )
            }
        }
    }

    /** Every surviving set row as `uuid to position`, ordered by uuid so comparison is stable. */
    private fun SQLiteConnection.readSetPositions(): List<Pair<String, Long>> {
        val rows = mutableListOf<Pair<String, Long>>()
        prepare("SELECT uuid, position FROM set_table ORDER BY uuid").use { statement ->
            while (statement.step()) {
                rows += statement.getText(0) to statement.getLong(1)
            }
        }
        return rows
    }

    private fun SQLiteConnection.countSets(): Long =
        prepare("SELECT COUNT(*) FROM set_table").use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    private fun SQLiteConnection.countDuplicateTargets(): Long =
        prepare(
            """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM set_table
                GROUP BY performed_exercise_uuid, position
                HAVING COUNT(*) > 1
            )
            """.trimIndent(),
        ).use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    private suspend fun seedV6Workout(): V6WorkoutSeed {
        val seed = V6WorkoutSeed()
        helper.createDatabase(6).use { db ->
            db.execSQL(
                """
                INSERT INTO training_table
                    (uuid, name, description, is_adhoc, archived, created_at, archived_at)
                VALUES ('${seed.trainingUuid}', 'Strength', NULL, 0, 0,
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO exercise_table
                    (uuid, name, type, description, image_path, archived,
                     created_at, archived_at, last_adhoc_sets, is_adhoc)
                VALUES ('${seed.exerciseUuid}', 'Deadlift', 'WEIGHTED', NULL, NULL, 0,
                        $SEED_TIMESTAMP, NULL, '[{"weight":100.0,"reps":8,"type":"WORK"}]', 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_table
                    (uuid, training_uuid, state, started_at, finished_at)
                VALUES ('${seed.sessionUuid}', '${seed.trainingUuid}', 'IN_PROGRESS',
                        $SEED_TIMESTAMP, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO performed_exercise_table
                    (uuid, session_uuid, exercise_uuid, position, skipped)
                VALUES ('${seed.performedUuid}', '${seed.sessionUuid}',
                        '${seed.exerciseUuid}', 0, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO set_table
                    (uuid, performed_exercise_uuid, position, reps, weight, type)
                VALUES ('${seed.setUuid}', '${seed.performedUuid}', 0, 8, 100.0, 'WORK')
                """.trimIndent(),
            )
        }
        return seed
    }

    private companion object {

        const val TEST_DB = "migration-test.db"
        const val SEED_TIMESTAMP = 1_700_000_000_000L
        const val TARGET_INDEX_NAME = "index_set_table_performed_exercise_uuid_position"

        // Fixed identities: the determinism gate needs two byte-identical seeded databases.
        const val FIXED_TRAINING = "11111111-1111-4111-8111-111111111111"
        const val FIXED_EXERCISE = "22222222-2222-4222-8222-222222222222"
        const val FIXED_SESSION = "33333333-3333-4333-8333-333333333333"
        const val PERFORMED_ONE = "44444444-4444-4444-8444-444444444444"
        const val PERFORMED_TWO = "55555555-5555-4555-8555-555555555555"
        const val SET_A = "aaaaaaaa-0000-4000-8000-000000000001"
        const val SET_B = "aaaaaaaa-0000-4000-8000-000000000002"
        const val SET_C = "aaaaaaaa-0000-4000-8000-000000000003"
        const val SET_D = "aaaaaaaa-0000-4000-8000-000000000004"
        const val SET_E = "aaaaaaaa-0000-4000-8000-000000000005"
        const val SET_F = "aaaaaaaa-0000-4000-8000-000000000006"

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

        /** Sparse first exercise (0, 0, 5) beside a three-way collision on a second exercise. */
        val SPARSE_MULTI_GROUP_FIXTURE = listOf(
            SeedSet(rowId = 10, uuid = SET_A, performedUuid = PERFORMED_ONE, position = 0),
            SeedSet(rowId = 20, uuid = SET_B, performedUuid = PERFORMED_ONE, position = 0),
            SeedSet(rowId = 30, uuid = SET_C, performedUuid = PERFORMED_ONE, position = 5),
            SeedSet(rowId = 40, uuid = SET_D, performedUuid = PERFORMED_TWO, position = 3),
            SeedSet(rowId = 50, uuid = SET_E, performedUuid = PERFORMED_TWO, position = 3),
            SeedSet(rowId = 60, uuid = SET_F, performedUuid = PERFORMED_TWO, position = 3),
        )
    }

    private data class V6WorkoutSeed(
        val trainingUuid: String = Uuid.random().toString(),
        val exerciseUuid: String = Uuid.random().toString(),
        val sessionUuid: String = Uuid.random().toString(),
        val performedUuid: String = Uuid.random().toString(),
        val setUuid: String = Uuid.random().toString(),
    )
}
