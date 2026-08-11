// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.pr.PrExplainerDialog
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionHeader
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
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
import io.github.stslex.workeeper.feature.past_session.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.past_session.mvi.store.DialogState
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.ui.components.DeleteConfirmDialog
import io.github.stslex.workeeper.feature.past_session.ui.components.PastExerciseCard
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionHeader
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionMenuSheetContent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
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
        TopBar(state = state, consume = consume)
        when (val phase = state.phase) {
            State.Phase.Loading -> LoadingContent(
                modifier = Modifier.fillMaxSize(),
            )

            is State.Phase.Error -> ErrorContent(
                modifier = Modifier.fillMaxSize(),
                errorType = phase.errorType,
                onRetry = { consume(Action.Click.OnRetryLoad) },
            )

            is State.Phase.Loaded -> LoadedContent(
                modifier = Modifier.fillMaxSize(),
                detail = phase.detail,
                expandedUuids = state.expandedExerciseUuids,
                consume = consume,
            )
        }
    }

    when (state.bottomSheetState) {
        BottomSheetState.Hidden -> Unit
        BottomSheetState.SessionMenu -> AppBottomSheet(
            onDismiss = { consume(Action.Click.OnSheetDismiss) },
        ) {
            PastSessionMenuSheetContent(consume = consume)
        }
    }

    when (state.dialogState) {
        DialogState.Hidden -> Unit
        DialogState.DeleteConfirm -> DeleteConfirmDialog(
            onConfirm = { consume(Action.Click.OnDeleteConfirm) },
            onDismiss = { consume(Action.Click.OnDeleteDismiss) },
        )

        DialogState.PrExplainer -> PrExplainerDialog(
            onDismiss = { consume(Action.Click.OnPrExplainerDismiss) },
        )
    }
}

/**
 * `.topbar` (extraction §2.2): back chevron leading, the `h1.sm` title, vertical three-dot
 * trailing — the same `.icon-btn` treatment as the session screen, plus the small title the
 * session deliberately lacks. The trailing glyph is the mockup's ⋮ overflow, **not** the
 * v2.4 error-tinted delete icon, which is retired: the ⋮ opens the session menu sheet, and
 * deletion lives there as a destructive item ahead of its confirmation.
 *
 * `internal` rather than private so the golden can render it in isolation — the same move
 * `feature/live-workout`'s `TopBar` makes for `SessionHeaderGoldenTest`.
 */
@Composable
internal fun TopBar(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = (state.phase as? State.Phase.Loaded)?.detail?.trainingName
        ?: stringResource(R.string.feature_past_session_loading_title)
    AppTopBar(
        modifier = modifier.testTag("PastSessionTopBar"),
        title = title,
        smallTitle = true,
        navigation = {
            AppIconButton(
                modifier = Modifier.testTag("PastSessionBackButton"),
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(KitR.string.core_ui_kit_action_back),
                onClick = { consume(Action.Click.OnBackClick) },
            )
        },
        actions = {
            if (state.canDelete) {
                AppIconButton(
                    icon = AppIcons.MoreVertical,
                    contentDescription = stringResource(
                        R.string.feature_past_session_action_more,
                    ),
                    onClick = { consume(Action.Click.OnSessionMenuClick) },
                )
            }
        },
    )
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
    // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
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
    expandedUuids: ImmutableSet<String>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Horizontal padding is per-item rather than contentPadding: `AppSectionHeader` carries
    // its own screen-edge padding, so a list-level gutter would double it.
    //
    // Vertical rhythm, from the mockup's flex column (margins do NOT collapse in flex, so
    // §2.4's 12px bottom and §2.5's 26px `.cards` top are additive): section head sits
    // 32dp under the header block and 12dp above the cards region, whose own top margin is
    // 24dp — first card top = 12+24; between cards the gap is 8dp (10px → 8dp).
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = AppDimension.Space.xl),
    ) {
        item(key = "header") {
            PastSessionHeader(
                detail = detail,
                modifier = Modifier.padding(horizontal = AppDimension.screenEdge),
            )
        }
        item(key = "section-head") {
            // `Записано` / `можно править` (§2.4) — two peer labels; the right one declares
            // the mode. Same class in the mockup, same one style here.
            AppSectionHeader(
                modifier = Modifier.padding(
                    top = AppDimension.Space.xxl,
                    bottom = AppDimension.Space.md,
                ),
                label = stringResource(R.string.feature_past_session_section_logged),
                trailingLabel = stringResource(R.string.feature_past_session_section_editable),
            )
        }
        itemsIndexed(
            items = detail.exercises,
            key = { _, exercise -> exercise.performedExerciseUuid },
        ) { index, exercise ->
            PastExerciseCard(
                modifier = Modifier.padding(
                    start = AppDimension.screenEdge,
                    end = AppDimension.screenEdge,
                    top = if (index == 0) AppDimension.Space.xl else AppDimension.Space.sm,
                ),
                exercise = exercise,
                // The amended §7 disclosure model: open is exactly membership in this set.
                expanded = exercise.performedExerciseUuid in expandedUuids,
                onHeaderClick = {
                    consume(
                        Action.Click.OnExerciseHeaderClick(
                            performedExerciseUuid = exercise.performedExerciseUuid,
                        ),
                    )
                },
                onWeightChange = { setUuid, raw ->
                    consume(Action.Input.OnSetWeightChange(setUuid = setUuid, raw = raw))
                },
                onRepsChange = { setUuid, raw ->
                    consume(Action.Input.OnSetRepsChange(setUuid = setUuid, raw = raw))
                },
                onPrTagClick = { consume(Action.Click.OnPrTagClick) },
                onSetReorder = { performedExerciseUuid, from, to ->
                    consume(
                        Action.Click.OnSetReorder(
                            performedExerciseUuid = performedExerciseUuid,
                            from = from,
                            to = to,
                        ),
                    )
                },
                onDragStarted = {
                    consume(Action.Click.OnDragStarted)
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
                expandedExerciseUuids = persistentSetOf("pe-1"),
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
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
                expandedExerciseUuids = persistentSetOf("pe-1"),
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
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
                expandedExerciseUuids = persistentSetOf(),
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
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
                expandedExerciseUuids = persistentSetOf(),
                dialogState = DialogState.Hidden,
                bottomSheetState = BottomSheetState.Hidden,
            ),
            consume = {},
        )
    }
}

private fun stubDetail(): PastSessionUiModel = PastSessionUiModel(
    trainingName = "низ — 2",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "23 July 2026",
    durationLabel = "56:08",
    totalsLabel = "5 exercises · 14 sets · 4,820 kg",
    exercises = persistentListOf(
        PastExerciseUiModel(
            performedExerciseUuid = "pe-1",
            exerciseName = "разведение ног",
            position = 0,
            skipped = false,
            isWeighted = true,
            setSummary = "49×15",
            sets = persistentListOf(
                PastSetUiModel(
                    setUuid = "s-1",
                    performedExerciseUuid = "pe-1",
                    position = 0,
                    type = SetTypeUiModel.WORK,
                    weightInput = "49",
                    repsInput = "15",
                    weightError = false,
                    repsError = false,
                    isPersonalRecord = false,
                ),
            ),
        ),
    ),
)
