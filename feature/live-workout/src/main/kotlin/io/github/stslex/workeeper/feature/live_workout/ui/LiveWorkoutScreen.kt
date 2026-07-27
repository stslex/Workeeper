// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.DiscardSessionConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.ExercisePickerBottomSheet
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.ui.components.FinishConfirmDialog
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveExerciseCard
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveWorkoutHeader
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
internal fun LiveWorkoutScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
    activeSessionBannerModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("LiveWorkoutScreen"),
    ) {
        TopBar(consume = consume)
        Body(
            state = state,
            activeSessionBannerModifier = activeSessionBannerModifier,
            consume = consume,
        )
    }

    when (val sheetState = state.bottomSheetState) {
        is BottomSheetState.ExercisePicker -> ExercisePickerBottomSheet(
            query = sheetState.query,
            results = sheetState.results,
            noMatchHeadline = sheetState.noMatchHeadline,
            createCtaLabel = sheetState.createCtaLabel,
            searchHint = stringResource(R.string.feature_live_workout_picker_search_hint),
            isPrimaryActionEnabled = state.canAddExercise,
            onAction = { action -> consume(Action.DialogClick.PickerAction(action)) },
        )

        BottomSheetState.Hidden -> Unit
    }

    when (val dialog = state.dialogState) {
        is DialogState.DeleteDialog -> DiscardSessionConfirmDialog(
            sessionName = dialog.sessionName,
            progressLabel = dialog.progressLabel,
            onConfirmDelete = { consume(Action.DialogClick.OnDeleteSessionConfirm) },
            onDismiss = { consume(Action.DialogClick.OnDeleteSessionDismiss) },
        )

        is DialogState.EmptyFinish -> AppDialog(
            title = stringResource(R.string.feature_live_workout_empty_finish_title),
            body = stringResource(R.string.feature_live_workout_empty_finish_body),
            confirmLabel = dialog.confirmLabel,
            dismissLabel = dialog.dismissLabel,
            destructive = true,
            onConfirm = {
                if (dialog.canDiscard) {
                    consume(Action.DialogClick.OnEmptyFinishDiscard)
                } else {
                    consume(Action.DialogClick.OnCancelSessionConfirm)
                }
            },
            onDismiss = { consume(Action.DialogClick.OnEmptyFinishContinue) },
        )

        is DialogState.ConfirmDialog -> {
            AppDialog(
                title = dialog.title,
                body = dialog.body,
                confirmLabel = dialog.confirmLabel,
                dismissLabel = dialog.dismissLabel,
                destructive = true,
                onConfirm = {
                    val action = when (dialog) {
                        is DialogState.ConfirmDialog.CancelSession -> Action.DialogClick.OnCancelSessionConfirm
                        is DialogState.ConfirmDialog.ResetSets -> Action.DialogClick.OnResetSetsConfirm(
                            dialog.exerciseUuid,
                        )

                        is DialogState.ConfirmDialog.SkipExercise -> Action.DialogClick.OnSkipExerciseConfirm(
                            dialog.exerciseUuid,
                        )
                    }
                    consume(action)
                },
                onDismiss = {
                    val action = when (dialog) {
                        is DialogState.ConfirmDialog.CancelSession -> Action.DialogClick.OnCancelSessionDismiss
                        is DialogState.ConfirmDialog.ResetSets -> Action.DialogClick.OnResetSetsDismiss
                        is DialogState.ConfirmDialog.SkipExercise -> Action.DialogClick.OnSkipExerciseDismiss
                    }
                    consume(action)
                },
            )
        }

        is DialogState.FinishSession -> FinishConfirmDialog(
            stats = dialog,
            onNameChange = { consume(Action.DialogClick.OnFinishNameChange(it)) },
            onConfirm = {
                consume(Action.DialogClick.OnFinishConfirm)
            },
            onDismiss = {
                consume(Action.DialogClick.OnFinishDismiss)
            },
        )

        DialogState.Hidden -> Unit
    }
}

@Composable
private fun TopBar(consume: (Action) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    AppTopAppBar(
        title = "",
        navigationIcon = {
            IconButton(onClick = { consume(Action.Click.OnBackClick) }) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feature_live_workout_back),
                )
            }
        },
        actions = {
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
                    text = { Text(stringResource(R.string.feature_live_workout_session_overflow_cancel)) },
                    onClick = {
                        menuExpanded = false
                        consume(Action.Click.OnCancelSessionClick)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.feature_live_workout_delete_session),
                            color = AppUi.colors.setType.failureForeground,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        consume(Action.Click.OnDeleteSessionMenuClick)
                    },
                )
            }
        },
    )
}

@Composable
private fun Body(
    state: State,
    @SuppressLint("ModifierParameter") activeSessionBannerModifier: Modifier = Modifier,
    consume: (Action) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimension.screenEdge),
    ) {
        Spacer(Modifier.height(AppDimension.Space.sm))
        LiveWorkoutHeader(
            trainingNameLabel = state.trainingNameLabel,
            namePlaceholder = stringResource(R.string.feature_live_workout_training_name_placeholder),
            elapsedLabel = state.elapsedDurationLabel,
            progressLabel = state.progressLabel,
            progress = state.progress,
            isEditingName = state.isTrainingNameEditing,
            nameDraft = state.trainingNameDraft,
            onNameTap = { consume(Action.Click.OnTrainingNameTap) },
            onNameChange = { consume(Action.Click.OnTrainingNameChange(it)) },
            onNameSubmit = { consume(Action.Click.OnTrainingNameSubmit(it)) },
            modifier = activeSessionBannerModifier,
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        if (state.exercises.isEmpty()) {
            EmptyExercisesPlaceholder(
                onAddExerciseClick = { consume(Action.Click.OnAddExerciseClick) },
                isAddEnabled = state.canAddExercise,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                items(
                    items = state.exercises,
                    key = { it.performedExerciseUuid },
                ) { exercise ->
                    // Auto-default CURRENT (not in activeUuids) stays expanded by default; any
                    // user-toggled state (including a manually-active CURRENT collapsed by
                    // tapping its header) honors the explicit set.
                    val expanded = exercise.performedExerciseUuid in state.expandedExerciseUuids
                    LiveExerciseCard(
                        exercise = exercise,
                        expanded = expanded,
                        consume = consume,
                    )
                }
                item(key = "add-another-exercise-cta") {
                    AddAnotherExerciseRow(
                        onClick = { consume(Action.Click.OnAddExerciseClick) },
                        enabled = state.canAddExercise,
                    )
                }
            }
        }
        Spacer(Modifier.height(AppDimension.Space.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimension.Space.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            AppButton.Primary(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("LiveWorkoutFinishButton"),
                text = stringResource(R.string.feature_live_workout_finish),
                onClick = { consume(Action.Click.OnFinishClick) },
                enabled = !state.isLoading,
            )
        }
    }
}

@Composable
private fun EmptyExercisesPlaceholder(
    onAddExerciseClick: () -> Unit,
    isAddEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AppEmptyState(
            headline = stringResource(R.string.feature_live_workout_empty_headline),
            supportingText = stringResource(R.string.feature_live_workout_empty_supporting),
            actionLabel = stringResource(R.string.feature_live_workout_add_exercise_cta)
                .takeIf { isAddEnabled },
            onAction = onAddExerciseClick.takeIf { isAddEnabled },
        )
    }
}

@Composable
private fun AddAnotherExerciseRow(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    AppButton.Tertiary(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("LiveWorkoutAddAnotherExerciseCta"),
        text = stringResource(R.string.feature_live_workout_add_another_exercise_cta),
        onClick = onClick,
        enabled = enabled,
    )
}

@Preview
@Composable
private fun LiveWorkoutScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveWorkoutScreen(
            state = stubState().copy(
                exercises = persistentListOf(),
                trainingName = "",
                trainingNameLabel = "Untitled",
                progressLabel = "",
                doneCount = 0,
                totalCount = 0,
                setsLogged = 0,
                progress = 0f,
                isAdhoc = true,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutScreenEmptyDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutScreen(
            state = stubState().copy(
                exercises = persistentListOf(),
                trainingName = "",
                trainingNameLabel = "Untitled",
                progressLabel = "",
                doneCount = 0,
                totalCount = 0,
                setsLogged = 0,
                progress = 0f,
                isAdhoc = true,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutScreenPopulatedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveWorkoutScreen(
            state = stubState(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutScreenPopulatedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutScreen(
            state = stubState(),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutScreenLoadingPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutScreen(
            state = State.create(sessionUuid = null, trainingUuid = "training-1"),
            consume = {},
        )
    }
}

private fun stubState(): State = State(
    sessionUuid = "session-1",
    trainingUuid = "training-1",
    trainingName = "Push Day",
    trainingNameLabel = "Push Day",
    trainingNameDraft = "Push Day",
    isTrainingNameEditing = false,
    isAdhoc = false,
    startedAt = 0L,
    nowMillis = 23 * 60_000L + 14_000L,
    elapsedDurationLabel = "23:14",
    doneCount = 1,
    totalCount = 2,
    setsLogged = 1,
    progress = 0.5f,
    progressLabel = "1 of 2 done · 1 set logged",
    exercises = persistentListOf(
        LiveExerciseUiModel(
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
        ),
        LiveExerciseUiModel(
            performedExerciseUuid = "pe-2",
            exerciseUuid = "ex-2",
            exerciseName = "Pull ups",
            exerciseType = ExerciseTypeUiModel.WEIGHTLESS,
            position = 1,
            status = ExerciseStatusUiModel.PENDING,
            statusLabel = "Plan: 2x8",
            planSets = persistentListOf(
                PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.WORK),
            ),
            performedSets = persistentListOf(),
        ),
    ),
    setDrafts = persistentMapOf(),
    activeExerciseUuids = kotlinx.collections.immutable.persistentSetOf(),
    expandedExerciseUuids = kotlinx.collections.immutable.persistentSetOf(),
    manualExpandedExerciseUuids = kotlinx.collections.immutable.persistentSetOf(),
    manualCollapsedExerciseUuids = kotlinx.collections.immutable.persistentSetOf(),
    hasManualDisclosureAction = false,
    preSessionPrSnapshot = persistentMapOf(),
    isAddExerciseInFlight = false,
    isFinishInFlight = false,
    isLoading = false,
    dialogState = DialogState.Hidden,
    bottomSheetState = BottomSheetState.Hidden,
)
