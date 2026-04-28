// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.ui.components.DeleteConfirmDialog
import io.github.stslex.workeeper.feature.past_session.ui.components.PastExerciseCard
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionHeader
import kotlinx.collections.immutable.persistentListOf
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun PastSessionScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("PastSessionScreen"),
    ) {
        AppTopAppBar(
            title = (state.phase as? State.Phase.Loaded)?.detail?.trainingName
                ?: stringResource(R.string.feature_past_session_loading_title),
            navigationIcon = {
                IconButton(onClick = { consume(Action.Click.OnBackClick) }) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconMd),
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                    )
                }
            },
            actions = {
                if (state.canDelete) {
                    IconButton(onClick = { consume(Action.Click.OnDeleteClick) }) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconMd),
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.feature_past_session_action_delete),
                            tint = AppUi.colors.status.error,
                        )
                    }
                }
            },
        )

        when (val phase = state.phase) {
            State.Phase.Loading -> LoadingContent(modifier = Modifier.fillMaxSize())
            is State.Phase.Error -> ErrorContent(
                modifier = Modifier.fillMaxSize(),
                errorType = phase.errorType,
                onRetry = { consume(Action.Click.OnRetryLoad) },
            )

            is State.Phase.Loaded -> LoadedContent(
                modifier = Modifier.fillMaxSize(),
                detail = phase.detail,
                consume = consume,
            )
        }
    }

    if (state.deleteDialogVisible) {
        DeleteConfirmDialog(
            onConfirm = { consume(Action.Click.OnDeleteConfirm) },
            onDismiss = { consume(Action.Click.OnDeleteDismiss) },
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppLoadingIndicator()
    }
}

@Composable
private fun ErrorContent(
    errorType: ErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headlineRes = when (errorType) {
        ErrorType.SessionNotFound -> R.string.feature_past_session_error_not_found
        ErrorType.LoadFailed -> R.string.feature_past_session_error_load_failed
        ErrorType.SaveFailed -> R.string.feature_past_session_save_failed_snackbar
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppEmptyState(
            headline = stringResource(headlineRes),
            actionLabel = stringResource(R.string.feature_past_session_action_retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun LoadedContent(
    detail: PastSessionUiModel,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = AppDimension.screenEdge,
            vertical = AppDimension.Space.md,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        item {
            PastSessionHeader(detail = detail)
        }
        items(
            items = detail.exercises,
            key = { it.performedExerciseUuid },
        ) { exercise ->
            PastExerciseCard(
                exercise = exercise,
                onWeightChange = { setUuid, raw ->
                    consume(Action.Input.OnSetWeightChange(setUuid = setUuid, raw = raw))
                },
                onRepsChange = { setUuid, raw ->
                    consume(Action.Input.OnSetRepsChange(setUuid = setUuid, raw = raw))
                },
                onTypeChange = { setUuid, type ->
                    consume(Action.Click.OnSetTypeChange(setUuid = setUuid, type = type))
                },
            )
        }
    }
}

@Preview(name = "Loaded — Light")
@Composable
private fun PastSessionScreenLoadedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastSessionScreen(
            state = State(
                sessionUuid = "stub",
                phase = State.Phase.Loaded(detail = stubDetail()),
                deleteDialogVisible = false,
            ),
            consume = {},
        )
    }
}

@Preview(name = "Loaded — Dark")
@Composable
private fun PastSessionScreenLoadedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSessionScreen(
            state = State(
                sessionUuid = "stub",
                phase = State.Phase.Loaded(detail = stubDetail()),
                deleteDialogVisible = false,
            ),
            consume = {},
        )
    }
}

@Preview(name = "Loading")
@Composable
private fun PastSessionScreenLoadingPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSessionScreen(
            state = State(
                sessionUuid = "stub",
                phase = State.Phase.Loading,
                deleteDialogVisible = false,
            ),
            consume = {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun PastSessionScreenErrorPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastSessionScreen(
            state = State(
                sessionUuid = "stub",
                phase = State.Phase.Error(ErrorType.SessionNotFound),
                deleteDialogVisible = false,
            ),
            consume = {},
        )
    }
}

private fun stubDetail(): PastSessionUiModel = PastSessionUiModel(
    trainingName = "Push day",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "Mon, Apr 27, 19:42",
    durationLabel = "47 min",
    totalsLabel = "5 exercises · 18 sets",
    volumeLabel = "1,250 kg total",
    exercises = persistentListOf(
        PastExerciseUiModel(
            performedExerciseUuid = "pe-1",
            exerciseName = "Bench press",
            position = 0,
            skipped = false,
            isWeighted = true,
            sets = persistentListOf(
                PastSetUiModel(
                    setUuid = "s-1",
                    performedExerciseUuid = "pe-1",
                    position = 0,
                    type = SetTypeUiModel.WORK,
                    weightInput = "100",
                    repsInput = "5",
                    weightError = false,
                    repsError = false,
                ),
            ),
        ),
    ),
)

