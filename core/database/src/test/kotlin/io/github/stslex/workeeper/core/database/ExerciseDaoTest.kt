package io.github.stslex.workeeper.core.database

import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import kotlinx.coroutines.flow.first
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
    fun `get all items query with full database and empty query`() = runTest {
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll("")
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
    fun `get all items query with full database and not existed query`() = runTest {
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll("not_existed_query")
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isEmpty())
        assertEquals(0, actual?.size)
    }

    @Test
    fun `get all items query with full database and particularly existed query`() = runTest {
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll(exerciseEntities.first().name)
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size + 10,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(1, actual?.size)
    }

    @Test
    fun `get all items query with full database and all existed query`() = runTest {
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll("test")
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
    fun `get all items full database with query from date to date matching all props`() = runTest {
        dao.create(exerciseEntities)
        val items = dao.getExercises("test", 0, Long.MAX_VALUE).first().reversed()
        assertEquals(exerciseEntities, items)
    }

    @Test
    fun `get all items full database with query from date to date not matching name`() = runTest {
        dao.create(exerciseEntities)
        val items = dao.getExercises("test_not_existed", 0, Long.MAX_VALUE).first().reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with query from date to date not matching date`() = runTest {
        dao.create(exerciseEntities)
        val items = dao.getExercises("test", 0, 1).first().reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with query from date to date not matching date and name`() = runTest {
        dao.create(exerciseEntities)
        val items = dao.getExercises("test_not_existed", 0, 1).first().reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with exactly query from date to date not matching props`() = runTest {
        dao.create(exerciseEntities)
        val items = dao.getExercisesExactly("test", 0, Long.MAX_VALUE).first().reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
    }

    @Test
    fun `get all items full database with exactly query from date to date matching prop`() = runTest {
        val firstEntity = exerciseEntities.first()
        dao.create(exerciseEntities)
        val items = dao.getExercisesExactly(firstEntity.name, 0, Long.MAX_VALUE).first().reversed()
        assertEquals(listOf(firstEntity), items)
    }

    @Test
    fun `get all items full database with exactly query from date to date not matching date`() = runTest {
        val firstEntity = exerciseEntities.first()
        dao.create(exerciseEntities)
        val items = dao.getExercisesExactly(firstEntity.name, 0, 1).first().reversed()
        assertEquals(emptyList<ExerciseEntity>(), items)
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

    @Test
    fun `get all items query with empty database`() = runTest {
        val pagingSource = dao.getAll("")
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
    fun `update exist item`() = runTest {
        val item = createTestExercise()
        val expectedNewItem = createTestExercise().copy(
            uuid = item.uuid
        )
        dao.create(item)
        val createdItem = dao.getExercise(item.uuid)
        assertEquals(item, createdItem)

        dao.update(expectedNewItem)
        val createdNewItem = dao.getExercise(item.uuid)
        assertEquals(expectedNewItem, createdNewItem)
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
    fun `search unique results success`() = runTest {
        val item1 = createTestExercise(0).copy(name = "name_1")
        val item2 = createTestExercise(1).copy(name = "other_title")
        val item3 = createTestExercise(2).copy(name = "other_title")
        val item4 = createTestExercise(3).copy(name = "search_title")

        dao.create(item1)
        dao.create(item2)
        dao.create(item3)
        dao.create(item4)

        val results = dao.searchUnique("title")
        assertEquals(listOf(item4, item3), results)

        val results2 = dao.searchUnique("other")
        assertEquals(listOf(item3), results2)

        val results3 = dao.searchUnique("_")
        assertEquals(listOf(item4, item3, item1), results3)

        val results4 = dao.searchUnique("1")
        assertEquals(listOf(item1), results4)

        val results5 = dao.searchUnique("name_123")
        assertTrue(results5.isEmpty())

        val results6 = dao.searchUnique("other_title")
        assertEquals(listOf(item3), results6)
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
        name = "test_$index",
        sets = index.inc(),
        reps = index.plus(2),
        weight = index.plus(3).toDouble(),
        timestamp = index.plus(123).toLong(),
    )

}
