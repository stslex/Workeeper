package io.github.stslex.workeeper.feature.single_training.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDatePickerDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.ui.component.ExerciseCreateWidget
import io.github.stslex.workeeper.feature.single_training.ui.component.ToolbarRow
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SingleTrainingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .testTag("SingleTrainingScreen"),
    ) {
        Column {
            ToolbarRow(
                isDeleteVisible = state.training.uuid.isNotBlank(),
                onConfirmClick = { consume(Action.Click.Save) },
                onCancelClick = { consume(Action.Click.Close) },
                onDeleteClick = { consume(Action.Click.DeleteDialogOpen) },
                modifier = Modifier.testTag("SingleTrainingToolbar"),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimension.Padding.large)
                    .testTag("SingleTrainingContent"),
            ) {
                item {
                    AppTextField(
                        value = state.training.name.uiValue,
                        onValueChange = { consume(Action.Input.Name(it)) },
                        label = stringResource(R.string.feature_single_training_field_name_label),
                        isError = state.training.name.isError,
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = AppDimension.Padding.big),
                    )
                }

                item {
                    Text(
                        text = "Exercises: ",
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (state.training.exercises.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.feature_single_training_field_exercises_empty_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                } else {
                    items(
                        count = state.training.exercises.size,
                        key = {
                            state.training.exercises[it].uuid
                        },
                    ) { index ->
                        val item = state.training.exercises[index]
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { consume(Action.Click.ExerciseClick(item.uuid)) },
                        ) { Text(item.name) }
                    }
                }

                item {
                    ExerciseCreateWidget(
                        onClick = { consume(Action.Click.CreateExercise) },
                        modifier = Modifier.testTag("SingleTrainingCreateExerciseButton"),
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = AppDimension.Padding.big),
                    )
                }

                item {
                    Box(
                        modifier = Modifier.clickable {
                            consume(Action.Click.OpenCalendarPicker)
                        },
                    ) {
                        AppTextField(
                            value = state.training.date.uiValue,
                            onValueChange = {},
                            label = stringResource(R.string.feature_single_training_field_date_label),
                            enabled = false,
                        )
                    }
                }
            }
        }

        when (val dialogState = state.dialogState) {
            DialogState.Closed -> Unit
            DialogState.Calendar -> AppDatePickerDialog(
                initialDateMillis = state.training.date.value,
                onDateSelected = { consume(Action.Input.Date(it)) },
                onDismiss = { consume(Action.Click.CloseCalendarPicker) },
            )

            is DialogState.ConfirmDialog -> AppDialog(
                title = stringResource(dialogState.titleRes),
                body = "",
                confirmLabel = "Confirm",
                onConfirm = { consume(Action.Click.ConfirmDialog.Confirm) },
                dismissLabel = "Cancel",
                onDismiss = { consume(Action.Click.ConfirmDialog.Dismiss) },
                destructive = true,
            )
        }
    }
}

@Composable
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
private fun SingleTrainingsScreenPreview() {
    AppTheme {
        SingleTrainingsScreen(
            state = State(
                training = TrainingUiModel(
                    uuid = "uuid",
                    name = PropertyHolder.StringProperty.new("special training name"),
                    labels = persistentListOf(),
                    exercises = persistentListOf(),
                    date = PropertyHolder.DateProperty.now(),
                    isMenuOpen = false,
                    menuItems = persistentSetOf(),
                ),
                dialogState = DialogState.Closed,
                pendingForCreateUuid = "pendingForCreateUuid",
                initialTrainingUiModel = TrainingUiModel.INITIAL,
            ),
            consume = {},
        )
    }
}
