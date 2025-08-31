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

    @BeforeEach
    override fun initDb() {
        super.initDb()
    }

    @AfterEach
    override fun clearDb() {
        super.clearDb()
    }

    @Test
    fun getAll() = runTest {
        dao.create(exerciseEntities)
        val pagingSource = dao.getAll()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size,
                placeholdersEnabled = false
            )
        )
        val actual = (loadResult as? PagingSource.LoadResult.Page)?.data
        assertTrue(actual.orEmpty().isNotEmpty())
        assertEquals(actual?.size, exerciseEntities.size)

    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun getItem() = runTest {
        val expectedExercise = exerciseEntities.first()
        dao.create(expectedExercise)
        val compareExercise = dao.getExercise(expectedExercise.uuid)
        assertEquals(expectedExercise, compareExercise)
    }

    @Test
    fun insertSingleItem() = runTest {
        val exerciseSize = dao.getAll().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size,
                placeholdersEnabled = false
            )
        ).let {
            (it as? PagingSource.LoadResult.Page)?.data
        }?.size
        val expectedExercise = exerciseEntities.first()
        dao.create(expectedExercise)
        val exerciseSizeAssert = exerciseSize?.plus(1)

        val exercisesNewSize = dao.getAll().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = exerciseEntities.size,
                placeholdersEnabled = false
            )
        ).let {
            (it as? PagingSource.LoadResult.Page)?.data
        }
        assertEquals(exerciseSizeAssert, exercisesNewSize?.size)
    }

    private val exerciseEntities: List<ExerciseEntity> by lazy {
        listOf(testExercise, testExercise, testExercise, testExercise)
    }

    @OptIn(ExperimentalUuidApi::class)
    private val List<ExerciseEntity>.containsCurrentItem: Boolean
        get() = contains(testExercise.copy(uuid = last().uuid))

    @OptIn(ExperimentalUuidApi::class)
    private val testExercise: ExerciseEntity by lazy {
        ExerciseEntity(
            uuid = Uuid.random(),
            name = "test",
            sets = 2,
            reps = 3,
            weight = 4,
            timestamp = 123,
        )
    }

}