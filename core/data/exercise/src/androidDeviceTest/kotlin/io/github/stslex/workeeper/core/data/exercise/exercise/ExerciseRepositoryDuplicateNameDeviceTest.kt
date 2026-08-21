// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.exercise

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.SaveResult
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * Duplicate-name oracle under the production `BundledSQLiteDriver`. The Robolectric twin
 * (`ExerciseRepositoryImplDbTest`) covers the framework driver; this test keeps the same
 * arrangement and asserted outcome on a real device. See kmp-phase-6-data-layer.md → §10
 * "androidx.sqlite.SQLiteException is actual typealias … on Android."
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class ExerciseRepositoryDuplicateNameDeviceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ExerciseRepositoryImpl

    private val transition = object : DbTransitionRunner {
        override suspend fun <T> invoke(block: suspend CoroutineScope.() -> T): T =
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction { coroutineScope { block() } }
            }
    }

    /** Fail-fast stub: the duplicate-name path must never touch image storage. */
    private val imageStorage = object : ImageStorage {
        override suspend fun saveImage(sourceRef: ImageRef, exerciseUuid: String): ImageSaveResult =
            error("saveItem must not reach ImageStorage.saveImage")

        override suspend fun createTempCaptureRef(): ImageRef =
            error("saveItem must not reach ImageStorage.createTempCaptureRef")

        override suspend fun deleteImage(path: String): Boolean =
            error("saveItem must not reach ImageStorage.deleteImage")

        override suspend fun cleanupTempFiles() =
            error("saveItem must not reach ImageStorage.cleanupTempFiles")
    }

    @Before
    fun setUp() {
        database = InMemoryDatabaseProvider.create(ApplicationProvider.getApplicationContext())
        repository = ExerciseRepositoryImpl(
            dao = database.exerciseDao,
            tagDao = database.tagDao,
            exerciseTagDao = database.exerciseTagDao,
            trainingExerciseDao = database.trainingExerciseDao,
            sessionDao = database.sessionDao,
            setDao = database.setDao,
            imageStorage = imageStorage,
            transition = transition,
            bgDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveItem_returnsDuplicateName_underBundledDriver() = runBlocking {
        val firstUuid = Uuid.random()
        assertEquals(
            SaveResult.Success,
            repository.saveItem(exerciseChange(uuid = firstUuid, name = "Bench")),
        )

        val collisionResult = repository.saveItem(
            exerciseChange(uuid = Uuid.random(), name = "BENCH"),
        )

        assertEquals(SaveResult.DuplicateName, collisionResult)
        // The original row is still present and unchanged.
        val rows = database.exerciseDao.getAllActive()
        assertEquals(1, rows.size)
        assertEquals(firstUuid, rows.single().uuid)
    }

    private fun exerciseChange(uuid: Uuid, name: String): ExerciseChangeDataModel =
        ExerciseChangeDataModel(
            uuid = uuid,
            name = name,
            type = ExerciseTypeDataModel.WEIGHTED,
            description = null,
            imagePath = null,
            archived = false,
            timestamp = 0L,
            labels = emptyList(),
            lastAdHocSets = null,
        )
}
