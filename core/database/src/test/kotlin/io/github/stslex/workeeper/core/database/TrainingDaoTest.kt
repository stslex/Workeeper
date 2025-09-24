package io.github.stslex.workeeper.core.database

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `deleteAll with empty list should not affect database`() = runTest {
        testTrainings.forEach { dao.add(it) }

        dao.deleteAll(emptyList())

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
    }

    @Test
    fun `deleteAll with single uuid`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val trainingToDelete = testTrainings[3]

        dao.deleteAll(listOf(trainingToDelete.uuid))

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
    fun `deleteAll with multiple uuids`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val trainingsToDelete = listOf(testTrainings[1], testTrainings[3], testTrainings[5])
        val uuidsToDelete = trainingsToDelete.map { it.uuid }

        dao.deleteAll(uuidsToDelete)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(testTrainings.size - 3, actual?.size)
        assertTrue(actual.orEmpty().none { training ->
            trainingsToDelete.any { it.uuid == training.uuid }
        })
    }

    @Test
    fun `deleteAll with all uuids should clear database`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val allUuids = testTrainings.map { it.uuid }

        dao.deleteAll(allUuids)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `deleteAll with non-existing uuids should not affect database`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val nonExistingUuids = List(3) { Uuid.random() }

        dao.deleteAll(nonExistingUuids)

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
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `deleteAll with mix of existing and non-existing uuids`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val existingUuids = listOf(testTrainings[2].uuid, testTrainings[4].uuid)
        val nonExistingUuids = List(2) { Uuid.random() }
        val mixedUuids = existingUuids + nonExistingUuids

        dao.deleteAll(mixedUuids)

        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = testTrainings.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(testTrainings.size - 2, actual?.size)
        assertTrue(actual.orEmpty().none { training ->
            existingUuids.contains(training.uuid)
        })
    }

    @Test
    fun `deleteAll with duplicate uuids in list`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val trainingToDelete = testTrainings[2]
        val uuidsWithDuplicates = listOf(
            trainingToDelete.uuid,
            trainingToDelete.uuid,
            trainingToDelete.uuid
        )

        dao.deleteAll(uuidsWithDuplicates)

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
    fun `deleteAll on empty database should not cause errors`() = runTest {
        val uuidsToDelete = testTrainings.take(3).map { it.uuid }

        dao.deleteAll(uuidsToDelete)

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
    fun `deleteAll preserves correct trainings order after deletion`() = runTest {
        val orderedTrainings = List(5) { index ->
            createTestTraining(index, timestamp = (5 - index).toLong() * 1000)
        }
        orderedTrainings.forEach { dao.add(it) }

        val uuidsToDelete = listOf(orderedTrainings[1].uuid, orderedTrainings[3].uuid)

        dao.deleteAll(uuidsToDelete)

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
        assertEquals(orderedTrainings[0], actual?.get(0))
        assertEquals(orderedTrainings[2], actual?.get(1))
        assertEquals(orderedTrainings[4], actual?.get(2))
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
    fun `get single training by uuid`() = runTest {
        val training = testTrainings.first()
        dao.add(training)

        val retrievedTraining = dao.get(training.uuid)
        assertEquals(training, retrievedTraining)
    }

    @Test
    fun `get non-existing training by uuid returns null`() = runTest {
        val nonExistingUuid = Uuid.random()
        val retrievedTraining = dao.get(nonExistingUuid)
        assertNull(retrievedTraining)
    }

    @Test
    fun `get training by uuid from multiple trainings`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val targetTraining = testTrainings[3]

        val retrievedTraining = dao.get(targetTraining.uuid)
        assertEquals(targetTraining, retrievedTraining)
    }

    @Test
    fun `get training flow by uuid from multiple trainings`() = runTest {
        testTrainings.forEach { dao.add(it) }
        val targetTraining = testTrainings[3]

        val retrievedTraining = dao.subscribeForTraining(targetTraining.uuid).first()
        assertEquals(targetTraining, retrievedTraining)
    }

    @Test
    fun `get non-existing training flow by uuid returns null`() = runTest {
        val nonExistingUuid = Uuid.random()
        val retrievedTraining = dao.subscribeForTraining(nonExistingUuid).firstOrNull()
        assertNull(retrievedTraining)
    }

    @Test
    fun `get single training flow by uuid`() = runTest {
        val training = testTrainings.first()
        dao.add(training)

        val retrievedTraining = dao.subscribeForTraining(training.uuid).first()
        assertEquals(training, retrievedTraining)
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

    @Test
    fun `get trainings with date range returns matching trainings`() = runTest {
        val baseTime = 1000000L
        val trainingsInRange = listOf(
            createTestTraining(0, name = "Training A", timestamp = baseTime + 100),
            createTestTraining(1, name = "Training B", timestamp = baseTime + 200),
            createTestTraining(2, name = "Training C", timestamp = baseTime + 300)
        )
        val trainingsOutOfRange = listOf(
            createTestTraining(3, name = "Training D", timestamp = baseTime - 100),
            createTestTraining(4, name = "Training E", timestamp = baseTime + 1000)
        )

        (trainingsInRange + trainingsOutOfRange).forEach { dao.add(it) }

        val result = dao.getTrainings("Training", baseTime, baseTime + 500)

        assertEquals(3, result.size)
        trainingsInRange.forEach { expected ->
            assertTrue(result.any { it.uuid == expected.uuid })
        }
    }

    @Test
    fun `get trainings with name filter returns matching trainings`() = runTest {
        val trainings = listOf(
            createTestTraining(0, name = "Chest Workout", timestamp = 1000L),
            createTestTraining(1, name = "Back Workout", timestamp = 2000L),
            createTestTraining(2, name = "Chest Training", timestamp = 3000L),
            createTestTraining(3, name = "Leg Day", timestamp = 4000L)
        )

        trainings.forEach { dao.add(it) }

        val result = dao.getTrainings("Chest", 0, 5000L)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Chest Workout" })
        assertTrue(result.any { it.name == "Chest Training" })
    }

    @Test
    fun `get trainings with no matches returns empty list`() = runTest {
        val trainings = listOf(
            createTestTraining(0, name = "Workout A", timestamp = 1000L),
            createTestTraining(1, name = "Workout B", timestamp = 2000L)
        )

        trainings.forEach { dao.add(it) }

        val result = dao.getTrainings("NonExistentName", 0, 5000L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `get trainings orders by timestamp descending`() = runTest {
        val trainings = listOf(
            createTestTraining(0, name = "Training A", timestamp = 1000L),
            createTestTraining(1, name = "Training B", timestamp = 3000L),
            createTestTraining(2, name = "Training C", timestamp = 2000L)
        )

        trainings.forEach { dao.add(it) }

        val result = dao.getTrainings("Training", 0, 5000L)

        assertEquals(3, result.size)
        assertEquals("Training B", result[0].name) // timestamp 3000L
        assertEquals("Training C", result[1].name) // timestamp 2000L
        assertEquals("Training A", result[2].name) // timestamp 1000L
    }

    @Test
    fun `get trainings with partial name match works correctly`() = runTest {
        val trainings = listOf(
            createTestTraining(0, name = "Upper Body Training", timestamp = 1000L),
            createTestTraining(1, name = "Full Body Workout", timestamp = 2000L),
            createTestTraining(2, name = "Lower Body Session", timestamp = 3000L)
        )

        trainings.forEach { dao.add(it) }

        val result = dao.getTrainings("Body", 0, 5000L)

        assertEquals(3, result.size)
    }

    @Test
    fun `get trainings from empty database returns empty list`() = runTest {
        val result = dao.getTrainings("Any", 0, 5000L)

        assertTrue(result.isEmpty())
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
        timestamp = timestamp ?: (System.currentTimeMillis() + index * 1000)
    )
}