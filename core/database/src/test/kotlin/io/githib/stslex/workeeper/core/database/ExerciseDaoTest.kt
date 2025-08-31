package io.githib.stslex.workeeper.core.database

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
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(exerciseEntities.size, actual?.size)
    }

    @Test
    fun `get all items with empty database`() = runTest {
        val pagingSource = dao.getAll()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false
            )
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
        dao.create(expectedItems)
        val actual = expectedItems.map {
            dao.getExercise(it.uuid)
        }
        assertEquals(expectedItems, actual)
    }

    @Test
    fun `clear all items`() = runTest {
        val expectedItem = createTestExercise()
        dao.create(exerciseEntities)
        dao.clear()
        val actual = dao.getExercise(expectedItem.uuid)
        assertEquals(null, actual)
    }

    private fun createTestExercise(
        index: Int = 0
    ): ExerciseEntity = ExerciseEntity(
        name = "test",
        sets = index.inc(),
        reps = index.plus(2),
        weight = index.plus(3),
        timestamp = index.plus(123).toLong(),
    )

}