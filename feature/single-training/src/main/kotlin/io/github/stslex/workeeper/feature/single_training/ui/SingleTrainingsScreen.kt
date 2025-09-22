package io.github.stslex.workeeper.feature.single_training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.ui.component.DatePickerDialog
import io.github.stslex.workeeper.feature.single_training.ui.component.ExerciseCreateWidget
import io.github.stslex.workeeper.feature.single_training.ui.component.ToolbarRow
import io.github.stslex.workeeper.feature.single_training.ui.component.TrainingPropertyTextField
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TextMode
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun SingleTrainingsScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            ToolbarRow(
                isDeleteVisible = state.training.uuid.isNotBlank(),
                onConfirmClick = { consume(Action.Click.Save) },
                onCancelClick = { consume(Action.Click.Close) },
                onDeleteClick = { consume(Action.Click.Delete) },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppDimension.Padding.large)
        ) {
            item {
                TrainingPropertyTextField(
                    text = state.training.name,
                    labelRes = R.string.feature_single_training_field_name_label,
                    mode = TextMode.NUMBER,
                ) {
                    consume(Action.Input.Name(it))
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AppDimension.Padding.big)
                )
            }

            item {
                Text("Exercises: ")
            }

            if (state.training.exercises.isEmpty()) {
                item {
                    Text("There is no Exercises now - create or add new one")
                }
            } else {
                items(
                    count = state.training.exercises.size,
                    key = {
                        state.training.exercises[it].uuid
                    }
                ) { index ->
                    val item = state.training.exercises[index]
                    // todo replace and refactor to new ui
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        onClick = { consume(Action.Click.ExerciseClick(item.uuid)) }
                    ) { Text(item.name) }
                }
            }

            item {
                ExerciseCreateWidget(
                    onClick = { consume(Action.Click.CreateExercise) }
                )
            }


            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AppDimension.Padding.big)
                )
            }

            item {
                TrainingPropertyTextField(
                    text = state.training.date.converted,
                    labelRes = R.string.feature_single_training_field_date_label,
                    mode = TextMode.DATE,
                    onValueChange = {},
                    onClick = { consume(Action.Click.OpenCalendarPicker) }
                )
            }
        }

        when (state.dialogState) {
            DialogState.Closed -> Unit
            DialogState.Calendar -> DatePickerDialog(
                timestamp = state.training.date.timestamp,
                dateChange = { consume(Action.Input.Date(it)) },
                onDismissRequest = { consume(Action.Click.CloseCalendarPicker) }
            )
        }
    }
}

@Preview
@Composable
private fun SingleTrainingsScreenPreview() {
    AppTheme {
        SingleTrainingsScreen(
            state = State(
                training = TrainingUiModel(
                    uuid = "uuid",
                    name = "special training name",
                    labels = persistentListOf(),
                    exercises = persistentListOf(),
                    date = DateProperty.new(System.currentTimeMillis())
                ),
                dialogState = DialogState.Closed,
                pendingForCreateUuid = "pendingForCreateUuid",
            ),
            consume = {}
        )
    }
}