package io.github.stslex.workeeper.core.exercise

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ExerciseRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<ExerciseDao>()
    private val repository: ExerciseRepository = ExerciseRepositoryImpl(
        dao = dao,
        bgDispatcher = testDispatcher,
    )

    @Test
    fun `get all items paging`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntites = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDomain(0, uuid1),
            createDomain(1, uuid2),
        )

        every { dao.getAll() } returns TestPagingSource(expectedEntites)

        val items = repository.exercises.asSnapshot()
        verify(exactly = 1) { dao.getAll() }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `get all items with query paging`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntites = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDomain(0, uuid1),
            createDomain(1, uuid2),
        )

        every { dao.getAll("some_query") } returns TestPagingSource(expectedEntites)

        val items = repository.getExercises("some_query").asSnapshot()
        verify(exactly = 1) { dao.getAll("some_query") }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `get unique items with query paging`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntites = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDomain(0, uuid1),
            createDomain(1, uuid2),
        )

        every { dao.getAllUnique("some_query") } returns TestPagingSource(expectedEntites)

        val items = repository.getUniqueExercises("some_query").asSnapshot()
        verify(exactly = 1) { dao.getAllUnique("some_query") }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `get items with name from to date`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntites = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDomain(0, uuid1),
            createDomain(1, uuid2),
        )

        coEvery { dao.getExercises("name", 100L, 200L) } returns expectedEntites

        val items = repository.getExercises("name", 100L, 200L)
        coVerify(exactly = 1) { dao.getExercises("name", 100L, 200L) }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `save item`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val domain = createChangeModel(0, uuid)
        val entity = createEntity(0, uuid)
        coEvery { dao.create(exercise = any<ExerciseEntity>()) } answers {}

        repository.saveItem(domain)
        coVerify(exactly = 1) { dao.create(entity) }
    }

    @Test
    fun `delete item`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        coEvery { dao.delete(any<Uuid>()) } answers {}

        repository.deleteItem(uuid.toString())
        coVerify(exactly = 1) { dao.delete(uuid) }
    }

    @Test
    fun `search items`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val searchItem = createEntity(0, uuid)
        val domainItem = createDomain(0, uuid)
        val query = "tes"

        coEvery { dao.searchUniqueExclude(query) } returns listOf(searchItem)

        val result = repository.searchItemsWithExclude(query)
        coVerify(exactly = 1) { dao.searchUniqueExclude(query) }
        assertEquals(listOf(domainItem), result)
    }

    @Test
    fun `get exercise by uuid`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val entity = createEntity(0, uuid)
        val domain = createDomain(0, uuid)

        coEvery { dao.getExercise(uuid) } returns entity

        val result = repository.getExercise(uuid.toString())
        coVerify(exactly = 1) { dao.getExercise(uuid) }
        assertEquals(domain, result)
    }

    @Test
    fun `get exercise by uuid returns null when not found`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        coEvery { dao.getExercise(uuid) } returns null

        val result = repository.getExercise(uuid.toString())
        coVerify(exactly = 1) { dao.getExercise(uuid) }
        assertEquals(null, result)
    }

    @Test
    fun `delete all items`() = runTest(testDispatcher) {
        val uuids = listOf(Uuid.random(), Uuid.random(), Uuid.random())

        coEvery { dao.delete(uuids) } answers {}

        repository.deleteAllItems(uuids)
        coVerify(exactly = 1) { dao.delete(uuids) }
    }

    @Test
    fun `delete by training uuid`() = runTest(testDispatcher) {
        val trainingUuid = Uuid.random()

        coEvery { dao.deleteAllByTraining(trainingUuid) } answers {}

        repository.deleteByTrainingUuid(trainingUuid.toString())
        coVerify(exactly = 1) { dao.deleteAllByTraining(trainingUuid) }
    }

    @Test
    fun `delete by trainings uuids`() = runTest(testDispatcher) {
        val trainingsUuids = listOf(Uuid.random(), Uuid.random())

        coEvery { dao.deleteAllByTrainings(trainingsUuids) } answers {}

        repository.deleteByTrainingsUuids(trainingsUuids.map { it.toString() })
        coVerify(exactly = 1) { dao.deleteAllByTrainings(trainingsUuids) }
    }

    private class TestPagingSource(
        private val expectedEntites: List<ExerciseEntity>,
    ) : PagingSource<Int, ExerciseEntity>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExerciseEntity> =
            LoadResult.Page(
                data = expectedEntites,
                prevKey = null as Int?,
                nextKey = null as Int?,
            )

        override fun getRefreshKey(state: PagingState<Int, ExerciseEntity>): Int? = null
    }

    private fun createChangeModel(
        @Suppress("SameParameterValue") index: Int,
        uuid: Uuid,
    ) = ExerciseChangeDataModel(
        uuid = uuid.toString(),
        name = "test$index",
        sets = Array(index) {
            SetsDataModel(
                uuid = Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", it + index)}")
                    .toString(),
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsDataType.WORK,
            )
        }.toList(),
        labels = listOf(index.toString()),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )

    private fun createDomain(
        index: Int,
        uuid: Uuid,
    ) = ExerciseDataModel(
        uuid = uuid.toString(),
        name = "test$index",
        sets = Array(index) {
            SetsDataModel(
                uuid = Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", it + index)}")
                    .toString(),
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsDataType.WORK,
            )
        }.toList(),
        labels = listOf(index.toString()),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )

    private fun createEntity(
        index: Int = 0,
        uuid: Uuid = Uuid.random(),
    ) = ExerciseEntity(
        uuid = uuid,
        trainingUuid = null,
        labels = listOf(index.toString()),
        sets = Array(index) {
            SetsEntity(
                uuid = Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", it + index)}"),
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsEntityType.WORK,
            )
        }.toList(),
        name = "test$index",
        timestamp = index.plus(123).toLong(),
    )
}
