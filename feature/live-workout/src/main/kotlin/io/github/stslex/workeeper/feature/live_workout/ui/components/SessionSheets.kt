// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetSeparator
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetSwitchRow
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore

/*
 * The session's four sheets (extraction §1.9) as bare CONTENT composables — `AppBottomSheet`
 * wraps them at the call site, which is what keeps each of them goldenable.
 */

/** `sh-session`: no title — add exercise · cancel session (rust). */
@Composable
internal fun SessionMenuSheetContent(
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppSheetItem(
            title = stringResource(R.string.feature_live_workout_add_exercise_cta),
            icon = AppIcons.Plus,
            onClick = { consume(LiveWorkoutStore.Action.Click.OnAddExerciseClick) },
            modifier = Modifier.testTag("SessionMenu_Add"),
        )
        AppSheetItem(
            title = stringResource(R.string.feature_live_workout_session_overflow_cancel),
            icon = AppIcons.Close,
            destructive = true,
            onClick = { consume(LiveWorkoutStore.Action.Click.OnCancelSessionClick) },
            modifier = Modifier.testTag("SessionMenu_Cancel"),
        )
    }
}

/** `sh-ex`: one-off switch (plan sessions only) · plan/reset · skip toggle · delete. */
@Composable
internal fun ExerciseMenuSheetContent(
    exercise: LiveExerciseUiModel,
    showOneOffRow: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uuid = exercise.performedExerciseUuid
    Column(modifier = modifier.fillMaxWidth()) {
        SheetTitle(exercise.exerciseName)
        if (showOneOffRow) {
            AppSheetSwitchRow(
                title = stringResource(R.string.feature_live_workout_one_off_row_title),
                supporting = stringResource(R.string.feature_live_workout_one_off_row_sub),
                checked = !exercise.isPlanAttached,
                onCheckedChange = { consume(LiveWorkoutStore.Action.Click.OnToggleOneOff(uuid)) },
                modifier = Modifier.testTag("ExerciseMenu_OneOff"),
            )
            AppSheetSeparator()
        }
        AppSheetItem(
            title = stringResource(R.string.feature_live_workout_action_edit_plan),
            onClick = { consume(LiveWorkoutStore.Action.Click.OnEditPlan(uuid)) },
            modifier = Modifier.testTag("ExerciseMenu_EditPlan"),
        )
        AppSheetItem(
            title = stringResource(R.string.feature_live_workout_action_reset_sets),
            onClick = { consume(LiveWorkoutStore.Action.Click.OnResetSets(uuid)) },
        )
        AppSheetItem(
            title = if (exercise.status == ExerciseStatusUiModel.SKIPPED) {
                stringResource(R.string.feature_live_workout_action_unskip)
            } else {
                stringResource(R.string.feature_live_workout_action_skip)
            },
            icon = AppIcons.Skip,
            onClick = { consume(LiveWorkoutStore.Action.Click.OnSkipExercise(uuid)) },
            modifier = Modifier.testTag("ExerciseMenu_Skip"),
        )
        AppSheetItem(
            title = stringResource(R.string.feature_live_workout_action_delete_exercise),
            icon = AppIcons.Trash,
            destructive = true,
            onClick = { consume(LiveWorkoutStore.Action.Click.OnDeleteExerciseClick(uuid)) },
            modifier = Modifier.testTag("ExerciseMenu_Delete"),
        )
    }
}

/** `sh-del`: context-correct removal copy, Ghost keep above DangerText confirm. */
@Composable
internal fun DeleteExerciseSheetContent(
    exercise: LiveExerciseUiModel,
    copy: DeleteExerciseCopyUiModel,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SheetTitle(stringResource(copy.titleRes))
        Text(
            modifier = Modifier.padding(bottom = AppDimension.Space.lg),
            text = stringResource(copy.bodyRes, exercise.exerciseName),
            style = AppUi.typography.text.body,
            color = AppUi.colors.textTertiary,
        )
        AppButton.Ghost(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("DeleteExercise_Keep"),
            text = stringResource(R.string.feature_live_workout_delete_plan_keep),
            onClick = { consume(LiveWorkoutStore.Action.DialogClick.OnDeleteExerciseKeep) },
        )
        AppButton.DangerText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Space.sm)
                .testTag("DeleteExercise_Confirm"),
            text = stringResource(copy.confirmRes),
            onClick = {
                consume(
                    LiveWorkoutStore.Action.DialogClick.OnDeleteExerciseConfirm(
                        exercise.performedExerciseUuid,
                    ),
                )
            },
        )
    }
}

/** `sh-desc`: the exercise name, the free-text description, a Ghost close. */
@Composable
internal fun ExerciseDescriptionSheetContent(
    exercise: LiveExerciseUiModel,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SheetTitle(exercise.exerciseName)
        Text(
            modifier = Modifier.padding(bottom = AppDimension.Space.lg),
            text = exercise.description.orEmpty(),
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        AppButton.Ghost(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.feature_live_workout_sheet_close),
            onClick = { consume(LiveWorkoutStore.Action.Click.OnSheetDismiss) },
        )
    }
}

/** `.sheet h3` — 19/600 `max`, 12dp below (extraction §1.9). */
@Composable
private fun SheetTitle(text: String) {
    Text(
        modifier = Modifier.padding(bottom = AppDimension.Space.md),
        text = text,
        style = AppUi.typography.text.section,
        color = AppUi.colors.textPrimary,
    )
}
