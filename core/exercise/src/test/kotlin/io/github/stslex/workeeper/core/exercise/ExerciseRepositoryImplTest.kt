package io.github.stslex.workeeper.core.exercise

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.exercise.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

internal class ExerciseRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<ExerciseDao>()
    private val repository: ExerciseRepository = ExerciseRepositoryImpl(
        appDispatcher = mockk<AppDispatcher>(relaxed = true) { every { io } returns testDispatcher },
        dao = dao
    )

    @BeforeEach
    fun bindMain() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun unbindMain() = Dispatchers.resetMain()

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
            createDomain(1, uuid2)
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
            createDomain(1, uuid2)
        )

        every { dao.getAll("some_query") } returns TestPagingSource(expectedEntites)

        val items = repository.getExercises("some_query").asSnapshot()
        verify(exactly = 1) { dao.getAll("some_query") }

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
            createDomain(1, uuid2)
        )

        coEvery { dao.getExercises("name", 100L, 200L) } returns flowOf(expectedEntites)

        val items = repository.getExercises("name", 100L, 200L).first()
        coVerify(exactly = 1) { dao.getExercises("name", 100L, 200L) }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `get items with name from to date exactly`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntites = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDomain(0, uuid1),
            createDomain(1, uuid2)
        )

        coEvery { dao.getExercisesExactly("name", 100L, 200L) } returns flowOf(expectedEntites)

        val items = repository.getExercisesExactly("name", 100L, 200L).first()
        coVerify(exactly = 1) { dao.getExercisesExactly("name", 100L, 200L) }

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

        val result = repository.searchItems(query)
        coVerify(exactly = 1) { dao.searchUniqueExclude(query) }
        assertEquals(listOf(domainItem), result)
    }

    private class TestPagingSource(
        private val expectedEntites: List<ExerciseEntity>
    ) : PagingSource<Int, ExerciseEntity>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExerciseEntity> =
            LoadResult.Page(
                data = expectedEntites,
                prevKey = null as Int?,
                nextKey = null as Int?
            )

        override fun getRefreshKey(state: PagingState<Int, ExerciseEntity>): Int? = null
    }

    private fun createChangeModel(
        index: Int,
        uuid: Uuid
    ) = ChangeExerciseDataModel(
        uuid = uuid.toString(),
        name = "test$index",
        sets = Array(index) {
            SetsDataModel(
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsDataType.WORK
            )
        }.toList(),
        labels = listOf(index.toString()),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )

    private fun createDomain(
        index: Int,
        uuid: Uuid
    ) = ExerciseDataModel(
        uuid = uuid.toString(),
        name = "test$index",
        sets = Array(index) {
            SetsDataModel(
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsDataType.WORK
            )
        }.toList(),
        labels = listOf(index.toString()),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )

    private fun createEntity(
        index: Int = 0,
        uuid: Uuid = Uuid.random()
    ) = ExerciseEntity(
        uuid = uuid,
        name = "test$index",
        sets = Array(index) {
            SetsEntity(
                reps = it + index,
                weight = it + index.toDouble(),
                type = SetsEntityType.WORK
            )
        }.toList(),
        labels = listOf(index.toString()),
        trainingUuid = null,
        timestamp = index.plus(123).toLong(),
    )
}
