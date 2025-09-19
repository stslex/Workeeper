package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseButtonsRow
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDatePickerDialog
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseSetsCreateDialog
import io.github.stslex.workeeper.feature.exercise.ui.components.ExercisedColumn
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType.Companion.getAction
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseFeatureWidget(
    consume: (action: Action) -> Unit,
    state: State,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimension.Padding.big)
        ) {

            ExerciseButtonsRow(
                isDeleteVisible = state.uuid.isNullOrBlank().not(),
                onCancelClick = { consume(Action.Click.Cancel) },
                onConfirmClick = { consume(Action.Click.Save) },
                onDeleteClick = { consume(Action.Click.Delete) }
            )

            Spacer(Modifier.height(AppDimension.Padding.big))

            ExercisedColumn(
                modifier = Modifier
                    .padding(vertical = AppDimension.Padding.medium),
                state = state,
                consume = consume
            )
        }

        when (val dialogState = state.dialogState) {
            DialogState.Closed -> Unit
            DialogState.Calendar -> ExerciseDatePickerDialog(
                timestamp = state.dateProperty.timestamp,
                onDismissRequest = { consume(Action.Click.CloseDialog) },
                dateChange = { consume(Action.Input.Time(it)) }
            )

            is DialogState.Sets -> ExerciseSetsCreateDialog(
                setsUiModel = dialogState.set,
                onDismissRequest = { consume(Action.Click.DialogSets.DismissSetsDialog(it)) },
                onRepsInput = { consume(Action.Input.DialogSets.Reps(it)) },
                onWeightInput = { consume(Action.Input.DialogSets.Weight(it)) },
                onDeleteClick = { consume(Action.Click.DialogSets.DeleteButton(it)) },
                onSaveClick = { consume(Action.Click.DialogSets.SaveButton(it)) },
                onCancelClick = { consume(Action.Click.DialogSets.CancelButton) },
            )
        }

        SnackbarHostWidget(
            onActionClick = {
                when (it) {
                    SnackbarType.DELETE -> consume(Action.Click.ConfirmedDelete)
                    SnackbarType.DISMISS -> consume(Action.Navigation.Back)
                }
            },
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
private fun SnackbarHostWidget(
    snackbarHostState: SnackbarHostState,
    onActionClick: (type: SnackbarType) -> Unit,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState
    ) { data ->
        Snackbar(
            modifier = modifier
                .padding(AppDimension.Padding.small),
            action = {
                val action = remember(snackbarHostState.currentSnackbarData?.visuals) {
                    snackbarHostState.getAction()
                }
                TextButton(
                    onClick = { action?.let { onActionClick(it) } },
                    modifier = Modifier.padding(horizontal = AppDimension.Padding.small),
                ) {
                    val text = when (action) {
                        SnackbarType.DELETE -> "DELETE"
                        SnackbarType.DISMISS -> "EXIT"
                        null -> null
                    }
                    AnimatedVisibility(text != null) {
                        text?.let { Text(it) }
                    }
                }
            },
            actionContentColor = MaterialTheme.colorScheme.errorContainer,
            dismissActionContentColor = MaterialTheme.colorScheme.primaryContainer,
            actionOnNewLine = true,
            dismissAction = {
                TextButton(
                    modifier = Modifier.padding(horizontal = AppDimension.Padding.small),
                    onClick = {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    },
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text("Cancel")
                }
            }
        ) {
            Text(data.visuals.message)
        }
    }
}

@Composable
@Preview(showSystemUi = false, showBackground = false)
private fun ExerciseFeatureWidgetPreview() {
    AppTheme {
        ExerciseFeatureWidget(
            consume = {},
            state = State.INITIAL,
            modifier = Modifier,
            snackbarHostState = SnackbarHostState()
        )
    }
}