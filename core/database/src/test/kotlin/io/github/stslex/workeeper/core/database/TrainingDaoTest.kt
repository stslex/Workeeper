package io.github.stslex.workeeper.core.database

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class)
internal class TrainingDaoTest : BaseDatabaseTest() {

    private val dao: TrainingDao get() = database.trainingDao

    private val testTrainings: List<TrainingEntity> by lazy {
        Array(10) { createTestTraining(it) }.toList()
    }

    @BeforeEach
    override fun initDb() {
        super.initDb()
    }

    @AfterEach
    override fun clearDb() {
        super.clearDb()
    }

    @Test
    fun `get all trainings with full database and empty query`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(testTrainings.size, actual?.size)
    }

    @Test
    fun `get all trainings with query matching all names`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val pagingSource = dao.getAll("Training")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(testTrainings.size, actual?.size)
    }

    @Test
    fun `get all trainings with query matching specific name`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val specificTraining = testTrainings.first()
        val pagingSource = dao.getAll(specificTraining.name)
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(1, actual?.size)
        assertEquals(specificTraining, actual?.first())
    }

    @Test
    fun `get all trainings with query not matching any name`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val pagingSource = dao.getAll("NonExistentTraining")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isEmpty())
        assertEquals(0, actual?.size)
    }

    @Test
    fun `get all trainings from empty database`() = runTest {
        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @Test
    fun `trainings are ordered by timestamp descending`() = runTest {
        val training1 = createTestTraining(0, timestamp = 100)
        val training2 = createTestTraining(1, timestamp = 300)
        val training3 = createTestTraining(2, timestamp = 200)

        dao.add(training1)
        dao.add(training2)
        dao.add(training3)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(3, actual?.size)
        assertEquals(training2, actual?.get(0))
        assertEquals(training3, actual?.get(1))
        assertEquals(training1, actual?.get(2))
    }

    @Test
    fun `add single training`() = runTest {
        val training = testTrainings.first()
        dao.add(training)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actual?.size)
        assertEquals(training, actual?.first())
    }

    @Test
    fun `add multiple trainings`() = runTest {
        testTrainings.forEach { dao.add(it) }

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(testTrainings.size, actual?.size)
        assertTrue(actual.orEmpty().containsAll(testTrainings))
    }

    @Test
    fun `add duplicate training should replace existing`() = runTest {
        val originalTraining = testTrainings.first()
        val updatedTraining = originalTraining.copy(name = "Updated Training")

        dao.add(originalTraining)
        dao.add(updatedTraining)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actual?.size)
        assertEquals(updatedTraining, actual?.first())
    }

    @Test
    fun `update existing training`() = runTest {
        val originalTraining = testTrainings.first()
        val updatedTraining = originalTraining.copy(
            name = "Updated Training",
            labels = listOf("Updated Label")
        )

        dao.add(originalTraining)
        dao.update(updatedTraining)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actual?.size)
        assertEquals(updatedTraining, actual?.first())
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `update non-existing training should not add new training`() = runTest {
        val nonExistingTraining = createTestTraining(0, uuid = Uuid.random())

        dao.update(nonExistingTraining)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @Test
    fun `delete existing training`() = runTest {
        val training = testTrainings.first()
        dao.add(training)

        val pagingSourceBefore = dao.getAll("")
        val loadResultBefore = pagingSourceBefore.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actualBefore = (loadResultBefore as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actualBefore?.size)

        dao.delete(training.uuid)

        val pagingSourceAfter = dao.getAll("")
        val loadResultAfter = pagingSourceAfter.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actualAfter = (loadResultAfter as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actualAfter?.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `delete non-existing training should not affect database`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val initialCount = testTrainings.size

        dao.delete(Uuid.random())

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(initialCount, actual?.size)
    }

    @Test
    fun `delete specific training from multiple trainings`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val trainingToDelete = testTrainings[2]

        dao.delete(trainingToDelete.uuid)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(testTrainings.size - 1, actual?.size)
        assertTrue(actual.orEmpty().none { it.uuid == trainingToDelete.uuid })
    }

    @Test
    fun `clear all trainings`() = runTest {
        testTrainings.forEach { dao.add(it) }

        val pagingSourceBefore = dao.getAll("")
        val loadResultBefore = pagingSourceBefore.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actualBefore = (loadResultBefore as? PagingSource.LoadResult.Page)?.data
        assertEquals(testTrainings.size, actualBefore?.size)

        dao.clear()

        val pagingSourceAfter = dao.getAll("")
        val loadResultAfter = pagingSourceAfter.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actualAfter = (loadResultAfter as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actualAfter?.size)
    }

    @Test
    fun `clear empty database should not cause errors`() = runTest {
        dao.clear()

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @Test
    fun `search with case insensitive query`() = runTest {
        val training = createTestTraining(0, name = "Upper Body Training")
        dao.add(training)

        val testQueries = listOf("UPPER", "upper", "Body", "BODY", "training", "TRAINING")

        testQueries.forEach { query ->
            val pagingSource = dao.getAll(query)
            val loadResult = pagingSource.load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 10,
                    placeholdersEnabled = false
                )
            )
            val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
            assertEquals(1, actual?.size, "Query '$query' should match the training")
            assertEquals(training, actual?.first())
        }
    }

    @Test
    fun `search with partial query`() = runTest {
        val training = createTestTraining(0, name = "Full Body Strength Training")
        dao.add(training)

        val testQueries = listOf("Full", "Body", "Strength", "Training", "ll Bo", "dy Str")

        testQueries.forEach { query ->
            val pagingSource = dao.getAll(query)
            val loadResult = pagingSource.load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 10,
                    placeholdersEnabled = false
                )
            )
            val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
            assertEquals(1, actual?.size, "Query '$query' should match the training")
            assertEquals(training, actual?.first())
        }
    }

    @Test
    fun `training with complex data structures`() = runTest {
        val complexTraining = TrainingEntity(
            uuid = Uuid.random(),
            name = "Complex Training Session",
            exercises = listOf(Uuid.random(), Uuid.random(), Uuid.random()),
            labels = listOf("Strength", "Upper Body", "Intermediate", "30 min"),
            timestamp = System.currentTimeMillis()
        )

        dao.add(complexTraining)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actual?.size)
        assertEquals(complexTraining, actual?.first())
    }

    @Test
    fun `training with empty lists`() = runTest {
        val emptyTraining = TrainingEntity(
            uuid = Uuid.random(),
            name = "Empty Training",
            exercises = emptyList(),
            labels = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        dao.add(emptyTraining)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(1, actual?.size)
        assertEquals(emptyTraining, actual?.first())
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createTestTraining(
        index: Int = 0,
        uuid: Uuid = Uuid.random(),
        name: String? = null,
        timestamp: Long? = null
    ): TrainingEntity = TrainingEntity(
        uuid = uuid,
        name = name ?: "Training_$index",
        exercises = listOf(Uuid.random(), Uuid.random()),
        labels = listOf("Label_$index", "Test"),
        timestamp = timestamp ?: System.currentTimeMillis() + index * 1000
    )
}