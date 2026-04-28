// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor.FinishResult
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
 * Maps a domain [SessionSnapshot] into a fresh [State]. Status derivation walks the
 * exercises in position order and marks the first non-skipped, non-done exercise as
 * CURRENT — everything else after it falls through to PENDING.
 */
internal fun SessionSnapshot.toState(
    nowMillis: Long,
    resourceWrapper: ResourceWrapper,
): State {
    val ui = exercises.toUiList()
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
        expandedDoneExerciseUuids = persistentSetOf(),
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

internal fun List<PerformedExerciseSnapshot>.toUiList(): ImmutableList<LiveExerciseUiModel> {
    var foundCurrent = false
    return sortedBy { it.performed.position }
        .map { snapshot ->
            val plan = snapshot.planSets.orEmpty().map(PlanSetDataModel::toUi).toImmutableList()
            val performed = snapshot.toLiveSets()
            val isDone = isExerciseDone(plan, performed, snapshot.performed.skipped)
            val status = when {
                snapshot.performed.skipped -> ExerciseStatusUiModel.SKIPPED
                isDone -> ExerciseStatusUiModel.DONE
                !foundCurrent -> {
                    foundCurrent = true
                    ExerciseStatusUiModel.CURRENT
                }

                else -> ExerciseStatusUiModel.PENDING
            }
            LiveExerciseUiModel(
                performedExerciseUuid = snapshot.performed.uuid,
                exerciseUuid = snapshot.performed.exerciseUuid,
                exerciseName = snapshot.exerciseName,
                exerciseType = snapshot.exerciseType.toUi(),
                position = snapshot.performed.position,
                status = status,
                statusLabel = "",
                planSets = plan,
                performedSets = performed,
            )
        }
        .toImmutableList()
}

private fun PerformedExerciseSnapshot.toLiveSets(): ImmutableList<LiveSetUiModel> =
    performedSets.mapIndexed { index, set ->
        LiveSetUiModel(
            position = index,
            weight = set.weight,
            reps = set.reps,
            type = set.type.toUi(),
            isDone = true,
        )
    }.toImmutableList()

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
    )
}

internal fun FinishResult.toFinishStats(resourceWrapper: ResourceWrapper): State.FinishStats =
    State.FinishStats(
        durationMillis = durationMillis,
        durationLabel = formatElapsedDuration(durationMillis),
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
    )

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
