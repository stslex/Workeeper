// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.DiscardSessionConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.rail.AppProgressRail
import io.github.stslex.workeeper.core.ui.kit.components.rail.RailDetail
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.toast.AppToast
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.ExercisePickerBottomSheet
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.RailMapper.toRailGroups
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.ui.components.DeleteExerciseSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.ExerciseDescriptionSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.ExerciseMenuSheetContent
import io.github.stslex.workeeper.feature.live_workout.ui.components.FinishConfirmDialog
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveExerciseCard
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveWorkoutHeader
import io.github.stslex.workeeper.feature.live_workout.ui.components.SessionMenuSheetContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay

/**
 * §8's 22px gap above the rail. There is no 22dp rung on the `AppDimension` ladder and §0.1's
 * round-to-nearest would take it to 24dp; kept at 22dp because the rail's own height is also
 * off-ladder and the pair reads as one measured block. Flagged with the rail's other
 * unverified geometry for the device check.
 */
private val RAIL_TOP_MARGIN = 22.dp

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

        BottomSheetState.SessionMenu -> AppBottomSheet(
            onDismiss = { consume(Action.Click.OnSheetDismiss) },
        ) {
            SessionMenuSheetContent(consume = consume)
        }

        is BottomSheetState.ExerciseMenu -> state.exerciseFor(sheetState.performedExerciseUuid)
            ?.let { exercise ->
                AppBottomSheet(
                    onDismiss = { consume(Action.Click.OnSheetDismiss) },
                ) {
                    ExerciseMenuSheetContent(
                        exercise = exercise,
                        // §6.1: the toggle appears only on mid-session additions, and never
                        // in an ad-hoc session (there is no plan to be excluded from). A
                        // restored one-off keeps its row via !isPlanAttached.
                        showOneOffRow = !state.isAdhoc && (
                            exercise.performedExerciseUuid in state.midSessionAddedUuids ||
                                !exercise.isPlanAttached
                            ),
                        consume = consume,
                    )
                }
            }

        is BottomSheetState.DeleteExerciseConfirm ->
            state.exerciseFor(sheetState.performedExerciseUuid)?.let { exercise ->
                AppBottomSheet(
                    onDismiss = { consume(Action.Click.OnSheetDismiss) },
                ) {
                    DeleteExerciseSheetContent(
                        exercise = exercise,
                        isMidSessionAdded = exercise.performedExerciseUuid in state.midSessionAddedUuids,
                        consume = consume,
                    )
                }
            }

        is BottomSheetState.ExerciseDescription ->
            state.exerciseFor(sheetState.performedExerciseUuid)?.let { exercise ->
                AppBottomSheet(
                    onDismiss = { consume(Action.Click.OnSheetDismiss) },
                ) {
                    ExerciseDescriptionSheetContent(exercise = exercise, consume = consume)
                }
            }

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
                    }
                    consume(action)
                },
                onDismiss = {
                    val action = when (dialog) {
                        is DialogState.ConfirmDialog.CancelSession -> Action.DialogClick.OnCancelSessionDismiss
                        is DialogState.ConfirmDialog.ResetSets -> Action.DialogClick.OnResetSetsDismiss
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

/**
 * `.topbar` (extraction §1.2): back chevron leading, empty spacer, vertical three-dot
 * trailing opening `sh-session`. No title — §1.2 is explicit that the session top bar has
 * none. The old overflow's `Удалить сессию` item is not part of the drawn surface; its
 * dialog and actions remain in code, reported with the PR.
 */
@Composable
internal fun TopBar(consume: (Action) -> Unit) {
    AppTopBar(
        navigation = {
            AppIconButton(
                modifier = Modifier.testTag("LiveWorkoutBackButton"),
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(R.string.feature_live_workout_back),
                onClick = { consume(Action.Click.OnBackClick) },
            )
        },
        actions = {
            AppIconButton(
                modifier = Modifier.testTag("LiveWorkoutMenuButton"),
                icon = AppIcons.MoreVertical,
                contentDescription = stringResource(R.string.feature_live_workout_more),
                onClick = { consume(Action.Click.OnSessionMenuClick) },
            )
        },
    )
}

@Composable
private fun Body(
    state: State,
    @SuppressLint("ModifierParameter") activeSessionBannerModifier: Modifier = Modifier,
    consume: (Action) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Content scrolls out UNDER the dock (`.dock{position:sticky}` with its base
            // gradient); the clearance keeps the last row reachable above it.
            contentPadding = PaddingValues(bottom = DOCK_CLEARANCE),
        ) {
            item(key = "shead") {
                Column(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
                    Spacer(Modifier.height(AppDimension.Space.sm))
                    LiveWorkoutHeader(
                        trainingNameLabel = state.trainingNameLabel,
                        namePlaceholder = stringResource(R.string.feature_live_workout_training_name_placeholder),
                        elapsedLabel = state.elapsedDurationLabel,
                        metaLabel = state.headerMetaLabel,
                        isEditingName = state.isTrainingNameEditing,
                        nameDraft = state.trainingNameDraft,
                        onNameTap = { consume(Action.Click.OnTrainingNameTap) },
                        onNameChange = { consume(Action.Click.OnTrainingNameChange(it)) },
                        onNameSubmit = { consume(Action.Click.OnTrainingNameSubmit(it)) },
                        modifier = activeSessionBannerModifier,
                    )
                }
            }
            item(key = "rail") {
                // §14's frame: shead -> rail -> railmeta. The rail supersedes the header's
                // LinearProgressIndicator — two progress bars for one session would
                // contradict each other the moment their denominators diverged. (The old
                // frame double-inset the rail with a second screenEdge padding; the mockup
                // aligns it to the same gutter as everything else.)
                Column(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
                    Spacer(Modifier.height(RAIL_TOP_MARGIN))
                    AppProgressRail(
                        groups = state.toRailGroups(),
                        meta = { detail -> RailMetaRow(detail = detail, state = state) },
                    )
                }
            }
            if (state.exercises.isEmpty()) {
                item(key = "empty") {
                    EmptyExercisesPlaceholder(
                        onAddExerciseClick = { consume(Action.Click.OnAddExerciseClick) },
                        isAddEnabled = state.canAddExercise,
                        modifier = Modifier
                            .fillParentMaxHeight(EMPTY_STATE_HEIGHT_FRACTION)
                            .fillMaxWidth(),
                    )
                }
            } else {
                itemsIndexed(
                    items = state.exercises,
                    key = { _, exercise -> exercise.performedExerciseUuid },
                ) { index, exercise ->
                    // The amended disclosure model: open is exactly membership in this set.
                    val expanded = exercise.performedExerciseUuid in state.expandedExerciseUuids
                    LiveExerciseCard(
                        exercise = exercise,
                        ordinal = index + 1,
                        expanded = expanded,
                        consume = consume,
                        modifier = Modifier.padding(
                            start = AppDimension.screenEdge,
                            end = AppDimension.screenEdge,
                            // `.cards{margin-top:26px; gap:10px}` -> 24dp above the first
                            // card, 8dp between cards.
                            top = if (index == 0) AppDimension.Space.xl else AppDimension.Space.sm,
                        ),
                    )
                }
                item(key = "addex") {
                    AddExerciseButton(
                        onClick = { consume(Action.Click.OnAddExerciseClick) },
                        enabled = state.canAddExercise,
                        modifier = Modifier.padding(
                            start = AppDimension.screenEdge,
                            end = AppDimension.screenEdge,
                            top = AppDimension.Space.md,
                        ),
                    )
                }
            }
        }
        Dock(
            onFinish = { consume(Action.Click.OnFinishClick) },
            enabled = !state.isLoading,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        state.pendingUndo?.let { pending ->
            // `.toast{bottom:118px}` — floated above the dock; auto-dismiss commits the
            // deferred write after TOAST_TIMEOUT_MS, keyed on the undo id so a replacement
            // toast restarts the window (mockup: clearTimeout + fresh 5000ms).
            LaunchedEffect(pending.id) {
                delay(TOAST_TIMEOUT_MS)
                consume(Action.Click.OnUndoTimeout(pending.id))
            }
            AppToast(
                message = pending.message,
                actionLabel = stringResource(R.string.feature_live_workout_toast_undo),
                onAction = { consume(Action.Click.OnUndoClick) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = AppDimension.screenEdge,
                        end = AppDimension.screenEdge,
                        bottom = TOAST_BOTTOM_OFFSET,
                    )
                    .testTag("LiveWorkoutUndoToast"),
            )
        }
    }
}

/**
 * `.addex` (extraction §1.8): a dashed full-width 48dp button below the cards — `meta` text,
 * a `hair-s` (-> `borderDefault`) dashed outline at the card radius, the 17dp plus at its
 * heavier 1.9 stroke. String: `Добавить упражнение`, no "+" prefix — the plus is the icon.
 */
@Composable
private fun AddExerciseButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimension.heightMd)
            .clip(RoundedCornerShape(AppDimension.Radius.medium))
            .dashedBorder(
                color = AppUi.colors.borderDefault,
                cornerRadius = AppDimension.Radius.medium,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("LiveWorkoutAddAnotherExerciseCta"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(ADDEX_GLYPH_SIZE),
            imageVector = AppIcons.Plus,
            contentDescription = null,
            tint = AppUi.colors.textTertiary,
        )
        Text(
            text = stringResource(R.string.feature_live_workout_add_exercise_cta),
            style = AppUi.typography.text.body,
            color = AppUi.colors.textTertiary,
        )
    }
}

/**
 * `.dock` (extraction §1.8): sticky at the bottom, `linear-gradient(to top, base 62%,
 * transparent)` behind the finish button so content visibly scrolls out underneath.
 */
@Composable
private fun Dock(
    onFinish: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = AppUi.colors.surfaceTier0
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    DOCK_GRADIENT_STOP to base,
                    1f to base,
                ),
            )
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                top = AppDimension.Space.lg,
                bottom = AppDimension.Space.xl,
            )
            .navigationBarsPadding(),
    ) {
        AppButton.Primary(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("LiveWorkoutFinishButton"),
            text = stringResource(R.string.feature_live_workout_finish),
            onClick = onFinish,
            enabled = enabled,
        )
    }
}

/** `linear-gradient(to top, base 62%, …)`: solid from the bottom 62%, i.e. from 38% top-down. */
private const val DOCK_GRADIENT_STOP = 0.38f

/** Clearance so the list's tail scrolls clear of the overlaid dock. */
private val DOCK_CLEARANCE = 104.dp

/** `.toast{bottom:118px}` → the ladder-nearest 120dp above the screen edge. */
private val TOAST_BOTTOM_OFFSET = 120.dp

/** The mockup's 5000ms auto-dismiss — spec §6.1's "5-second undo toast". */
private const val TOAST_TIMEOUT_MS = 5_000L

/** The `.addex` plus renders at 17dp (mockup 17×17, stroke 1.9). */
private val ADDEX_GLYPH_SIZE = 17.dp

private const val EMPTY_STATE_HEIGHT_FRACTION = 0.6f

/**
 * `.railmeta` (extraction §1.4): two `.label`s, space-between. The left one names the
 * detail level the rail RESOLVED (the slot hands it over, so they cannot disagree); the
 * right one counts every exercise (skipped included), sets over non-skipped only, plus
 * `разовых: n` when one-offs exist — the mockup's own denominators.
 */
@Composable
private fun RailMetaRow(detail: RailDetail, state: State) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppLabel(
            text = stringResource(
                when (detail) {
                    RailDetail.SETS -> R.string.feature_live_workout_railmeta_detail_sets
                    RailDetail.EXERCISES -> R.string.feature_live_workout_railmeta_detail_exercises
                    RailDetail.OVERALL -> R.string.feature_live_workout_railmeta_detail_overall
                },
            ),
        )
        val setCount = state.exercises
            .filter { it.status != ExerciseStatusUiModel.SKIPPED }
            .sumOf { it.visibleSets.size }
        val oneOffCount = state.exercises.count {
            !it.isPlanAttached && it.status != ExerciseStatusUiModel.SKIPPED
        }
        val counts = stringResource(
            R.string.feature_live_workout_railmeta_counts,
            state.exercises.size,
            setCount,
        )
        AppLabel(
            text = if (oneOffCount > 0) {
                stringResource(
                    R.string.feature_live_workout_railmeta_one_off_suffix,
                    counts,
                    oneOffCount,
                )
            } else {
                counts
            },
        )
    }
}

private fun State.exerciseFor(performedExerciseUuid: String): LiveExerciseUiModel? =
    exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }

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

@Preview
@Composable
private fun LiveWorkoutScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveWorkoutScreen(
            state = stubState().copy(
                exercises = persistentListOf(),
                trainingName = "",
                trainingNameLabel = "Untitled",
                headerMetaLabel = "",
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
                headerMetaLabel = "",
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
    headerMetaLabel = "1 из 2 упражнений · 1 из 6 подходов",
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
    expandedExerciseUuids = kotlinx.collections.immutable.persistentSetOf<String>(),
    preSessionPrSnapshot = persistentMapOf(),
    isAddExerciseInFlight = false,
    isFinishInFlight = false,
    isLoading = false,
    dialogState = DialogState.Hidden,
    bottomSheetState = BottomSheetState.Hidden,
)
