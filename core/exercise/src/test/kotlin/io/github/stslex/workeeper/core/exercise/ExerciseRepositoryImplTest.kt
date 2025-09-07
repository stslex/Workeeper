package io.github.stslex.workeeper.core.exercise

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.data.ExerciseRepositoryImpl
import io.github.stslex.workeeper.core.exercise.data.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
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

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExerciseEntity> = LoadResult.Page(
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
        sets = index.plus(1),
        reps = index.plus(2),
        weight = index.plus(3).toDouble(),
        timestamp = index.plus(123).toLong(),
    )

    private fun createDomain(
        index: Int,
        uuid: Uuid
    ) = ExerciseDataModel(
        uuid = uuid.toString(),
        name = "test$index",
        sets = index.plus(1),
        reps = index.plus(2),
        weight = index.plus(3).toDouble(),
        timestamp = index.plus(123).toLong(),
    )

    private fun createEntity(
        index: Int = 0,
        uuid: Uuid = Uuid.random()
    ) = ExerciseEntity(
        uuid = uuid,
        name = "test$index",
        sets = index.plus(1),
        reps = index.plus(2),
        weight = index.plus(3).toDouble(),
        timestamp = index.plus(123).toLong(),
    )
}
