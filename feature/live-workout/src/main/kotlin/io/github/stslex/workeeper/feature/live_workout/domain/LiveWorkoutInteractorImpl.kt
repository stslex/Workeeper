// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.sets.PlanUpdateRule
import io.github.stslex.workeeper.core.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.FinishResult
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.PerformedExerciseSnapshot
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.SessionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
@ViewModelScoped
internal class LiveWorkoutInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val performedExerciseRepository: PerformedExerciseRepository,
    private val setRepository: SetRepository,
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : LiveWorkoutInteractor {

    override suspend fun startSession(
        trainingUuid: String,
    ): String = withContext(defaultDispatcher) {
        // Reuse any in-progress session for this training so re-entry from the Trainings
        // tab does not orphan an active session by spawning a parallel one.
        val existing = sessionRepository.getAnyActiveSession()
        if (existing != null && existing.trainingUuid == trainingUuid) {
            return@withContext existing.sessionUuid
        }
        val rows = trainingExerciseRepository.getRowsForTraining(trainingUuid)
        val pairs = rows
            .sortedBy { it.position }
            .map { it.exerciseUuid to it.position }
        val session = sessionRepository.startSessionWithExercises(
            trainingUuid = trainingUuid,
            exerciseUuids = pairs,
        )
        session.uuid
    }

    override suspend fun loadSession(
        sessionUuid: String,
    ): SessionSnapshot? = withContext(defaultDispatcher) {
        val session = sessionRepository.getById(sessionUuid) ?: return@withContext null
        val training = trainingRepository.getTraining(session.trainingUuid)
        val performedRows = performedExerciseRepository.getBySession(sessionUuid)
        val exerciseTemplates = exerciseRepository
            .getExercisesByUuid(performedRows.map { it.exerciseUuid })
            .associateBy { it.uuid }
        val planByExercise = if (training?.isAdhoc == true) {
            performedRows.associate { row ->
                row.exerciseUuid to exerciseRepository.getAdhocPlan(row.exerciseUuid)
            }
        } else {
            performedRows.associate { row ->
                val trainingPlan = trainingExerciseRepository.getPlan(
                    trainingUuid = session.trainingUuid,
                    exerciseUuid = row.exerciseUuid,
                )
                // Read-time fallback: trainings created before the write-time fix may have
                // null planSets even when the exercise has its own default. Recover here so
                // existing user data isn't stranded. Empty plan (deliberately cleared by
                // the user) is preserved as empty — `?:` only triggers on null.
                val resolved = trainingPlan ?: exerciseRepository.getAdhocPlan(row.exerciseUuid)
                row.exerciseUuid to resolved
            }
        }
        val exerciseSnapshots = performedRows
            .sortedBy { it.position }
            .mapNotNull { row ->
                val template = exerciseTemplates[row.exerciseUuid] ?: return@mapNotNull null
                val performedSets = setRepository.getByPerformedExercise(row.uuid)
                PerformedExerciseSnapshot(
                    performed = row,
                    exerciseName = template.name,
                    exerciseType = template.type,
                    planSets = planByExercise[row.exerciseUuid],
                    performedSets = performedSets.map { set ->
                        PlanSetDataModel(
                            weight = set.weight,
                            reps = set.reps,
                            type = set.type.toPlanType(),
                        )
                    },
                    performedSetUuids = performedSets.map { it.uuid },
                )
            }
        // Q6 lock — pre-session snapshot scope. We collect the PR map exactly once here and
        // then drop the underlying flow; the snapshot lives in State for the session's
        // lifetime, immune to mid-session emissions from other places (Exercise detail edit,
        // a finished session on another screen).
        val uuidsByType = exerciseSnapshots.associate { snap ->
            snap.performed.exerciseUuid to snap.exerciseType
        }
        val preSessionPrs = personalRecordRepository
            .observePersonalRecords(uuidsByType)
            .firstOrNull()
            .orEmpty()
        SessionSnapshot(
            session = session,
            trainingName = training?.name.orEmpty(),
            isAdhoc = training?.isAdhoc == true,
            exercises = exerciseSnapshots,
            preSessionPrSnapshot = preSessionPrs,
        )
    }

    override suspend fun upsertSet(
        performedExerciseUuid: String,
        position: Int,
        set: PlanSetDataModel,
    ) {
        withContext(defaultDispatcher) {
            setRepository.upsert(
                performedExerciseUuid = performedExerciseUuid,
                position = position,
                weight = set.weight,
                reps = set.reps,
                type = set.type.toSetsDataType(),
            )
        }
    }

    override suspend fun deleteSet(performedExerciseUuid: String, position: Int) {
        withContext(defaultDispatcher) {
            setRepository.deleteByPerformedAndPosition(performedExerciseUuid, position)
        }
    }

    override suspend fun setSkipped(performedExerciseUuid: String, skipped: Boolean) {
        withContext(defaultDispatcher) {
            performedExerciseRepository.setSkipped(performedExerciseUuid, skipped)
            if (skipped) {
                setRepository.deleteAllForPerformedExercise(performedExerciseUuid)
            }
        }
    }

    override suspend fun resetExerciseSets(performedExerciseUuid: String) {
        withContext(defaultDispatcher) {
            setRepository.deleteAllForPerformedExercise(performedExerciseUuid)
        }
    }

    override suspend fun finishSession(
        sessionUuid: String,
    ): FinishResult? = withContext(defaultDispatcher) {
        val session = sessionRepository.getById(sessionUuid) ?: return@withContext null
        val training = async { trainingRepository.getTraining(session.trainingUuid) }
        val performedRows = async { performedExerciseRepository.getBySession(sessionUuid) }
        val isAdhoc = training.await()?.isAdhoc == true

        val planUpdates = mutableListOf<PlanUpdate>()
        var setsLogged = 0
        var doneCount = 0
        var skippedCount = 0

        for (row in performedRows.await()) {
            if (row.skipped) {
                skippedCount++
                continue
            }
            val performedSets = setRepository
                .getByPerformedExercise(row.uuid)
                .map { it.toPlanSet() }
            setsLogged += performedSets.size
            if (performedSets.isNotEmpty()) doneCount += 1
            val existingPlan = if (isAdhoc) {
                exerciseRepository.getAdhocPlan(row.exerciseUuid)
            } else {
                trainingExerciseRepository
                    .getPlan(
                        trainingUuid = session.trainingUuid,
                        exerciseUuid = row.exerciseUuid,
                    )
                    ?: exerciseRepository.getAdhocPlan(row.exerciseUuid)
            }
            val nextPlan = PlanUpdateRule.update(existingPlan, performedSets)
            planUpdates += PlanUpdate(
                trainingUuid = session.trainingUuid,
                exerciseUuid = row.exerciseUuid,
                isAdhoc = isAdhoc,
                newPlan = nextPlan,
            )
        }
        val finishedAt = System.currentTimeMillis()
        val applied = sessionRepository.finishSessionAtomic(
            sessionUuid = sessionUuid,
            finishedAt = finishedAt,
            planUpdates = planUpdates,
        )
        if (!applied) return@withContext null
        FinishResult(
            durationMillis = finishedAt - session.startedAt,
            doneCount = doneCount,
            totalCount = performedRows.await().size,
            skippedCount = skippedCount,
            setsLogged = setsLogged,
        )
    }

    override suspend fun cancelSession(sessionUuid: String) {
        withContext(defaultDispatcher) {
            sessionRepository.deleteSession(sessionUuid)
        }
    }

    override suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDataModel>?,
    ) {
        withContext(defaultDispatcher) {
            trainingExerciseRepository.setPlan(trainingUuid, exerciseUuid, plan)
        }
    }

    override suspend fun setAdhocPlan(
        exerciseUuid: String,
        plan: List<PlanSetDataModel>?,
    ) {
        withContext(defaultDispatcher) {
            exerciseRepository.setAdhocPlan(exerciseUuid, plan)
        }
    }

    private fun SetsDataModel.toPlanSet(): PlanSetDataModel =
        PlanSetDataModel(
            weight = weight,
            reps = reps,
            type = type.toPlanType(),
        )
}

internal fun SetsDataType.toPlanType(): SetTypeDataModel = when (this) {
    SetsDataType.WARM -> SetTypeDataModel.WARMUP
    SetsDataType.WORK -> SetTypeDataModel.WORK
    SetsDataType.FAIL -> SetTypeDataModel.FAILURE
    SetsDataType.DROP -> SetTypeDataModel.DROP
}

internal fun SetTypeDataModel.toSetsDataType(): SetsDataType = when (this) {
    SetTypeDataModel.WARMUP -> SetsDataType.WARM
    SetTypeDataModel.WORK -> SetsDataType.WORK
    SetTypeDataModel.FAILURE -> SetsDataType.FAIL
    SetTypeDataModel.DROP -> SetsDataType.DROP
}
