package io.github.stslex.workeeper.feature.single_training.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.dialogs.ConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.DateInputField
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.TitleTextInputField
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.ui.component.DatePickerDialog
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
            .systemBarsPadding(),
    ) {
        Column {
            ToolbarRow(
                isDeleteVisible = state.training.uuid.isNotBlank(),
                onConfirmClick = { consume(Action.Click.Save) },
                onCancelClick = { consume(Action.Click.Close) },
                onDeleteClick = { consume(Action.Click.DeleteDialogOpen) },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimension.Padding.large),
            ) {
                item {
                    TitleTextInputField(
                        property = state.training.name,
                        isMenuOpen = state.training.isMenuOpen,
                        menuItems = state.training.menuItems,
                        labelRes = R.string.feature_single_training_field_name_label,
                        onMenuClick = { consume(Action.Click.Menu.Open) },
                        onMenuClose = { consume(Action.Click.Menu.Close) },
                        onMenuItemClick = { consume(Action.Click.Menu.Item(it)) },
                        onValueChange = { consume(Action.Input.Name(it)) },
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
                            text = "There is no Exercises now - create or add new one",
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
                        // todo replace and refactor to new ui
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { consume(Action.Click.ExerciseClick(item.uuid)) },
                        ) { Text(item.name) }
                    }
                }

                item {
                    ExerciseCreateWidget(
                        onClick = { consume(Action.Click.CreateExercise) },
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = AppDimension.Padding.big),
                    )
                }

                item {
                    DateInputField(
                        property = state.training.date,
                        labelRes = R.string.feature_single_training_field_date_label,
                        onClick = { consume(Action.Click.OpenCalendarPicker) },
                    )
                }
            }
        }

        when (val dialogState = state.dialogState) {
            DialogState.Closed -> Unit
            DialogState.Calendar -> DatePickerDialog(
                timestamp = state.training.date.value,
                dateChange = { consume(Action.Input.Date(it)) },
                onDismissRequest = { consume(Action.Click.CloseCalendarPicker) },
            )

            is DialogState.ConfirmDialog -> ConfirmDialog(
                text = stringResource(dialogState.titleRes),
                action = { consume(Action.Click.ConfirmDialog.Confirm) },
                onDismissRequest = { consume(Action.Click.ConfirmDialog.Dismiss) },
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
