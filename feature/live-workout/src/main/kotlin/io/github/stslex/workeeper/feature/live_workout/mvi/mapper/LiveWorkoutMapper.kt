// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel.Companion.toUi
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
): State {
    val ui = exercises.toUiList()
    return State(
        sessionUuid = session.uuid,
        trainingUuid = session.trainingUuid,
        trainingName = trainingName,
        isAdhoc = isAdhoc,
        startedAt = session.startedAt,
        nowMillis = nowMillis.coerceAtLeast(session.startedAt),
        elapsedDurationLabel = formatElapsedDuration(nowMillis - session.startedAt),
        exercises = ui,
        setDrafts = emptyMap<State.DraftKey, LiveSetUiModel>().toImmutableMap(),
        expandedDoneExerciseUuids = persistentSetOf(),
        planEditorTarget = null,
        pendingFinishConfirm = null,
        pendingResetExerciseUuid = null,
        pendingSkipExerciseUuid = null,
        pendingCancelConfirm = false,
        isLoading = false,
        errorMessage = null,
    )
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
