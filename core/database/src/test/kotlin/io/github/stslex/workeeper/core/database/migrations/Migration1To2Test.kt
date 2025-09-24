package io.github.stslex.workeeper.core.database.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class)
class Migration1To2Test {

    private lateinit var context: Context
    private lateinit var testDb: SupportSQLiteDatabase

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // No-op for test
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        // No-op for test
                    }
                })
                .build(),
        )
        testDb = openHelper.writableDatabase
    }

    @AfterEach
    fun tearDown() {
        if (::testDb.isInitialized) {
            testDb.close()
        }
    }

    @Test
    fun migration1To2_CreatesNewTablesCorrectly() {
        // Create initial exercises table structure (version 1)
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        // Verify new exercises table structure
        val exercisesCursor = testDb.query("PRAGMA table_info(exercises_table)")
        val exerciseColumns = mutableSetOf<String>()
        while (exercisesCursor.moveToNext()) {
            exerciseColumns.add(exercisesCursor.getString(1))
        }
        exercisesCursor.close()

        assertTrue(exerciseColumns.contains("uuid"))
        assertTrue(exerciseColumns.contains("training_uuid"))
        assertTrue(exerciseColumns.contains("labels"))
        assertTrue(exerciseColumns.contains("sets"))
        assertTrue(exerciseColumns.contains("name"))
        assertTrue(exerciseColumns.contains("timestamp"))

        // Verify training table exists
        val trainingCursor = testDb.query("PRAGMA table_info(training_table)")
        val trainingColumns = mutableSetOf<String>()
        while (trainingCursor.moveToNext()) {
            trainingColumns.add(trainingCursor.getString(1))
        }
        trainingCursor.close()

        assertTrue(trainingColumns.contains("uuid"))
        assertTrue(trainingColumns.contains("name"))
        assertTrue(trainingColumns.contains("exercises"))
        assertTrue(trainingColumns.contains("labels"))
        assertTrue(trainingColumns.contains("timestamp"))

        // Verify training labels table exists
        val labelsCursor = testDb.query("PRAGMA table_info(training_labels_table)")
        val labelsColumns = mutableSetOf<String>()
        while (labelsCursor.moveToNext()) {
            labelsColumns.add(labelsCursor.getString(1))
        }
        labelsCursor.close()

        assertTrue(labelsColumns.contains("label"))
    }

    @Test
    fun migration1To2_MigratesExistingDataCorrectly() {
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        val testUuid = "test-uuid-123"
        val testName = "Push Up"
        val testReps = 10
        val testWeight = 75.5
        val testSets = 3
        val testTimestamp = 1640995200000L

        testDb.execSQL(
            "INSERT INTO exercises_table (uuid, name, reps, weight, sets, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(testUuid, testName, testReps, testWeight, testSets, testTimestamp),
        )

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        // Verify migrated data
        val cursor = testDb.query("SELECT * FROM exercises_table WHERE uuid = ?", arrayOf(testUuid))
        assertTrue(cursor.moveToFirst())

        assertEquals(testUuid, cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
        assertNull(cursor.getString(cursor.getColumnIndexOrThrow("training_uuid")))
        assertEquals(testName, cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals(testTimestamp, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))

        // Verify labels conversion
        val labelsJson = cursor.getString(cursor.getColumnIndexOrThrow("labels"))
        val labels = Json.decodeFromString<List<String>>(labelsJson)
        assertTrue(labels.isEmpty())

        // Verify sets conversion
        val setsJson = cursor.getString(cursor.getColumnIndexOrThrow("sets"))
        val sets = Json.decodeFromString<List<SetsEntity>>(setsJson)
        assertEquals(testSets, sets.size)

        sets.forEach { setEntity ->
            assertEquals(testReps, setEntity.reps)
            assertEquals(testWeight, setEntity.weight)
            assertEquals(SetsEntityType.WORK, setEntity.type)
            assertNotNull(setEntity.uuid)
        }

        cursor.close()
    }

    @Test
    fun migration1To2_HandlesMultipleExercisesCorrectly() {
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        val exercises = listOf(
            TestExercise("uuid1", "Bench Press", 8, 100.0, 4, 1640995200000L),
            TestExercise("uuid2", "Squat", 12, 120.5, 3, 1640995300000L),
            TestExercise("uuid3", "Deadlift", 5, 150.0, 5, 1640995400000L),
        )

        exercises.forEach { exercise ->
            testDb.execSQL(
                "INSERT INTO exercises_table (uuid, name, reps, weight, sets, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(
                    exercise.uuid,
                    exercise.name,
                    exercise.reps,
                    exercise.weight,
                    exercise.sets,
                    exercise.timestamp,
                ),
            )
        }

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        // Verify all exercises were migrated correctly
        val cursor = testDb.query("SELECT COUNT(*) FROM exercises_table")
        assertTrue(cursor.moveToFirst())
        assertEquals(exercises.size, cursor.getInt(0))
        cursor.close()

        // Verify each exercise individually
        exercises.forEach { originalExercise ->
            val exerciseCursor = testDb.query(
                "SELECT * FROM exercises_table WHERE uuid = ?",
                arrayOf(originalExercise.uuid),
            )
            assertTrue(exerciseCursor.moveToFirst())

            val setsJson = exerciseCursor.getString(exerciseCursor.getColumnIndexOrThrow("sets"))
            val sets = Json.decodeFromString<List<SetsEntity>>(setsJson)

            assertEquals(originalExercise.sets, sets.size)
            sets.forEach { setEntity ->
                assertEquals(originalExercise.reps, setEntity.reps)
                assertEquals(originalExercise.weight, setEntity.weight)
                assertEquals(SetsEntityType.WORK, setEntity.type)
            }

            exerciseCursor.close()
        }
    }

    @Test
    fun migration1To2_HandlesEmptyDatabaseCorrectly() {
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        // Verify tables exist but are empty
        val exercisesCursor = testDb.query("SELECT COUNT(*) FROM exercises_table")
        assertTrue(exercisesCursor.moveToFirst())
        assertEquals(0, exercisesCursor.getInt(0))
        exercisesCursor.close()

        val trainingCursor = testDb.query("SELECT COUNT(*) FROM training_table")
        assertTrue(trainingCursor.moveToFirst())
        assertEquals(0, trainingCursor.getInt(0))
        trainingCursor.close()

        val labelsCursor = testDb.query("SELECT COUNT(*) FROM training_labels_table")
        assertTrue(labelsCursor.moveToFirst())
        assertEquals(0, labelsCursor.getInt(0))
        labelsCursor.close()
    }

    @Test
    fun migration1To2_HandlesNullAndInvalidDataCorrectly() {
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        // Insert exercise with zero sets (edge case)
        testDb.execSQL(
            "INSERT INTO exercises_table (uuid, name, reps, weight, sets, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("uuid-zero-sets", "Test Exercise", 10, 50.0, 0, 1640995200000L),
        )

        // Insert exercise with negative values (edge case)
        testDb.execSQL(
            "INSERT INTO exercises_table (uuid, name, reps, weight, sets, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("uuid-negative", "Negative Exercise", -5, -10.0, 1, 1640995200000L),
        )

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        // Verify zero sets exercise
        val zeroSetsCursor = testDb.query(
            "SELECT * FROM exercises_table WHERE uuid = ?",
            arrayOf("uuid-zero-sets"),
        )
        assertTrue(zeroSetsCursor.moveToFirst())
        val zeroSetsJson = zeroSetsCursor.getString(zeroSetsCursor.getColumnIndexOrThrow("sets"))
        val zeroSets = Json.decodeFromString<List<SetsEntity>>(zeroSetsJson)
        assertEquals(0, zeroSets.size)
        zeroSetsCursor.close()

        // Verify negative values exercise
        val negativeCursor = testDb.query(
            "SELECT * FROM exercises_table WHERE uuid = ?",
            arrayOf("uuid-negative"),
        )
        assertTrue(negativeCursor.moveToFirst())
        val negativeSetsJson = negativeCursor.getString(
            negativeCursor.getColumnIndexOrThrow("sets"),
        )
        val negativeSets = Json.decodeFromString<List<SetsEntity>>(negativeSetsJson)
        assertEquals(1, negativeSets.size)
        assertEquals(-5, negativeSets[0].reps)
        assertEquals(-10.0, negativeSets[0].weight)
        negativeCursor.close()
    }

    @Test
    fun migration1To2_VerifiesSetEntityStructure() {
        testDb.execSQL(
            """
            CREATE TABLE exercises_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL NOT NULL,
                sets INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """,
        )

        testDb.execSQL(
            "INSERT INTO exercises_table (uuid, name, reps, weight, sets, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("test-uuid", "Test Exercise", 15, 80.5, 2, 1640995200000L),
        )

        // Run migration
        MIGRATION_1_2.migrate(testDb)

        val cursor = testDb.query(
            "SELECT sets FROM exercises_table WHERE uuid = ?",
            arrayOf("test-uuid"),
        )
        assertTrue(cursor.moveToFirst())

        val setsJson = cursor.getString(0)
        val sets = Json.decodeFromString<List<SetsEntity>>(setsJson)

        assertEquals(2, sets.size)
        sets.forEach { setEntity ->
            // Verify UUID is properly generated
            assertNotNull(setEntity.uuid)
            assertTrue(setEntity.uuid.toString().isNotEmpty())

            // Verify data migration
            assertEquals(15, setEntity.reps)
            assertEquals(80.5, setEntity.weight)
            assertEquals(SetsEntityType.WORK, setEntity.type)
        }

        cursor.close()
    }

    private data class TestExercise(
        val uuid: String,
        val name: String,
        val reps: Int,
        val weight: Double,
        val sets: Int,
        val timestamp: Long,
    )
}
