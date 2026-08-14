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
 *
 * Shared rather than per-class on purpose: every class in this suite has to get somewhere before it
 * can assert anything, so the alternative is four copies of the same inserts drifting apart.
 *
 * Two rules the schema enforces and a caller cannot see from the call site:
 *  - **`SessionEntity.trainingUuid` is a non-null FK to `training_table`.** A session seeded without
 *    a parent training throws `SQLiteConstraintException` at INSERT, not at assert — so
 *    [finishedSession] always mints the training itself rather than taking one optionally.
 *  - **`is_adhoc = 0` is what makes an exercise visible.** Every user-facing list query filters it
 *    (`ExerciseDao.pagedActive`, `pagedActiveWithStats`). [exercise] therefore takes the default
 *    `isAdhoc = false` and a caller who wants an invisible row has to say so explicitly.
 *
 * Inserts run through [runBlocking] because every DAO insert is `suspend`. This is setup on the test
 * thread before the UI is driven, so there is no dispatcher to interfere with.
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
     * A library exercise visible in AllExercises.
     *
     * [imagePath] is what makes the exercise's thumbnail navigate rather than sit inert: the click
     * handler returns early on `ImageDisplay.None`, so an exercise with no image cannot reach the
     * image viewer at all. The path does not have to resolve — `FakeImageStorage` never touches
     * disk, and the viewer's arrival is asserted on its graph tag, not on a decoded bitmap.
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
     * The four rows a finished session needs to be visible in Home's recent list and loadable by
     * PastSession: training (FK parent) -> session -> performed exercise -> one set.
     *
     * The set is not decoration. `ExerciseDao.getRecentlyTrainedExercises` gates on an `EXISTS` over
     * `set_table`, so an exercise with a performed row but no sets has no chart history.
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

        // Fixed rather than "now": a seed that moves with the wall clock makes a relative-time
        // label ("2 minutes ago") change under the test, and the failure reads as flakiness.
        //
        // Know what "fixed" buys, though: it stops drift WITHIN a run, not across the calendar.
        // These epochs are a moment in 2023, so any relative-time meta string rendered from them
        // ("2 minutes ago") is already "years ago" and changes again every month with no code
        // change. Nothing breaks today only because every selector in this suite is name-based.
        //
        // The rule that keeps it that way: NEVER assert on a relative-time meta string. Such an
        // assertion is green on the day it is written and red on a calendar schedule. Assert on
        // seeded names (unique per test) or on absolute values the seed controls.
        const val FIXED_CREATED_AT: Long = 1_700_000_000_000L
        const val FIXED_STARTED_AT: Long = 1_700_000_100_000L
        const val FIXED_FINISHED_AT: Long = 1_700_000_200_000L

        const val SEEDED_REPS: Int = 8
        const val SEEDED_WEIGHT: Double = 40.0
    }
}
