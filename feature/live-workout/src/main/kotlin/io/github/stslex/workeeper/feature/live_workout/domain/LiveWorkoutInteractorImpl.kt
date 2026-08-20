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
        // Reuse any in-progress session for this training so re-entry from the Trainings
        // tab does not orphan an active session by spawning a parallel one.
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
                // An ad-hoc training has no template to attach to, so every exercise in it
                // is plan-attached by convention — there is nothing a one-off could differ
                // from. The plan lives on the exercise itself.
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
                // Key presence is the plan-attached flag (v3 §6.2) — absence of the
                // `training_exercise_table` row is the whole encoding. This must be read with
                // `containsKey`, never with a null check: `map[k] == null` is also true for a
                // row that exists with `plan_sets IS NULL`, which is attached-with-no-plan and
                // a different state. See TrainingExerciseRepository.getPlans.
                val planAttachedUuids = exerciseUuids.filterTo(mutableSetOf()) { uuid ->
                    trainingPlans.containsKey(uuid)
                }
                // Read-time fallback for legacy null planSets. Resolve only for attached rows
                // whose plan is null (empty list = deliberately cleared by the user, preserved
                // as empty) and for one-offs, which have no plan row to read at all.
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

        // GUARD: keep this in the parallel block. It is the one read here that scales with the
        // user's whole HISTORY rather than with the session, and in the tail it added all of its
        // own latency to the critical path. The profile that measured it is in
        // `documentation/feature-specs/v3-redesign-spec.md` §27, "PROFILE BEFORE OPTIMISING".
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
        // Q6 lock — pre-session snapshot scope. We collect the PR map exactly once here and
        // then drop the underlying flow; the snapshot lives in State for the session's
        // lifetime, immune to mid-session emissions from other places (Exercise detail edit,
        // a finished session on another screen).
        val exerciseUuids = exerciseSnapshots
            .mapTo(mutableSetOf()) { snap -> snap.performed.exerciseUuid }
        // GUARD: the deferred above asks for every performed row's exercise; the template lookup
        // then drops rows, so the snapshot set is narrower. This filter brings the map back to
        // exactly the exercises the session shows — without it the snapshot carries PRs for
        // exercises that are not in `exercises`.
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
            // Flag only — no set wipe. §6.1: skip is reversible in place, and reversal is
            // only lossless if the logged rows survive. The finish path already treats a
            // skipped row as `continue` (no plan update; its logged sets persist as
            // history), so preserved sets change nothing there.
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

        // Key presence is the plan-attached flag — see TrainingExerciseRepository.getPlans.
        // Read once for the whole session rather than per row. Empty for an ad-hoc training,
        // where the plan lives on the exercise and the axis does not apply.
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
            // Unfilled sets are discarded at finish (§6.1). Measured on this tree: no
            // production writer can persist `reps <= 0` — `SetRepository.upsert` is reached
            // only through `ClickHandler.processSetMarkDone`, which rejects `reps <= 0`, and
            // `SetRepository.update` only through past-session's `InputHandler`, which
            // requires `parsed > 0`. So this partition is defence-in-depth over legacy or
            // imported rows and normally finds nothing. It is here because the invariant is
            // otherwise enforced only by two UI validators agreeing, with nothing at the data
            // layer to stop a third writer from breaking it silently.
            val (filledSets, unfilledSets) = setRepository
                .getByPerformedExercise(row.uuid)
                .map { it.toDomain() }
                .partition { it.reps > 0 }
            // Collected, NOT deleted here: the deletion happens inside `finishSessionAtomic`
            // so a failed finish rolls it back with everything else. Deleting at this point
            // would destroy the rows even when the finish is reported as failed and the
            // session stays active.
            discardedSetUuids += unfilledSets.map { it.uuid }
            val performedSets = filledSets.map { it.toPlanSet() }
            setsLogged += performedSets.size
            if (performedSets.isNotEmpty()) doneCount += 1
            // A one-off has no plan row, so a template write would silently match zero rows
            // and the sets the user just logged would be persisted nowhere. Route it to the
            // exercise's own `last_adhoc_sets` instead — which is also where the read-time
            // fallback in `loadSession` will look for it next time. `PlanUpdate.isAdhoc`
            // already means "write to the exercise, not the training", so the one-off case
            // needs no new field, only the right value.
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
                // Cancel of an ad-hoc session must cascade to the training row + inline
                // exercises. Without this, Track Now / Quick start cancel paths leak orphan
                // training rows — the v5 → v6 migration sweeps existing leakers.
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
        // C1 lock — single-exercise lazy fetch. PersonalRecordRepository.getPersonalRecord is
        // a suspend hit on the same DAO query observePersonalRecord wraps, so we get the
        // freshest baseline without holding a Flow open mid-session.
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

    /**
     * The two things a plan read yields, kept together so the plan-attached flag cannot drift
     * from the plans it was derived alongside. [planAttachedUuids] is derived from key
     * presence in the repository map, not from plan nullability — see
     * `LiveExerciseDomain.isPlanAttached`.
     */
    private data class PlanLookup(
        val plansByExercise: Map<String, List<PlanSetDomain>?>,
        val planAttachedUuids: Set<String>,
    )
}
