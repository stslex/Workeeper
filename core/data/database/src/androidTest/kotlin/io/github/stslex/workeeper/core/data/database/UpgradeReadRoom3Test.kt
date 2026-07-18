// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.CoroutineScope
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
 * UPGRADE TEST — READ HALF (runs on the Room 3 branch / chore/room3-parked).
 *
 * Opens the SAME production "app.db" file that [UpgradeWriteRoom2Test] wrote on the Room 2.8.4
 * branch (the file survives because we install the test APK with `installDebugAndroidTest`
 * (install -r, no uninstall) rather than `connectedAndroidTest` (which uninstalls + wipes the
 * data dir). This proves the Room 3 driver (AndroidSQLiteDriver = framework SQLite) reads a
 * v6 database written by the Room 2.8.4 runtime — the real Play-upgrade path.
 *
 * Self-guarding: if the file had been wiped, the Room-2-written rows are absent and every
 * assertion FAILS (no `count >= 0`, no insert-then-assert).
 *
 * @Ignore'd in the normal suite ON PURPOSE — this is the READ half of a two-branch, two-APK
 * upgrade procedure and is NOT self-contained: it requires a Room-2-written `app.db` seeded on
 * the same device by `UpgradeWriteRoom2Test` (Room 2 branch), preserved via
 * `installDebugAndroidTest` (install -r, no uninstall). Under a normal `connectedAndroidTest`
 * (which uninstalls+reinstalls, wiping the data dir) `app.db` is empty and these MUST fail.
 * To re-verify the upgrade path manually:
 *   1. git switch feature/metro-batch (Room 2); author/run UpgradeWriteRoom2Test via
 *      `./gradlew :core:data:database:installDebugAndroidTest` + `adb shell am instrument -w
 *      -e class ...UpgradeWriteRoom2Test <testPkg>/androidx.test.runner.AndroidJUnitRunner`.
 *   2. git switch <this branch>; `installDebugAndroidTest` (install -r, no uninstall);
 *      remove this @Ignore and `am instrument -w -e class ...UpgradeReadRoom3Test <testPkg>/...`.
 * Proven green on device 2026-07-18 (OK 3/3); see the branch commit history.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.Ignore(
    "Cross-branch two-APK upgrade proof — not self-contained; run manually via " +
        "installDebugAndroidTest (see KDoc). connectedAndroidTest wipes the seeded app.db.",
)
internal class UpgradeReadRoom3Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Open the EXISTING app.db (written by Room 2 on the other branch) via the Room 3
        // driver. Do NOT delete it. Do NOT add migrations beyond production's — the file is v6
        // and the code is v6, so no migration runs; this is purely "can Room 3 open the file".
        database = Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun room3ReadsExactRoom2WrittenValues() = runBlocking {
        val exercise = database.exerciseDao.getById(EXERCISE_UUID)
        assertNotNull("Room-2-written exercise MUST be present in the upgraded file", exercise)
        assertEquals(EXERCISE_NAME, exercise!!.name)

        val training = database.trainingDao.getById(TRAINING_UUID)
        assertNotNull("Room-2-written training MUST be present in the upgraded file", training)
        assertEquals(TRAINING_NAME, training!!.name)
    }

    @Test
    fun room3PagingSourceReturnsRoom2WrittenRows() = runBlocking {
        // pagedActive() is a PagingSource<Int, ExerciseEntity> — the return type that needed
        // @DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter) under Room 3. Drive it
        // directly via load() (no paging-testing dep) and assert the upgraded row is returned.
        val pagingSource = database.exerciseDao.pagedActive()
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )
        assertTrue("PagingSource.load must return a Page", result is PagingSource.LoadResult.Page)
        val rows = (result as PagingSource.LoadResult.Page<Int, ExerciseEntity>).data
        assertTrue(
            "PagingSource from the upgraded file MUST contain the Room-2-written exercise",
            rows.any { it.uuid == EXERCISE_UUID && it.name == EXERCISE_NAME },
        )
    }

    @Test
    fun room3WriteThroughTransactionPersistsOnUpgradedFile() = runBlocking {
        // A write through the ported (Room 3) DbTransitionRunner shape, on the upgraded file.
        val tagName = "UpgradeProbe Tag ${Uuid.random()}"
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                coroutineScope {
                    database.tagDao.insert(TagEntity(name = tagName))
                }
            }
        }
        val persisted = database.tagDao.observeAll().first().map { it.name }
        assertTrue(
            "a write through the Room 3 transaction primitive MUST persist on the upgraded file",
            persisted.contains(tagName),
        )
        // The Room-2-written data is still intact alongside the new write.
        assertNotNull(database.exerciseDao.getById(EXERCISE_UUID))
    }

    private companion object {
        // Must match the constants UpgradeWriteRoom2Test wrote on the Room 2 branch.
        val EXERCISE_UUID: Uuid = Uuid.parse("11111111-1111-1111-1111-111111111111")
        const val EXERCISE_NAME = "UpgradeProbe Bench"
        val TRAINING_UUID: Uuid = Uuid.parse("22222222-2222-2222-2222-222222222222")
        const val TRAINING_NAME = "UpgradeProbe PushDay"
    }
}
