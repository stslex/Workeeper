package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel

@Composable
internal fun ExerciseSetsCreateDialog(
    setsUiModel: SetsUiModel,
    onDismissRequest: (SetsUiModel) -> Unit,
    onWeightInput: (String) -> Unit,
    onRepsInput: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onSaveClick: (SetsUiModel) -> Unit,
    onCancelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = { onDismissRequest(setsUiModel) }) {
        Column(
            modifier = modifier
                .testTag("ExerciseSetsDialog")
                .clip(AppUi.shapes.medium)
                .background(surface)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            AppNumberInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ExerciseSetsDialogRepsField"),
                value = setsUiModel.reps.uiValue,
                onValueChange = onRepsInput,
                decimals = 0,
                suffix = stringResource(R.string.feature_exercise_field_label_reps),
                isError = setsUiModel.reps.isError,
            )
            AppNumberInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ExerciseSetsDialogWeightField"),
                value = setsUiModel.weight.uiValue,
                onValueChange = onWeightInput,
                decimals = 2,
                suffix = stringResource(R.string.feature_exercise_field_label_weight),
                isError = setsUiModel.weight.isError,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppButton.Primary(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ExerciseSetsDialogSaveButton"),
                        text = "Save",
                        onClick = { onSaveClick(setsUiModel) },
                        size = AppButtonSize.MEDIUM,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppButton.Secondary(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ExerciseSetsDialogCancelButton"),
                        text = "Cancel",
                        onClick = { onCancelClick(setsUiModel.uuid) },
                        size = AppButtonSize.MEDIUM,
                    )
                }
            }
            AnimatedVisibility(
                setsUiModel.reps.isError.not() && setsUiModel.weight.isError.not(),
            ) {
                AppButton.Destructive(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Delete",
                    onClick = { onDeleteClick(setsUiModel.uuid) },
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Composable
@Preview
private fun ExerciseSetsCreateDialogPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ExerciseSetsCreateDialog(
                setsUiModel = SetsUiModel.EMPTY,
                onRepsInput = {},
                onWeightInput = {},
                onDeleteClick = {},
                onDismissRequest = {},
                onSaveClick = {},
                onCancelClick = {},
            )
        }
    }
}
