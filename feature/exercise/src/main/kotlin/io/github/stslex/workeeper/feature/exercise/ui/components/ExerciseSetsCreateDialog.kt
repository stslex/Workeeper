package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.BodyFloatInputField
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.BodyNumberInputField
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
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
    Dialog(
        onDismissRequest = { onDismissRequest(setsUiModel) },
    ) {
        Column(
            modifier = modifier
                .wrapContentHeight()
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(AppDimension.Radius.large),
                )
                .padding(AppDimension.Padding.big),
        ) {
            BodyNumberInputField(
                property = setsUiModel.reps,
                labelRes = R.string.feature_exercise_field_label_reps,
                onValueChange = onRepsInput,
            )
            BodyFloatInputField(
                property = setsUiModel.weight,
                labelRes = R.string.feature_exercise_field_label_weight,
                onValueChange = onWeightInput,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSaveClick(setsUiModel)
                    },
                ) {
                    Text("Save")
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCancelClick(setsUiModel.uuid)
                    },
                ) {
                    Text("Cancel")
                }
            }
            Spacer(modifier = Modifier.padding(AppDimension.Padding.medium))
            AnimatedVisibility(
                setsUiModel.reps.isError.not() &&
                        setsUiModel.weight.isError.not(),
            ) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDeleteClick(setsUiModel.uuid)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text("Delete")
                }
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
