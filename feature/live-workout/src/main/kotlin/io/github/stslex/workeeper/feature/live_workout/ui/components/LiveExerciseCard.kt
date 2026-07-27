// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.surface.AppActiveSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val DONE_ALPHA = 0.55f
private const val SKIPPED_ALPHA = 0.4f

@Composable
internal fun LiveExerciseCard(
    exercise: LiveExerciseUiModel,
    expanded: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardAlpha by animateFloatAsState(
        targetValue = when (exercise.status) {
            ExerciseStatusUiModel.DONE -> DONE_ALPHA
            ExerciseStatusUiModel.SKIPPED -> SKIPPED_ALPHA
            else -> 1f
        },
        animationSpec = tween(durationMillis = AppUi.motion.base),
    )
    // `.card.active{background:var(--slab);box-shadow:var(--slabtop)}` — the mockup marks the
    // active card by lifting it, and draws NO border in any state. The animated `accent` border
    // this card used to carry was a substitution for a mechanism that did not exist yet; it does
    // now, so the substitution goes.
    //
    // `active` is passed rather than branched on, so this is one call site for every card state
    // and the surface can animate across the flip. `ActiveSurfaceSingleReaderRule` permits this
    // file exactly one call — it named this file before the call existed.
    AppActiveSurface(
        active = exercise.status == ExerciseStatusUiModel.CURRENT,
        shape = AppUi.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppUi.shapes.medium)
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = AppUi.motion.base,
                    ),
                ),
        ) {
            ExerciseCardHeader(
                exercise = exercise,
                consume = consume,
            )
            if (expanded) {
                ExerciseCardBody(
                    exercise = exercise,
                    isReadOnly = exercise.status == ExerciseStatusUiModel.DONE,
                    consume = consume,
                )
            }
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
            .clickable {
                consume(LiveWorkoutStore.Action.Click.OnExerciseHeaderClick(exercise.performedExerciseUuid))
            }
            .padding(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            modifier = Modifier
                .height(AppDimension.iconSm),
            text = (exercise.position + 1).toString(),
            style = AppUi.typography.labelMedium,
            color = AppUi.colors.textTertiary,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = exercise.exerciseName,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = exercise.statusLabel,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textSecondary,
            )
        }
        // The IconButton + DropdownMenu pair must share a single anchor Box so
        // DropdownMenu's offset stays flush-right under the icon. Without the wrapper
        // the menu opens against the parent Row's start edge — which is what the
        // pre-v2.4 build did and the spec 5.4 three-dots fix closes.
        Box {
            IconButton(
                onClick = {
                    if (exercise.status == ExerciseStatusUiModel.CURRENT) {
                        menuExpanded = true
                    } else {
                        consume(LiveWorkoutStore.Action.Click.OnExerciseHeaderClick(exercise.performedExerciseUuid))
                    }
                },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.feature_live_workout_more),
                    tint = if (exercise.status == ExerciseStatusUiModel.CURRENT) {
                        AppUi.colors.textPrimary
                    } else {
                        AppUi.colors.surfaceTier1
                    },
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
    isReadOnly: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    val isWeighted = exercise.exerciseType == ExerciseTypeUiModel.WEIGHTED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimension.Space.sm)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = AppUi.motion.base,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        exercise.visibleSets.forEach { row ->
            key(exercise.performedExerciseUuid, row.position) {
                LiveSetRow(
                    set = row,
                    isWeighted = isWeighted,
                    testTagPrefix = "LiveSetRow_${exercise.performedExerciseUuid}_${row.position}",
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
                        if (!isReadOnly) {
                            consume(
                                LiveWorkoutStore.Action.Click.OnSetTypeSelect(
                                    exercise.performedExerciseUuid,
                                    row.position,
                                    type,
                                ),
                            )
                        }
                    },
                    onMarkDone = {
                        if (!isReadOnly) {
                            consume(
                                LiveWorkoutStore.Action.Click.OnSetMarkDone(
                                    exercise.performedExerciseUuid,
                                    row.position,
                                ),
                            )
                        }
                    },
                    onUncheck = {
                        consume(
                            LiveWorkoutStore.Action.Click.OnSetUncheck(
                                exercise.performedExerciseUuid,
                                row.position,
                            ),
                        )
                    },
                    editable = !isReadOnly,
                )
            }
        }

        if (!isReadOnly) {
            AppButton.Tertiary(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("LiveExerciseCard_AddSet_${exercise.performedExerciseUuid}"),
                text = stringResource(R.string.feature_live_workout_add_set),
                onClick = { consume(LiveWorkoutStore.Action.Click.OnAddSet(exercise.performedExerciseUuid)) },
                size = AppButtonSize.SMALL,
            )
        }
//        AnimatedVisibility(
//            visible = !isReadOnly,
//            enter = expandVertically(
//                animationSpec = tween(
//                    durationMillis = AppUi.motion.base,
//                ),
//            ),
//            exit = shrinkVertically(
//                animationSpec = tween(
//                    durationMillis = AppUi.motion.base,
//                ),
//            ),
//        ) {
//            AppButton.Tertiary(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .testTag("LiveExerciseCard_AddSet_${exercise.performedExerciseUuid}"),
//                text = stringResource(R.string.feature_live_workout_add_set),
//                onClick = { consume(LiveWorkoutStore.Action.Click.OnAddSet(exercise.performedExerciseUuid)) },
//                size = AppButtonSize.SMALL,
//            )
//        }
    }
}

@Preview
@Composable
private fun LiveExerciseCardCurrentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveExerciseCard(
            exercise = previewCurrent(),
            expanded = true,
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
            expanded = true,
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
            expanded = true,
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
            expanded = false,
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
            expanded = false,
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
    statusLabel = "1 of 3 sets",
    planSets = persistentListOf(
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
    ),
    performedSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
    ),
    visibleSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
        LiveSetUiModel(
            position = 1,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = false,
        ),
        LiveSetUiModel(
            position = 2,
            weight = 102.5,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = false,
        ),
    ),
)

private fun previewDone() = previewCurrent().copy(
    status = ExerciseStatusUiModel.DONE,
    statusLabel = "Completed · 3 sets",
    performedSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
        LiveSetUiModel(
            position = 1,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
        LiveSetUiModel(
            position = 2,
            weight = 102.5,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
    ).toImmutableList(),
    visibleSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
        LiveSetUiModel(
            position = 1,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
        LiveSetUiModel(
            position = 2,
            weight = 102.5,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
    ),
)

private fun previewPending() = previewCurrent().copy(
    status = ExerciseStatusUiModel.PENDING,
    statusLabel = "Plan: 3x5",
    performedSets = persistentListOf(),
    visibleSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = false,
        ),
        LiveSetUiModel(
            position = 1,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = false,
        ),
        LiveSetUiModel(
            position = 2,
            weight = 102.5,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = false,
        ),
    ),
)

private fun previewSkipped() = previewCurrent().copy(
    status = ExerciseStatusUiModel.SKIPPED,
    statusLabel = "Skipped",
    performedSets = persistentListOf(),
    visibleSets = persistentListOf(),
)
