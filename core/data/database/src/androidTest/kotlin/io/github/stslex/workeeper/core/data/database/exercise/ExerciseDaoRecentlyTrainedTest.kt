// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * Instrumented coverage of the v2.2 chart picker query. Exercises real Android SQLite so
 * the query's behavior matches what ships on devices, not what Robolectric's bundled
 * SQLite would do.
 *
 * Each case constructs minimal seed data via the project DAOs (no raw SQL), then calls
 * [ExerciseDao.getRecentlyTrainedExercises] and asserts the resulting picker rows.
 */
@RunWith(AndroidJUnit4::class)
internal class ExerciseDaoRecentlyTrainedTest {

    private lateinit var database: AppDatabase
    private val exerciseDao get() = database.exerciseDao
    private val sessionDao get() = database.sessionDao
    private val performedExerciseDao get() = database.performedExerciseDao
    private val setDao get() = database.setDao
    private val trainingDao get() = database.trainingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyDb_returnsEmptyList() = runBlocking {
        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun finishedSessionWithLoggedSets_isIncluded() = runBlocking {
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(exercise, rows.first().uuid)
        assertEquals(1_000L, rows.first().lastFinishedAt)
    }

    @Test
    fun archivedExercise_isExcluded_evenWhenSetsAreLogged() = runBlocking {
        val training = Uuid.random()
        val active = Uuid.random()
        val archived = Uuid.random()
        seedTraining(training)
        seedExercise(active, "Bench")
        seedExercise(archived, "Squat", archived = true)
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = active,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 80.0, reps = 5)),
        )
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = archived,
            finishedAt = 5_000L,
            sets = listOf(SetSpec(weight = 120.0, reps = 3)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(listOf(active), rows.map { it.uuid })
    }

    @Test
    fun inProgressSessionOnly_isExcluded() = runBlocking {
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedSession(
            trainingUuid = training,
            exerciseUuid = exercise,
            state = SessionStateEntity.IN_PROGRESS,
            finishedAt = null,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
        )

        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun finishedSessionWithNullFinishedAt_isExcluded() = runBlocking {
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        // A FINISHED row with NULL finished_at is physically possible (old data, broken
        // migrations); the query must defend against it because MAX(finished_at) over a
        // group containing only NULL would otherwise yield a NULL last_finished_at.
        seedSession(
            trainingUuid = training,
            exerciseUuid = exercise,
            state = SessionStateEntity.FINISHED,
            finishedAt = null,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
        )

        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun skippedPerformedRowWithSets_isExcluded() = runBlocking {
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
            skipped = true,
        )

        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun unskippedPerformedRowWithoutSets_isExcluded() = runBlocking {
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = emptyList(),
        )

        assertTrue(exerciseDao.getRecentlyTrainedExercises().isEmpty())
    }

    @Test
    fun mixedPerformedRows_validRowMoreRecent_includesOnceWithValidFinishedAt() = runBlocking {
        // Row A: skipped — invalid. Row B: valid, more recent.
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
            skipped = true,
        )
        val validSession = seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 5_000L,
            sets = listOf(SetSpec(weight = 120.0, reps = 3)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(5_000L, rows.first().lastFinishedAt)
        // Sanity: the seed helper returned a freshly minted session UUID.
        assertTrue(validSession.toString().isNotEmpty())
    }

    @Test
    fun mixedPerformedRows_invalidRowMoreRecent_lastFinishedAtMatchesValidRow() = runBlocking {
        // Row A: valid, older. Row B: skipped (invalid), more recent. The MAX must come
        // from the valid row, not the skipped one — that's the whole point of filtering
        // before the aggregate.
        val training = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(training)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 5_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
            skipped = true,
        )
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 90.0, reps = 5)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(1_000L, rows.first().lastFinishedAt)
    }

    @Test
    fun multipleValidExercises_orderedByLastFinishedAtDesc() = runBlocking {
        val training = Uuid.random()
        val exA = Uuid.random()
        val exB = Uuid.random()
        val exC = Uuid.random()
        seedTraining(training)
        seedExercise(exA, "A")
        seedExercise(exB, "B")
        seedExercise(exC, "C")
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exA,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 80.0, reps = 5)),
        )
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exB,
            finishedAt = 5_000L,
            sets = listOf(SetSpec(weight = 90.0, reps = 5)),
        )
        seedFinishedSessionWithSets(
            trainingUuid = training,
            exerciseUuid = exC,
            finishedAt = 3_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(listOf(exB, exC, exA), rows.map { it.uuid })
        assertEquals(listOf(5_000L, 3_000L, 1_000L), rows.map { it.lastFinishedAt })
    }

    @Test
    fun adhocAndRegularTrainings_bothContribute() = runBlocking {
        val regular = Uuid.random()
        val adhoc = Uuid.random()
        val exercise = Uuid.random()
        seedTraining(regular, isAdhoc = false)
        seedTraining(adhoc, isAdhoc = true)
        seedExercise(exercise, "Bench")
        seedFinishedSessionWithSets(
            trainingUuid = regular,
            exerciseUuid = exercise,
            finishedAt = 1_000L,
            sets = listOf(SetSpec(weight = 100.0, reps = 5)),
        )
        seedFinishedSessionWithSets(
            trainingUuid = adhoc,
            exerciseUuid = exercise,
            finishedAt = 5_000L,
            sets = listOf(SetSpec(weight = 110.0, reps = 3)),
        )

        val rows = exerciseDao.getRecentlyTrainedExercises()

        assertEquals(1, rows.size)
        assertEquals(5_000L, rows.first().lastFinishedAt)
    }

    private suspend fun seedTraining(uuid: Uuid, isAdhoc: Boolean = false) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = "T",
                description = null,
                isAdhoc = isAdhoc,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
    }

    private suspend fun seedExercise(uuid: Uuid, name: String, archived: Boolean = false) {
        exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = archived,
                createdAt = 0L,
                archivedAt = if (archived) 0L else null,
                lastAdhocSets = null,
            ),
        )
    }

    private suspend fun seedFinishedSessionWithSets(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        finishedAt: Long,
        sets: List<SetSpec>,
        skipped: Boolean = false,
    ): Uuid = seedSession(
        trainingUuid = trainingUuid,
        exerciseUuid = exerciseUuid,
        state = SessionStateEntity.FINISHED,
        finishedAt = finishedAt,
        sets = sets,
        skipped = skipped,
    )

    private suspend fun seedSession(
        trainingUuid: Uuid,
        exerciseUuid: Uuid,
        state: SessionStateEntity,
        finishedAt: Long?,
        sets: List<SetSpec>,
        skipped: Boolean = false,
    ): Uuid {
        val sessionUuid = Uuid.random()
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = state,
                startedAt = 0L,
                finishedAt = finishedAt,
            ),
        )
        val performedUuid = Uuid.random()
        performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = performedUuid,
                    sessionUuid = sessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = skipped,
                ),
            ),
        )
        sets.forEachIndexed { index, spec ->
            setDao.insert(
                SetEntity(
                    uuid = Uuid.random(),
                    performedExerciseUuid = performedUuid,
                    position = index,
                    reps = spec.reps,
                    weight = spec.weight,
                    type = SetTypeEntity.WORK,
                ),
            )
        }
        return sessionUuid
    }

    private data class SetSpec(val weight: Double?, val reps: Int)
}
