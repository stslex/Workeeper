// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.toData
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.toSetsDataType
import io.github.stslex.workeeper.feature.live_workout.domain.model.AddExerciseResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.AdhocSessionResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExercisePickerEntry
import io.github.stslex.workeeper.feature.live_workout.domain.model.FinishResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.InlineAdhocResult
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions", "LongParameterList")
@Inject
@SingleIn(LiveWorkoutScope::class)
class LiveWorkoutInteractorImpl internal constructor(
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
        // Reuse any in-progress session for this training so re-entry cannot spawn a second.
        val existing = sessionRepository.getAnyActiveSession()
        if (existing != null && existing.trainingUuid == trainingUuid) {
            return@withContext existing.sessionUuid
        }
        val pairs = trainingExerciseRepository.getRowsForTraining(trainingUuid)
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
    ): SessionSnapshotDomain? = withContext(defaultDispatcher) {
        val sessionDeferred = async {
            sessionRepository.getById(sessionUuid)
        }
        val performedRowsDeferred = async {
            performedExerciseRepository.getBySession(sessionUuid)
        }
        val session = sessionDeferred.await() ?: return@withContext null
        val trainingDeferred = async {
            trainingRepository.getTraining(session.trainingUuid)
        }

        val performedRows = performedRowsDeferred.await()

        val planByExerciseDeferred = async {
            val exerciseUuids = performedRows.map { it.exerciseUuid }
            if (trainingDeferred.await()?.isAdhoc == true) {
                // An ad-hoc training has no template; the plan lives on the exercise itself.
                PlanLookup(
                    plansByExercise = exerciseRepository.getAdhocPlans(exerciseUuids)
                        .mapValues { (_, plan) -> plan?.map { it.toDomain() } },
                    planAttachedUuids = exerciseUuids.toSet(),
                )
            } else {
                val trainingPlans = trainingExerciseRepository.getPlans(
                    trainingUuid = session.trainingUuid,
                    exerciseUuids = exerciseUuids,
                )
                // GUARD: read plan attachment with `containsKey`, never `map[k] == null` —
                // a row with `plan_sets IS NULL` is attached-with-no-plan, a different state.
                val planAttachedUuids = exerciseUuids.filterTo(mutableSetOf()) { uuid ->
                    trainingPlans.containsKey(uuid)
                }
                // Read-time fallback for legacy null planSets; an empty list is a real clear.
                val nullExerciseUuids = exerciseUuids.filter { trainingPlans[it] == null }
                val fallbacks = if (nullExerciseUuids.isNotEmpty()) {
                    exerciseRepository.getAdhocPlans(nullExerciseUuids)
                } else {
                    emptyMap()
                }
                PlanLookup(
                    plansByExercise = exerciseUuids.associateWith { uuid ->
                        (trainingPlans[uuid] ?: fallbacks[uuid])?.map { it.toDomain() }
                    },
                    planAttachedUuids = planAttachedUuids,
                )
            }
        }

        val exerciseTemplatesDeferred = async {
            exerciseRepository
                .getExercisesByUuid(performedRows.map { it.exerciseUuid })
                .associateBy { it.uuid }
        }

        val performedSetsByPerformedDeferred = async {
            setRepository.getByPerformedExercises(performedRows.map { it.uuid })
        }

        // GUARD: keep this in the parallel block — it is the one read that scales with the
        // user's whole history, so serialising it puts its latency on the critical path.
        val preSessionPrsDeferred = async {
            personalRecordRepository
                .observePersonalRecordsBatch(performedRows.mapTo(mutableSetOf()) { it.exerciseUuid })
                .firstOrNull()
                .orEmpty()
                .mapValues { (_, pr) -> pr.toDomain() }
        }

        val exerciseTemplates = exerciseTemplatesDeferred.await()
        val planByExercise = planByExerciseDeferred.await()
        val performedSetsByPerformed = performedSetsByPerformedDeferred.await()

        val exerciseSnapshots = performedRows
            .sortedBy { it.position }
            .mapNotNull { row -> // sync now — no I/O inside
                val template = exerciseTemplates[row.exerciseUuid] ?: return@mapNotNull null
                LiveExerciseDomain(
                    performed = row.toDomain(exerciseName = template.name),
                    exerciseType = template.type.toDomain(),
                    planSets = planByExercise.plansByExercise[row.exerciseUuid],
                    performedSets = performedSetsByPerformed[row.uuid].orEmpty()
                        .map { it.toDomain() },
                    isPlanAttached = row.exerciseUuid in planByExercise.planAttachedUuids,
                    description = template.description?.takeIf { it.isNotBlank() },
                )
            }
        // Collected once and the flow dropped: the snapshot is frozen for the whole session.
        val exerciseUuids = exerciseSnapshots
            .mapTo(mutableSetOf()) { snap -> snap.performed.exerciseUuid }
        // GUARD: the deferred asks for every performed row; the template lookup then drops
        // rows, so narrow the map back to exactly the exercises the session shows.
        val preSessionPrs = preSessionPrsDeferred.await().filterKeys { it in exerciseUuids }
        val training = trainingDeferred.await()
        SessionSnapshotDomain(
            session = session.toDomain(),
            trainingName = training?.name.orEmpty(),
            isAdhoc = training?.isAdhoc == true,
            exercises = exerciseSnapshots,
            preSessionPrSnapshot = preSessionPrs,
        )
    }

    override suspend fun upsertSet(
        performedExerciseUuid: String,
        position: Int,
        set: PlanSetDomain,
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
            // Flag only — no set wipe; skip is reversible in place only if the rows survive.
            performedExerciseRepository.setSkipped(performedExerciseUuid, skipped)
        }
    }

    override suspend fun deleteExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    ) {
        withContext(defaultDispatcher) {
            sessionRepository.removeExerciseFromSession(
                performedExerciseUuid = performedExerciseUuid,
                exerciseUuid = exerciseUuid,
                trainingUuid = trainingUuid,
                removeFromPlan = removeFromPlan,
            )
        }
    }

    override suspend fun setPlanAttachment(
        trainingUuid: String,
        exerciseUuid: String,
        attached: Boolean,
        planSets: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            if (attached) {
                trainingExerciseRepository.attachExercise(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    planSets = planSets?.map { it.toData() },
                )
            } else {
                trainingExerciseRepository.detachExercise(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                )
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
        newTrainingName: String?,
    ): FinishResult? = withContext(defaultDispatcher) {
        val session = sessionRepository.getById(sessionUuid) ?: return@withContext null
        val training = async { trainingRepository.getTraining(session.trainingUuid) }
        val performedRows = async { performedExerciseRepository.getBySession(sessionUuid) }
        val isAdhoc = training.await()?.isAdhoc == true

        val planUpdates = mutableListOf<PlanUpdate>()
        var setsLogged = 0
        var doneCount = 0
        var skippedCount = 0
        val discardedSetUuids = mutableListOf<String>()

        // Key presence is the plan-attached flag; empty for an ad-hoc training.
        val planAttachedUuids = if (isAdhoc) {
            emptySet()
        } else {
            trainingExerciseRepository
                .getPlans(
                    trainingUuid = session.trainingUuid,
                    exerciseUuids = performedRows.await().map { it.exerciseUuid },
                )
                .keys
        }

        for (row in performedRows.await()) {
            if (row.skipped) {
                skippedCount++
                continue
            }
            // Unfilled sets are discarded at finish. Defence-in-depth: no production writer
            // can persist `reps <= 0`, but nothing at the data layer enforces it.
            val (filledSets, unfilledSets) = setRepository
                .getByPerformedExercise(row.uuid)
                .map { it.toDomain() }
                .partition { it.reps > 0 }
            // Collected, NOT deleted here: `finishSessionAtomic` deletes them, so a failed
            // finish rolls the deletion back with everything else.
            discardedSetUuids += unfilledSets.map { it.uuid }
            val performedSets = filledSets.map { it.toPlanSet() }
            setsLogged += performedSets.size
            if (performedSets.isNotEmpty()) doneCount += 1
            // A one-off has no plan row, so a template write would match zero rows. Route it
            // to the exercise's own `last_adhoc_sets`, where `loadSession` looks next time.
            val isPlanAttached = isAdhoc || row.exerciseUuid in planAttachedUuids
            val writesToExercise = !isPlanAttached || isAdhoc
            val existingPlan = if (writesToExercise) {
                exerciseRepository.getAdhocPlan(row.exerciseUuid)
            } else {
                trainingExerciseRepository
                    .getPlan(
                        trainingUuid = session.trainingUuid,
                        exerciseUuid = row.exerciseUuid,
                    )
                    ?: exerciseRepository.getAdhocPlan(row.exerciseUuid)
            }
            val nextPlan = performedSets
                .map { it.toData() }
                .ifEmpty { existingPlan }
            planUpdates += PlanUpdate(
                trainingUuid = session.trainingUuid,
                exerciseUuid = row.exerciseUuid,
                isAdhoc = writesToExercise,
                newPlan = nextPlan,
            )
        }
        val finishedAt = System.currentTimeMillis()
        val applied = sessionRepository.finishSessionAtomic(
            sessionUuid = sessionUuid,
            finishedAt = finishedAt,
            planUpdates = planUpdates,
            newTrainingName = newTrainingName,
            discardedSetUuids = discardedSetUuids,
        )
        if (!applied) return@withContext null
        FinishResult(
            durationMillis = finishedAt - session.startedAt,
            doneCount = doneCount,
            totalCount = performedRows.await().size,
            skippedCount = skippedCount,
            setsLogged = setsLogged,
            discardedUnfilledSets = discardedSetUuids.size,
        )
    }

    override suspend fun cancelSession(sessionUuid: String) {
        withContext(defaultDispatcher) {
            val session = sessionRepository.getById(sessionUuid) ?: return@withContext
            val training = trainingRepository.getTraining(session.trainingUuid)
            if (training?.isAdhoc == true) {
                // Cancel must cascade to the training row + inline exercises, or they leak.
                sessionRepository.discardAdhocSession(
                    sessionUuid = sessionUuid,
                    trainingUuid = session.trainingUuid,
                )
            } else {
                sessionRepository.deleteSession(sessionUuid)
            }
        }
    }

    override suspend fun createAdhocSession(
        name: String,
        exerciseUuids: List<String>,
    ): AdhocSessionResult = withContext(defaultDispatcher) {
        val result = sessionRepository.createAdhocSession(name, exerciseUuids)
        AdhocSessionResult(
            sessionUuid = result.sessionUuid,
            trainingUuid = result.trainingUuid,
        )
    }

    override suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean,
    ): AddExerciseResult = withContext(defaultDispatcher) {
        val result = sessionRepository.addExerciseToActiveSession(
            sessionUuid = sessionUuid,
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
            attachToPlan = attachToPlan,
        )
        AddExerciseResult(
            performedExerciseUuid = result.performedExerciseUuid,
            planSets = result.planSets?.map { it.toDomain() },
            isPlanAttached = result.isPlanAttached,
        )
    }

    override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String) {
        withContext(defaultDispatcher) {
            sessionRepository.discardAdhocSession(
                sessionUuid = sessionUuid,
                trainingUuid = trainingUuid,
            )
        }
    }

    override suspend fun createInlineAdhocExercise(
        name: String,
    ): InlineAdhocResult = withContext(defaultDispatcher) {
        val result = exerciseRepository.createInlineAdhocExercise(name)
        InlineAdhocResult(
            exerciseUuid = result.exercise.uuid,
            name = result.exercise.name,
            type = result.exercise.type.toDomain(),
            reusedExisting = result.reusedExisting,
        )
    }

    override suspend fun updateTrainingName(trainingUuid: String, name: String) {
        withContext(defaultDispatcher) {
            trainingRepository.updateName(trainingUuid, name)
        }
    }

    override suspend fun searchExercisesForPicker(
        query: String,
        excludedUuids: Set<String>,
    ): List<ExercisePickerEntry> = withContext(defaultDispatcher) {
        exerciseRepository
            .searchActiveExercises(query = query, excludeUuids = excludedUuids)
            .map { exercise ->
                ExercisePickerEntry(
                    uuid = exercise.uuid,
                    name = exercise.name,
                    type = exercise.type.toDomain(),
                )
            }
    }

    override suspend fun fetchPrSnapshotForExercise(
        exerciseUuid: String,
    ): PersonalRecordDomain? = withContext(defaultDispatcher) {
        // Suspend hit on the same DAO query, so no Flow stays open mid-session.
        personalRecordRepository.getPersonalRecord(exerciseUuid)?.toDomain()
    }

    override suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            trainingExerciseRepository.setPlan(
                trainingUuid,
                exerciseUuid,
                plan?.map { it.toData() },
            )
        }
    }

    override suspend fun setAdhocPlan(
        exerciseUuid: String,
        plan: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            exerciseRepository.setAdhocPlan(exerciseUuid, plan?.map { it.toData() })
        }
    }

    private fun SetDomain.toPlanSet(): PlanSetDomain =
        PlanSetDomain(
            weight = weight,
            reps = reps,
            type = type,
        )

    /** Plans plus the plan-attached set, kept together so the flag cannot drift from them. */
    private data class PlanLookup(
        val plansByExercise: Map<String, List<PlanSetDomain>?>,
        val planAttachedUuids: Set<String>,
    )
}
