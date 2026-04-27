// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

private const val DONE_ALPHA = 0.55f
private const val SKIPPED_ALPHA = 0.4f

@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExerciseUiModel,
    drafts: ImmutableMap<LiveWorkoutStore.State.DraftKey, LiveSetUiModel>,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when (exercise.status) {
        ExerciseStatusUiModel.CURRENT -> AppUi.colors.accent
        else -> AppUi.colors.borderSubtle
    }
    val cardAlpha = when (exercise.status) {
        ExerciseStatusUiModel.DONE -> DONE_ALPHA
        ExerciseStatusUiModel.SKIPPED -> SKIPPED_ALPHA
        else -> 1f
    }
    val expanded = exercise.status == ExerciseStatusUiModel.CURRENT
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .border(
                width = if (exercise.status == ExerciseStatusUiModel.CURRENT) {
                    AppDimension.Border.small
                } else {
                    AppDimension.borderHairline
                },
                color = borderColor,
                shape = AppUi.shapes.medium,
            )
            .alpha(cardAlpha)
            .padding(AppDimension.Space.md),
    ) {
        ExerciseCardHeader(
            exercise = exercise,
            consume = consume,
        )
        if (expanded) {
            ExerciseCardBody(
                exercise = exercise,
                drafts = drafts,
                consume = consume,
            )
        }
    }
}

@Composable
private fun ExerciseCardHeader(
    exercise: LiveExerciseUiModel,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { consume(LiveWorkoutStore.Action.Click.OnExerciseHeaderClick(exercise.performedExerciseUuid)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            text = (exercise.position + 1).toString(),
            style = AppUi.typography.labelMedium,
            color = AppUi.colors.textTertiary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.exerciseName,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = exercise.statusLine(),
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textSecondary,
            )
        }
        if (exercise.status != ExerciseStatusUiModel.SKIPPED) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.feature_live_workout_more),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_edit_plan)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnEditPlan(exercise.performedExerciseUuid))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_reset_sets)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnResetSets(exercise.performedExerciseUuid))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_skip)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnSkipExercise(exercise.performedExerciseUuid))
                    },
                )
            }
        }
    }
}

@Composable
private fun ExerciseCardBody(
    exercise: LiveExerciseUiModel,
    drafts: ImmutableMap<LiveWorkoutStore.State.DraftKey, LiveSetUiModel>,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    val isWeighted = exercise.exerciseType == ExerciseTypeUiModel.WEIGHTED
    val rows = buildSetRowList(exercise, drafts)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimension.Space.sm),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        rows.forEach { row ->
            LiveSetRow(
                set = row,
                isWeighted = isWeighted,
                onWeightChange = { value ->
                    consume(
                        LiveWorkoutStore.Action.Input.OnSetWeightChange(
                            exercise.performedExerciseUuid,
                            row.position,
                            value,
                        ),
                    )
                },
                onRepsChange = { value ->
                    consume(
                        LiveWorkoutStore.Action.Input.OnSetRepsChange(
                            exercise.performedExerciseUuid,
                            row.position,
                            value,
                        ),
                    )
                },
                onTypeChange = { type ->
                    consume(
                        LiveWorkoutStore.Action.Click.OnSetTypeSelect(
                            exercise.performedExerciseUuid,
                            row.position,
                            type,
                        ),
                    )
                },
                onMarkDone = {
                    consume(LiveWorkoutStore.Action.Click.OnSetMarkDone(exercise.performedExerciseUuid, row.position))
                },
                onUncheck = {
                    consume(LiveWorkoutStore.Action.Click.OnSetUncheck(exercise.performedExerciseUuid, row.position))
                },
            )
        }
        AppButton.Tertiary(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.feature_live_workout_add_set),
            onClick = { consume(LiveWorkoutStore.Action.Click.OnAddSet(exercise.performedExerciseUuid)) },
            size = AppButtonSize.SMALL,
        )
    }
}

private fun buildSetRowList(
    exercise: LiveExerciseUiModel,
    drafts: ImmutableMap<LiveWorkoutStore.State.DraftKey, LiveSetUiModel>,
): List<LiveSetUiModel> {
    val total = maxOf(
        exercise.planSets.size,
        exercise.performedSets.size,
        drafts.keys
            .filter { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxOfOrNull { it.position + 1 } ?: 0,
    )
    if (total == 0) return emptyList()
    val performedByPos = exercise.performedSets.associateBy { it.position }
    val planByPos = exercise.planSets.withIndex().associate { (idx, plan) -> idx to plan }
    return (0 until total).map { position ->
        val draft = drafts[LiveWorkoutStore.State.DraftKey(exercise.performedExerciseUuid, position)]
        val performed = performedByPos[position]
        val plan = planByPos[position]
        when {
            performed != null -> performed
            draft != null -> draft
            plan != null -> LiveSetUiModel(
                position = position,
                weight = plan.weight,
                reps = plan.reps,
                type = plan.type,
                isDone = false,
            )

            else -> LiveSetUiModel(
                position = position,
                weight = null,
                reps = 0,
                type = SetTypeUiModel.WORK,
                isDone = false,
            )
        }
    }
}

@Composable
private fun LiveExerciseUiModel.statusLine(): String = when (status) {
    ExerciseStatusUiModel.DONE -> {
        val count = performedSets.size
        stringResource(
            R.string.feature_live_workout_status_completed_format,
            androidx.compose.ui.res.pluralStringResource(
                id = R.plurals.feature_live_workout_status_set_count,
                count = count,
                count,
            ),
        )
    }

    ExerciseStatusUiModel.CURRENT -> {
        if (planSets.isEmpty()) {
            stringResource(R.string.feature_live_workout_status_no_plan)
        } else {
            stringResource(
                R.string.feature_live_workout_status_progress_format,
                performedSets.count { it.isDone },
                planSets.size,
            )
        }
    }

    ExerciseStatusUiModel.PENDING -> {
        val summary = if (planSets.isEmpty()) {
            stringResource(R.string.feature_live_workout_status_no_plan)
        } else {
            planSets.formatPlanSummary()
        }
        stringResource(R.string.feature_live_workout_status_plan_format, summary)
    }

    ExerciseStatusUiModel.SKIPPED -> stringResource(R.string.feature_live_workout_status_skipped)
}

@Preview
@Composable
private fun LiveExerciseCardCurrentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveExerciseCard(
            exercise = previewCurrent(),
            drafts = persistentMapOf(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardCurrentDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewCurrent(),
            drafts = persistentMapOf(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardDonePreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewDone(),
            drafts = persistentMapOf(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardPendingPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewPending(),
            drafts = persistentMapOf(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardSkippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewSkipped(),
            drafts = persistentMapOf(),
            consume = {},
        )
    }
}

private fun previewCurrent() = LiveExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseUuid = "ex-1",
    exerciseName = "Bench press",
    exerciseType = ExerciseTypeUiModel.WEIGHTED,
    position = 0,
    status = ExerciseStatusUiModel.CURRENT,
    planSets = persistentListOf(
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
    ),
    performedSets = persistentListOf(
        LiveSetUiModel(position = 0, weight = 100.0, reps = 5, type = SetTypeUiModel.WORK, isDone = true),
    ),
)

private fun previewDone() = previewCurrent().copy(
    status = ExerciseStatusUiModel.DONE,
    performedSets = persistentListOf(
        LiveSetUiModel(position = 0, weight = 100.0, reps = 5, type = SetTypeUiModel.WORK, isDone = true),
        LiveSetUiModel(position = 1, weight = 100.0, reps = 5, type = SetTypeUiModel.WORK, isDone = true),
        LiveSetUiModel(position = 2, weight = 102.5, reps = 5, type = SetTypeUiModel.WORK, isDone = true),
    ).toImmutableList(),
)

private fun previewPending() = previewCurrent().copy(
    status = ExerciseStatusUiModel.PENDING,
    performedSets = persistentListOf(),
)

private fun previewSkipped() = previewCurrent().copy(
    status = ExerciseStatusUiModel.SKIPPED,
    performedSets = persistentListOf(),
)
