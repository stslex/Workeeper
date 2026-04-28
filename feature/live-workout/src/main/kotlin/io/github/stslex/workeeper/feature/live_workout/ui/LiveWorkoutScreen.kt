// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.dialog.DiscardSessionConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
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
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveExerciseCard
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveWorkoutHeader
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
internal fun LiveWorkoutScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("LiveWorkoutScreen"),
    ) {
        TopBar(consume = consume)
        if (state.isLoading) {
            AppLoadingIndicator(
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Body(state = state, consume = consume)
        }
    }
    if (state.deleteDialogVisible) {
        val sessionName = state.trainingNameLabel.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.feature_live_workout_delete_session_unnamed)
        val progressLabel = stringResource(
            R.string.feature_live_workout_delete_session_progress_format,
            state.doneCount,
            state.totalCount,
            state.setsLogged,
        )
        DiscardSessionConfirmDialog(
            sessionName = sessionName,
            progressLabel = progressLabel,
            onConfirmDelete = { consume(Action.Click.OnDeleteSessionConfirm) },
            onDismiss = { consume(Action.Click.OnDeleteSessionDismiss) },
        )
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
            elapsedLabel = state.elapsedDurationLabel,
            progressLabel = state.progressLabel,
            progress = state.progress,
        )
        Spacer(Modifier.height(AppDimension.Space.md))
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
                LiveExerciseCard(
                    exercise = exercise,
                    expanded = exercise.status == ExerciseStatusUiModel.CURRENT ||
                        (
                            exercise.status == ExerciseStatusUiModel.DONE &&
                                exercise.performedExerciseUuid in state.expandedDoneExerciseUuids
                            ),
                    drafts = state.setDrafts,
                    consume = consume,
                )
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
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.feature_live_workout_finish),
                onClick = { consume(Action.Click.OnFinishClick) },
                enabled = !state.isLoading,
            )
        }
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
    expandedDoneExerciseUuids = kotlinx.collections.immutable.persistentSetOf(),
    preSessionPrSnapshot = persistentMapOf(),
    planEditorTarget = null,
    pendingFinishConfirm = null,
    pendingResetExerciseUuid = null,
    pendingSkipExerciseUuid = null,
    pendingCancelConfirm = false,
    deleteDialogVisible = false,
    isLoading = false,
    errorMessage = null,
)
