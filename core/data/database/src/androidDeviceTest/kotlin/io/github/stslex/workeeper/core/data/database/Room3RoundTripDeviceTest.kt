// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * ROOM 3 ROUND-TRIP — real device, real FILE-BACKED database, self-contained.
 *
 * ★ WHAT THIS PROVES, repeatably (runs in a normal `connectedAndroidTest`):
 *   Room 3 round-trips the production schema on a real on-disk file — write, close,
 *   RE-OPEN a fresh AppDatabase on the same file, read the exact values back, drive a
 *   PagingSource DAO (the `@DaoReturnTypeConverters` path), and persist a write through
 *   the ported `useWriterConnection { immediateTransaction { } }` transaction primitive.
 *   Self-seeding, so uninstall-between-runs is irrelevant (unlike the earlier cross-branch
 *   paired test, which required a Room-2-seeded file and could only run manually).
 *
 * ★ WHAT THIS DOES NOT PROVE:
 *   That a file written specifically by the Room 2.8.4 runtime is readable by Room 3.
 *   That is a DIFFERENT guarantee (the actual Play-upgrade cross-version path). It was
 *   proven ONCE, manually, on 2026-07-18 (Room-2 write APK + Room-3 read APK via
 *   `installDebugAndroidTest` install -r + `am instrument`, with an `ls -l` vacuity gate,
 *   3/3 green, plus a real dev-app launch on the Room-2 file with zero Room
 *   integrity/migration/driver exceptions). It is NOT in the automated suite — to repeat
 *   it, see documentation/tech-debt.md → "Room 2→3 cross-version upgrade proof".
 *
 * ⚠️ androidDeviceTest + file-backed on purpose (own DB name, not "app.db", so it never collides
 * with production/other tests). Do NOT move onto Robolectric or in-memory.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class Room3RoundTripDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    private fun openDb(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, ROUNDTRIP_DB)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ROUNDTRIP_DB)
        database = openDb()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(ROUNDTRIP_DB)
    }

    @Test
    fun room3WritesClosesReopensAndReadsExactValues() = runBlocking {
        // Write the known rows, then CLOSE (so nothing lingers in a still-open handle/WAL).
        database.exerciseDao.insert(exerciseEntity())
        database.trainingDao.insert(trainingEntity())
        database.close()

        // RE-OPEN a fresh instance on the same file — this is the "did it persist to disk" check.
        database = openDb()

        val exercise = database.exerciseDao.getById(EXERCISE_UUID)
        assertNotNull("exercise MUST be readable from the re-opened file", exercise)
        assertEquals(EXERCISE_NAME, exercise!!.name)

        val training = database.trainingDao.getById(TRAINING_UUID)
        assertNotNull("training MUST be readable from the re-opened file", training)
        assertEquals(TRAINING_NAME, training!!.name)
    }

    @Test
    fun room3PagingSourceReturnsThePersistedRow() = runBlocking {
        database.exerciseDao.insert(exerciseEntity())
        database.close()
        database = openDb()

        // pagedActive() : PagingSource<Int, ExerciseEntity> — the return type that needed
        // @DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter) under Room 3.
        val result = database.exerciseDao.pagedActive().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )
        assertTrue("PagingSource.load must return a Page", result is PagingSource.LoadResult.Page)
        val rows = (result as PagingSource.LoadResult.Page<Int, ExerciseEntity>).data
        assertTrue(
            "PagingSource from the re-opened file MUST contain the persisted exercise",
            rows.any { it.uuid == EXERCISE_UUID && it.name == EXERCISE_NAME },
        )
    }

    @Test
    fun room3WriteThroughTransactionPrimitivePersists() = runBlocking {
        val tagName = "RoundTrip Tag ${Uuid.random()}"
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                coroutineScope { database.tagDao.insert(TagEntity(name = tagName)) }
            }
        }
        database.close()
        database = openDb()

        assertTrue(
            "a write through the ported DbTransitionRunner primitive MUST persist across re-open",
            database.tagDao.observeAll().first().map { it.name }.contains(tagName),
        )
    }

    /**
     * KNOWN-NEGATIVE: asserting a value that was never written MUST fail — proving the
     * read assertions above can observe ABSENCE, so a round-trip that reads its own write
     * is a real check, not a tautology. Runs the same read path against a value never inserted.
     */
    @Test
    fun knownNegative_neverWrittenValueIsAbsent() = runBlocking {
        database.exerciseDao.insert(exerciseEntity())
        database.close()
        database = openDb()

        val neverWritten = database.exerciseDao.getById(
            Uuid.parse("99999999-9999-9999-9999-999999999999"),
        )
        // If the read path could not observe absence, this would be non-null — it must be null.
        assertEquals(
            "a never-written uuid must read back as absent (proves the positive reads can fail)",
            null,
            neverWritten,
        )
    }

    private fun exerciseEntity() = ExerciseEntity(
        uuid = EXERCISE_UUID,
        name = EXERCISE_NAME,
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = null,
        archived = false,
        createdAt = CREATED_AT,
        archivedAt = null,
        lastAdhocSets = null,
    )

    private fun trainingEntity() = TrainingEntity(
        uuid = TRAINING_UUID,
        name = TRAINING_NAME,
        description = null,
        isAdhoc = false,
        archived = false,
        createdAt = CREATED_AT,
        archivedAt = null,
    )

    private companion object {
        const val ROUNDTRIP_DB = "room3_roundtrip.db"
        val EXERCISE_UUID: Uuid = Uuid.parse("11111111-1111-1111-1111-111111111111")
        const val EXERCISE_NAME = "RoundTrip Bench"
        val TRAINING_UUID: Uuid = Uuid.parse("22222222-2222-2222-2222-222222222222")
        const val TRAINING_NAME = "RoundTrip PushDay"
        const val CREATED_AT = 1_700_000_000_000L
    }
}
