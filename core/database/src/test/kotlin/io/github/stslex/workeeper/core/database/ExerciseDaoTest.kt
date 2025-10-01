package io.github.stslex.workeeper.core.database

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
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
internal class ExerciseDaoTest : BaseDatabaseTest() {

    private val dao: ExerciseDao get() = database.exerciseDao

    private val exerciseEntities: List<ExerciseEntity> by lazy {
        Array(10) { createTestExercise(it) }.toList()
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
    fun `get all items with full database`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val pagingSource = dao.getAll()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(exerciseEntities.size, actual?.size)
    }

    @Test
    fun `get all items query with full database and empty query`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(exerciseEntities.size, actual?.size)
    }

    @Test
    fun `get all items query with full database and not existed query`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val pagingSource = dao.getAll("not_existed_query")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isEmpty())
        assertEquals(0, actual?.size)
    }

    @Test
    fun `get all items query with full database and particularly existed query`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val pagingSource = dao.getAll(exerciseEntities.first().name)
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(1, actual?.size)
    }

    @Test
    fun `get all items query with full database and all existed query`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val pagingSource = dao.getAll("test")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(exerciseEntities.size, actual?.size)
    }

    @Test
    fun `get all items full database with query from date to date matching all props`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val items = dao.getExercises("test", 0, Long.MAX_VALUE).reversed()
        assertEquals(exerciseEntities, items)
    }

    @Test
    fun `get all items full database with query from date to date not matching name`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val items = dao.getExercises("test_not_existed", 0, Long.MAX_VALUE).reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with query from date to date not matching date`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val items = dao.getExercises("test", 0, 1).reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with query from date to date not matching date and name`() =
        runTest {
            exerciseEntities.forEach { dao.create(it) }
            val items = dao.getExercises("test_not_existed", 0, 1).reversed()
            assertEquals(emptyList<ExerciseEntity>(), items)
        }

    @Test
    fun `get all items with empty database`() = runTest {
        val pagingSource = dao.getAll()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @Test
    fun `get all items query with empty database`() = runTest {
        val pagingSource = dao.getAll("")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false,
            ),
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertEquals(0, actual?.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `create one item and get it`() = runTest {
        val expectedExercise = exerciseEntities.first()
        dao.create(expectedExercise)
        val compareExercise = dao.getExercise(expectedExercise.uuid)
        assertEquals(expectedExercise, compareExercise)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `get item form empty db`() = runTest {
        val compareExercise = dao.getExercise(Uuid.random())
        assertEquals(null, compareExercise)
    }

    @Test
    fun `insert single item`() = runTest {
        val expectedItem = exerciseEntities.first()
        dao.create(expectedItem)
        val actual = dao.getExercise(expectedItem.uuid)
        assertEquals(expectedItem, actual)
    }

    @Test
    fun `insert multiple items`() = runTest {
        val expectedItems = exerciseEntities
        expectedItems.forEach { dao.create(it) }
        val actual = expectedItems.map {
            dao.getExercise(it.uuid)
        }
        assertEquals(expectedItems, actual)
    }

    @Test
    fun `delete single item`() = runTest {
        val item = createTestExercise()
        dao.create(item)
        assertEquals(item, dao.getExercise(item.uuid))
        dao.delete(item.uuid)
        assertEquals(null, dao.getExercise(item.uuid))
    }

    @Test
    fun `search unique results exclude success`() = runTest {
        val item1 = createTestExercise(0).copy(name = "name_1")
        val item2 = createTestExercise(1).copy(name = "other_title")
        val item3 = createTestExercise(2).copy(name = "other_title")
        val item4 = createTestExercise(3).copy(name = "search_title")

        dao.create(item1)
        dao.create(item2)
        dao.create(item3)
        dao.create(item4)

        val results = dao.searchUniqueExclude("title")
        assertEquals(listOf(item4, item3), results)

        val results2 = dao.searchUniqueExclude("other")
        assertEquals(listOf(item3), results2)

        val results3 = dao.searchUniqueExclude("_")
        assertEquals(listOf(item4, item3, item1), results3)

        val results4 = dao.searchUniqueExclude("1")
        assertEquals(listOf(item1), results4)

        val results5 = dao.searchUniqueExclude("name_123")
        assertTrue(results5.isEmpty())

        val results6 = dao.searchUniqueExclude("other_title")
        assertTrue(results6.isEmpty())
    }

    @Test
    fun `delete multiple items by uuids`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val uuidsToDelete = exerciseEntities.take(3).map { it.uuid }

        dao.delete(uuidsToDelete)

        val remaining = exerciseEntities.drop(3)
        remaining.forEach { exercise ->
            val actual = dao.getExercise(exercise.uuid)
            assertEquals(exercise, actual)
        }

        uuidsToDelete.forEach { uuid ->
            val deleted = dao.getExercise(uuid)
            assertEquals(null, deleted)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `delete all by training uuid`() = runTest {
        val trainingUuid = Uuid.random()
        val exercisesWithTraining = Array(3) { index ->
            createTestExercise(index).copy(trainingUuid = trainingUuid)
        }.toList()
        val exercisesWithoutTraining = Array(2) { index ->
            createTestExercise(index + 3)
        }.toList()

        exercisesWithTraining.forEach { dao.create(it) }
        exercisesWithoutTraining.forEach { dao.create(it) }

        dao.deleteAllByTraining(trainingUuid)

        exercisesWithTraining.forEach { exercise ->
            val deleted = dao.getExercise(exercise.uuid)
            assertEquals(null, deleted)
        }

        exercisesWithoutTraining.forEach { exercise ->
            val actual = dao.getExercise(exercise.uuid)
            assertEquals(exercise, actual)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `delete all by multiple training uuids`() = runTest {
        val trainingUuid1 = Uuid.random()
        val trainingUuid2 = Uuid.random()

        val exercisesWithTraining1 = Array(2) { index ->
            createTestExercise(index).copy(trainingUuid = trainingUuid1)
        }.toList()
        val exercisesWithTraining2 = Array(2) { index ->
            createTestExercise(index + 2).copy(trainingUuid = trainingUuid2)
        }.toList()
        val exercisesWithoutTraining = Array(2) { index ->
            createTestExercise(index + 4)
        }.toList()

        exercisesWithTraining1.forEach { dao.create(it) }
        exercisesWithTraining2.forEach { dao.create(it) }
        exercisesWithoutTraining.forEach { dao.create(it) }

        dao.deleteAllByTrainings(listOf(trainingUuid1, trainingUuid2))

        (exercisesWithTraining1 + exercisesWithTraining2).forEach { exercise ->
            val deleted = dao.getExercise(exercise.uuid)
            assertEquals(null, deleted)
        }

        exercisesWithoutTraining.forEach { exercise ->
            val actual = dao.getExercise(exercise.uuid)
            assertEquals(exercise, actual)
        }
    }

    @Test
    fun `delete all by empty training uuids list`() = runTest {
        exerciseEntities.forEach { dao.create(it) }

        dao.deleteAllByTrainings(emptyList())

        exerciseEntities.forEach { exercise ->
            val actual = dao.getExercise(exercise.uuid)
            assertEquals(exercise, actual)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `delete all by non-existing training uuid`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val nonExistingTrainingUuid = Uuid.random()

        dao.deleteAllByTraining(nonExistingTrainingUuid)

        exerciseEntities.forEach { exercise ->
            val actual = dao.getExercise(exercise.uuid)
            assertEquals(exercise, actual)
        }
    }

    @Test
    fun `get by uuids returns matching exercises`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val expectedUuids = exerciseEntities.take(3).map { it.uuid }
        val expectedExercises = exerciseEntities.take(3)

        val actual = dao.getByUuids(expectedUuids)

        assertEquals(expectedExercises.size, actual?.size)
        expectedExercises.forEach { expected ->
            assertTrue(actual?.contains(expected) == true)
        }
    }

    @Test
    fun `get by uuids with empty list returns empty result`() = runTest {
        exerciseEntities.forEach { dao.create(it) }

        val actual = dao.getByUuids(emptyList())

        assertTrue(actual?.isEmpty() == true)
    }

    @Test
    fun `get by uuids with non-existing uuids returns empty result`() = runTest {
        exerciseEntities.forEach { dao.create(it) }
        val nonExistingUuids = listOf(
            Uuid.random(),
            Uuid.random(),
            Uuid.random(),
        )

        val actual = dao.getByUuids(nonExistingUuids)

        assertTrue(actual?.isEmpty() == true)
    }

    @Test
    fun `get by uuids with mix of existing and non-existing uuids returns only existing`() =
        runTest {
            exerciseEntities.forEach { dao.create(it) }
            val existingUuids = exerciseEntities.take(2).map { it.uuid }
            val nonExistingUuids = listOf(Uuid.random())
            val mixedUuids = existingUuids + nonExistingUuids

            val actual = dao.getByUuids(mixedUuids)

            assertEquals(existingUuids.size, actual?.size)
            existingUuids.forEach { uuid ->
                assertTrue(actual?.any { it.uuid == uuid } == true)
            }
        }

    private fun createTestExercise(
        index: Int = 0,
    ): ExerciseEntity = ExerciseEntity(
        name = "test_$index",
        sets = listOf(),
        labels = listOf(),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )
}
