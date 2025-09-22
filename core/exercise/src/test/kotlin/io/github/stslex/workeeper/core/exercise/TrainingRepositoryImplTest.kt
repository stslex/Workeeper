package io.github.stslex.workeeper.core.exercise

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import io.github.stslex.workeeper.core.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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

internal class TrainingRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<TrainingDao>()
    private val repository: TrainingRepository = TrainingRepositoryImpl(
        dao = dao,
        ioDispatcher = testDispatcher
    )

    @BeforeEach
    fun bindMain() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun unbindMain() = Dispatchers.resetMain()

    @Test
    fun `get trainings with query paging`() = runTest(testDispatcher) {
        val uuid1 = Uuid.random()
        val uuid2 = Uuid.random()
        val expectedEntities = listOf(
            createEntity(0, uuid1),
            createEntity(1, uuid2),
        )
        val expectedDataModels = listOf(
            createDataModel(0, uuid1),
            createDataModel(1, uuid2)
        )

        every { dao.getAll("some_query") } returns TestPagingSource(expectedEntities)

        val items = repository.getTrainings("some_query").asSnapshot()
        verify(exactly = 1) { dao.getAll("some_query") }

        assertEquals(expectedDataModels, items)
    }

    @Test
    fun `add training`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val dataModel = createDataModel(0, uuid)
        val entity = createEntity(0, uuid)

        coEvery { dao.add(entity) } answers {}

        repository.addTraining(dataModel)
        coVerify(exactly = 1) { dao.add(entity) }
    }

    @Test
    fun `update training`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val changeModel = createChangeModel(0, uuid)
        val entity = createEntity(0, uuid)

        coEvery { dao.add(entity) } answers {}

        repository.updateTraining(changeModel)
        coVerify(exactly = 1) { dao.add(entity) }
    }

    @Test
    fun `remove training`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        coEvery { dao.delete(uuid) } answers {}

        repository.removeTraining(uuid.toString())
        coVerify(exactly = 1) { dao.delete(uuid) }
    }

    @Test
    fun `get training by uuid`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val entity = createEntity(0, uuid)
        val dataModel = createDataModel(0, uuid)

        coEvery { dao.get(uuid) } returns entity

        val result = repository.getTraining(uuid.toString())
        coVerify(exactly = 1) { dao.get(uuid) }
        assertEquals(dataModel, result)
    }

    @Test
    fun `get training by uuid returns null when not found`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        coEvery { dao.get(uuid) } returns null

        val result = repository.getTraining(uuid.toString())
        coVerify(exactly = 1) { dao.get(uuid) }
        assertEquals(null, result)
    }

    @Test
    @Suppress("UnusedFlow")
    fun `get training flow uuid`() = runTest(testDispatcher) {
        val uuid = Uuid.random()
        val entity = createEntity(0, uuid)
        val dataModel = createDataModel(0, uuid)

        coEvery { dao.subscribeForTraining(uuid) } returns flowOf(entity)

        val result = repository.subscribeForTraining(uuid.toString()).firstOrNull()
        coVerify(exactly = 1) { dao.subscribeForTraining(uuid) }
        assertEquals(dataModel, result)
    }

    @Test
    @Suppress("UnusedFlow")
    fun `get training flow by uuid returns null when not found`() = runTest(testDispatcher) {
        val uuid = Uuid.random()

        coEvery { dao.subscribeForTraining(uuid) } returns flowOf()

        val result = repository.subscribeForTraining(uuid.toString()).firstOrNull()

        coVerify(exactly = 1) { dao.subscribeForTraining(uuid) }
        assertEquals(null, result)
    }

    @Test
    fun `remove all trainings`() = runTest(testDispatcher) {
        val uuids = listOf(Uuid.random(), Uuid.random(), Uuid.random())

        coEvery { dao.deleteAll(uuids) } answers {}

        repository.removeAll(uuids.map { it.toString() })
        coVerify(exactly = 1) { dao.deleteAll(uuids) }
    }

    private class TestPagingSource(
        private val expectedEntities: List<TrainingEntity>
    ) : PagingSource<Int, TrainingEntity>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrainingEntity> =
            LoadResult.Page(
                data = expectedEntities,
                prevKey = null as Int?,
                nextKey = null as Int?
            )

        override fun getRefreshKey(state: PagingState<Int, TrainingEntity>): Int? = null
    }

    private fun createDataModel(
        index: Int,
        uuid: Uuid
    ): TrainingDataModel {
        val exerciseUuid1 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2)}")
        val exerciseUuid2 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2 + 1)}")
        return TrainingDataModel(
            uuid = uuid.toString(),
            name = "Training_$index",
            exerciseUuids = listOf(exerciseUuid1.toString(), exerciseUuid2.toString()),
            labels = listOf("Label_$index", "Test"),
            timestamp = index.plus(123).toLong()
        )
    }

    private fun createChangeModel(
        @Suppress("SameParameterValue") index: Int,
        uuid: Uuid
    ): TrainingChangeDataModel {
        val exerciseUuid1 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2)}")
        val exerciseUuid2 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2 + 1)}")
        return TrainingChangeDataModel(
            uuid = uuid.toString(),
            name = "Training_$index",
            exerciseUuids = listOf(exerciseUuid1.toString(), exerciseUuid2.toString()),
            labels = listOf("Label_$index", "Test"),
            timestamp = index.plus(123).toLong()
        )
    }

    private fun createEntity(
        index: Int = 0,
        uuid: Uuid = Uuid.random()
    ): TrainingEntity {
        val exerciseUuid1 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2)}")
        val exerciseUuid2 =
            Uuid.parse("00000000-0000-0000-0000-${String.format("%012d", index * 2 + 1)}")
        return TrainingEntity(
            uuid = uuid,
            name = "Training_$index",
            exercises = listOf(exerciseUuid1, exerciseUuid2),
            labels = listOf("Label_$index", "Test"),
            timestamp = index.plus(123).toLong()
        )
    }
}