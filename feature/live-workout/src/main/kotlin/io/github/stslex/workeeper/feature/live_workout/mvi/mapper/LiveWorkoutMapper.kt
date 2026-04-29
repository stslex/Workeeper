// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.exercise.sets.PrComparator
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.PerformedExerciseSnapshot
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.SessionSnapshot
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Maps a domain [SessionSnapshot] into a fresh [State]. Status derivation marks any
 * exercise the user has explicitly started ([State.activeExerciseUuids]) as CURRENT;
 * when no exercise is explicitly active (fresh session), the first non-skipped,
 * non-done exercise auto-defaults to CURRENT so the screen always opens with a
 * focused card.
 */
internal fun SessionSnapshot.toState(
    nowMillis: Long,
    resourceWrapper: ResourceWrapper,
): State {
    val typeByUuid = exercises.associate {
        it.performed.exerciseUuid to it.exerciseType
    }
    val prSnapshot = preSessionPrSnapshot.toUiSnapshot(typeByUuid)
    val ui = exercises.toUiList(prSnapshot = preSessionPrSnapshot, activeUuids = emptySet())
    return State(
        sessionUuid = session.uuid,
        trainingUuid = session.trainingUuid,
        trainingName = trainingName,
        trainingNameLabel = "",
        isAdhoc = isAdhoc,
        startedAt = session.startedAt,
        nowMillis = nowMillis.coerceAtLeast(session.startedAt),
        elapsedDurationLabel = formatElapsedDuration(nowMillis - session.startedAt),
        doneCount = 0,
        totalCount = 0,
        setsLogged = 0,
        progress = 0f,
        progressLabel = "",
        exercises = ui,
        setDrafts = emptyMap<State.DraftKey, LiveSetUiModel>().toImmutableMap(),
        activeExerciseUuids = persistentSetOf(),
        expandedExerciseUuids = persistentSetOf(),
        preSessionPrSnapshot = prSnapshot,
        planEditorTarget = null,
        pendingFinishConfirm = null,
        pendingResetExerciseUuid = null,
        pendingSkipExerciseUuid = null,
        pendingCancelConfirm = false,
        deleteDialogVisible = false,
        isLoading = false,
        errorMessage = null,
    ).withPresentation(resourceWrapper)
}

internal fun List<PerformedExerciseSnapshot>.toUiList(
    prSnapshot: Map<String, PersonalRecordDataModel?> = emptyMap(),
    activeUuids: Set<String> = emptySet(),
): ImmutableList<LiveExerciseUiModel> {
    val sorted = sortedBy { it.performed.position }
    val computed = sorted.map { snapshot ->
        val plan = snapshot.planSets.orEmpty().map(PlanSetDataModel::toUi).toImmutableList()
        val baseline = prSnapshot[snapshot.performed.exerciseUuid]
        val performed = snapshot.toLiveSets(baseline)
        val isDone = isExerciseDone(plan, performed, snapshot.performed.skipped)
        Computed(snapshot, plan, performed, isDone)
    }
    // Auto-default: when the user hasn't manually started anything, the first
    // non-skipped non-done exercise becomes CURRENT so a fresh session opens with a
    // focused card. Once the active set is non-empty, the user is in control — no
    // implicit promotion.
    val autoCurrentUuid = if (activeUuids.isEmpty()) {
        computed.firstOrNull { !it.snapshot.performed.skipped && !it.isDone }
            ?.snapshot?.performed?.uuid
    } else null

    return computed.map { c ->
        val uuid = c.snapshot.performed.uuid
        val status = when {
            c.snapshot.performed.skipped -> ExerciseStatusUiModel.SKIPPED
            c.isDone -> ExerciseStatusUiModel.DONE
            uuid in activeUuids || uuid == autoCurrentUuid -> ExerciseStatusUiModel.CURRENT
            else -> ExerciseStatusUiModel.PENDING
        }
        LiveExerciseUiModel(
            performedExerciseUuid = uuid,
            exerciseUuid = c.snapshot.performed.exerciseUuid,
            exerciseName = c.snapshot.exerciseName,
            exerciseType = c.snapshot.exerciseType.toUi(),
            position = c.snapshot.performed.position,
            status = status,
            statusLabel = "",
            planSets = c.plan,
            performedSets = c.performed,
        )
    }.toImmutableList()
}

private data class Computed(
    val snapshot: PerformedExerciseSnapshot,
    val plan: ImmutableList<PlanSetUiModel>,
    val performed: ImmutableList<LiveSetUiModel>,
    val isDone: Boolean,
)

private fun PerformedExerciseSnapshot.toLiveSets(
    baseline: PersonalRecordDataModel?,
): ImmutableList<LiveSetUiModel> =
    performedSets.mapIndexed { index, set ->
        LiveSetUiModel(
            position = index,
            weight = set.weight,
            reps = set.reps,
            type = set.type.toUi(),
            isDone = true,
            isPersonalRecord = PrComparator.beats(set, baseline, exerciseType),
        )
    }.toImmutableList()

private fun Map<String, PersonalRecordDataModel?>.toUiSnapshot(
    typeByUuid: Map<String, ExerciseTypeDataModel>,
): kotlinx.collections.immutable.ImmutableMap<String, State.PrSnapshotItem> = entries
    .mapNotNull { (uuid, pr) ->
        pr?.let {
            uuid to State.PrSnapshotItem(
                weight = pr.weight,
                reps = pr.reps,
                type = (typeByUuid[uuid] ?: ExerciseTypeDataModel.WEIGHTED).toUi(),
            )
        }
    }
    .toMap()
    .toImmutableMap()

private fun isExerciseDone(
    plan: ImmutableList<PlanSetUiModel>,
    performed: ImmutableList<LiveSetUiModel>,
    skipped: Boolean,
): Boolean {
    if (skipped) return false
    if (plan.isEmpty()) return performed.any { it.isDone }
    if (performed.size < plan.size) return false
    val performedByPosition = performed.associateBy { it.position }
    return plan.indices.all { idx -> performedByPosition[idx]?.isDone == true }
}

internal fun State.withPresentation(resourceWrapper: ResourceWrapper): State {
    val presentedExercises = exercises.map { exercise ->
        exercise.copy(statusLabel = exercise.toStatusLabel(resourceWrapper))
    }.toImmutableList()
    val doneCount = presentedExercises.count { it.status == ExerciseStatusUiModel.DONE }
    val totalCount = presentedExercises.size
    val setsLogged = presentedExercises.sumOf { exercise -> exercise.performedSets.count { it.isDone } }
    val safeTotal = totalCount.coerceAtLeast(1)
    val progress = (doneCount.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val setCountLabel = resourceWrapper.getQuantityString(
        R.plurals.feature_live_workout_set_count,
        setsLogged,
        setsLogged,
    )
    return copy(
        trainingNameLabel = trainingName.ifBlank {
            resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)
        },
        doneCount = doneCount,
        totalCount = totalCount,
        setsLogged = setsLogged,
        progress = progress,
        progressLabel = resourceWrapper.getString(
            R.string.feature_live_workout_progress_format,
            doneCount,
            totalCount,
            setCountLabel,
        ),
        exercises = presentedExercises,
    )
}

internal fun State.toFinishStats(resourceWrapper: ResourceWrapper): State.FinishStats {
    val skippedCount = exercises.count { it.status == ExerciseStatusUiModel.SKIPPED }
    return State.FinishStats(
        durationMillis = elapsedMillis,
        durationLabel = elapsedDurationLabel,
        exercisesSummaryLabel = formatExerciseSummary(
            resourceWrapper = resourceWrapper,
            doneCount = doneCount,
            totalCount = totalCount,
            skippedCount = skippedCount,
        ),
        setsLoggedLabel = resourceWrapper.getString(
            R.string.feature_live_workout_finish_stat_sets_count,
            setsLogged,
        ),
        newPersonalRecords = computeNewPersonalRecords(resourceWrapper),
    )
}

/**
 * Walks the in-memory exercises and finds, per exercise, the best logged set; then keeps
 * only the ones that strictly beat the pre-session snapshot. No DB hit — the snapshot is
 * already in State, the performed sets came from the user's session.
 */
private fun State.computeNewPersonalRecords(
    resourceWrapper: ResourceWrapper,
): ImmutableList<State.FinishStats.NewPrEntry> = exercises
    .asSequence()
    .filter { it.status != ExerciseStatusUiModel.SKIPPED }
    .mapNotNull { it.toNewPrEntry(preSessionPrSnapshot, resourceWrapper) }
    .toList()
    .toImmutableList()

private fun LiveExerciseUiModel.toNewPrEntry(
    snapshot: Map<String, State.PrSnapshotItem>,
    resourceWrapper: ResourceWrapper,
): State.FinishStats.NewPrEntry? {
    val performedAsPlanSets = performedSets
        .filter { it.isDone }
        .map { it.toPlanSet() }
    if (performedAsPlanSets.isEmpty()) return null
    val typeData = exerciseType.toData()
    val best = PrComparator.bestOf(performedAsPlanSets, typeData) ?: return null
    val baseline = snapshot[exerciseUuid]
    val beatsBaseline = PrComparator.beats(
        candidate = best,
        baselineWeight = baseline?.weight,
        baselineReps = baseline?.reps,
        type = typeData,
        hasBaseline = baseline != null,
    )
    if (!beatsBaseline) return null
    return State.FinishStats.NewPrEntry(
        exerciseUuid = exerciseUuid,
        exerciseName = exerciseName,
        displayLabel = formatPrLabel(
            weight = best.weight,
            reps = best.reps,
            type = exerciseType,
            resourceWrapper = resourceWrapper,
        ),
    )
}

private fun LiveSetUiModel.toPlanSet(): PlanSetDataModel = PlanSetDataModel(
    weight = weight,
    reps = reps,
    type = type.toData(),
)

private fun formatPrLabel(
    weight: Double?,
    reps: Int,
    type: ExerciseTypeUiModel,
    resourceWrapper: ResourceWrapper,
): String = when (type) {
    ExerciseTypeUiModel.WEIGHTED -> {
        val weightLabel = (weight ?: 0.0).formatPrWeight()
        resourceWrapper.getString(
            R.string.feature_live_workout_finish_pr_weighted_format,
            weightLabel,
            reps,
        )
    }

    ExerciseTypeUiModel.WEIGHTLESS -> resourceWrapper.getString(
        R.string.feature_live_workout_finish_pr_weightless_format,
        reps,
    )
}

private fun Double.formatPrWeight(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    toString().trimEnd('0').trimEnd('.')
}

private fun LiveExerciseUiModel.toStatusLabel(resourceWrapper: ResourceWrapper): String = when (status) {
    ExerciseStatusUiModel.DONE -> {
        val count = performedSets.count { it.isDone }
        val setCountLabel = resourceWrapper.getQuantityString(
            R.plurals.feature_live_workout_status_set_count,
            count,
            count,
        )
        resourceWrapper.getString(
            R.string.feature_live_workout_status_completed_format,
            setCountLabel,
        )
    }

    ExerciseStatusUiModel.CURRENT -> {
        if (planSets.isEmpty()) {
            resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)
        } else if (performedSets.none { it.isDone }) {
            resourceWrapper.getString(
                R.string.feature_live_workout_status_plan_format,
                planSets.formatPlanSummary(),
            )
        } else {
            resourceWrapper.getString(
                R.string.feature_live_workout_status_progress_format,
                performedSets.count { it.isDone },
                planSets.size,
            )
        }
    }

    ExerciseStatusUiModel.PENDING -> {
        val summary = if (planSets.isEmpty()) {
            resourceWrapper.getString(R.string.feature_live_workout_status_no_plan)
        } else {
            planSets.formatPlanSummary()
        }
        resourceWrapper.getString(R.string.feature_live_workout_status_plan_format, summary)
    }

    ExerciseStatusUiModel.SKIPPED -> resourceWrapper.getString(R.string.feature_live_workout_status_skipped)
}

private fun formatExerciseSummary(
    resourceWrapper: ResourceWrapper,
    doneCount: Int,
    totalCount: Int,
    skippedCount: Int,
): String = if (skippedCount > 0) {
    resourceWrapper.getString(
        R.string.feature_live_workout_finish_stat_exercises_with_skipped_format,
        doneCount,
        totalCount,
        skippedCount,
    )
} else {
    resourceWrapper.getString(
        R.string.feature_live_workout_finish_stat_exercises_format,
        doneCount,
        totalCount,
    )
}
