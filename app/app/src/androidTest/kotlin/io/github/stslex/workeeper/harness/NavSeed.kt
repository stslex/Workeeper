// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

/**
 * Row seeding for the navigation regression suite, over [MetroTestRule]'s in-memory database.
 * A session needs its FK parent training, and only `is_adhoc = 0` exercises are list-visible.
 */
@OptIn(ExperimentalUuidApi::class)
internal class NavSeed(private val db: AppDatabase) {

    /** A training visible in AllTrainings. Returns the uuid, which is also the row's tag suffix. */
    fun training(name: String): Uuid = runBlocking {
        val uuid = Uuid.random()
        db.trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = name,
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = FIXED_CREATED_AT,
                archivedAt = null,
            ),
        )
        uuid
    }

    /**
     * A library exercise visible in AllExercises. [imagePath] is what makes its thumbnail navigate;
     * the path need not resolve, since `FakeImageStorage` never touches disk.
     */
    fun exercise(
        name: String,
        imagePath: String? = null,
    ): Uuid = runBlocking {
        val uuid = Uuid.random()
        db.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = imagePath,
                archived = false,
                createdAt = FIXED_CREATED_AT,
                archivedAt = null,
                lastAdhocSets = null,
                isAdhoc = false,
            ),
        )
        uuid
    }

    /**
     * The four rows a finished session needs: training (FK parent) -> session -> performed exercise
     * -> one set. The set is required: recent-exercise queries gate on `EXISTS` over `set_table`.
     */
    fun finishedSession(exerciseName: String, trainingName: String): FinishedSession = runBlocking {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        val sessionUuid = Uuid.random()
        val performedUuid = Uuid.random()

        db.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = trainingName,
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = FIXED_CREATED_AT,
                archivedAt = null,
            ),
        )
        db.exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = exerciseName,
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = FIXED_CREATED_AT,
                archivedAt = null,
                lastAdhocSets = null,
                isAdhoc = false,
            ),
        )
        db.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = FIXED_STARTED_AT,
                finishedAt = FIXED_FINISHED_AT,
            ),
        )
        db.performedExerciseDao.insert(
            PerformedExerciseEntity(
                uuid = performedUuid,
                sessionUuid = sessionUuid,
                exerciseUuid = exerciseUuid,
                position = 0,
                skipped = false,
            ),
        )
        db.setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = performedUuid,
                position = 0,
                reps = SEEDED_REPS,
                weight = SEEDED_WEIGHT,
                type = SetTypeEntity.WORK,
            ),
        )
        FinishedSession(
            sessionUuid = sessionUuid,
            exerciseUuid = exerciseUuid,
            trainingUuid = trainingUuid,
        )
    }

    /** Uuids of a seeded finished session, in the form the per-row testTags interpolate. */
    internal data class FinishedSession(
        val sessionUuid: Uuid,
        val exerciseUuid: Uuid,
        val trainingUuid: Uuid,
    )

    private companion object {

        // GUARD: these are fixed 2023 epochs — never assert on a relative-time label rendered from
        // them; it goes red on a calendar schedule. See documentation/testing.md.
        const val FIXED_CREATED_AT: Long = 1_700_000_000_000L
        const val FIXED_STARTED_AT: Long = 1_700_000_100_000L
        const val FIXED_FINISHED_AT: Long = 1_700_000_200_000L

        const val SEEDED_REPS: Int = 8
        const val SEEDED_WEIGHT: Double = 40.0
    }
}
