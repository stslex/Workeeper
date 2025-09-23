package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.DateInputField
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder.Companion.update
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.TitleTextInputField
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

@Composable
internal fun ExercisedColumn(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            TitleTextInputField(
                property = state.name,
                isMenuOpen = state.isMenuOpen,
                menuItems = state.menuItems,
                labelRes = R.string.feature_exercise_field_label_name,
                onMenuClick = { consume(Action.Click.OpenMenuVariants) },
                onMenuClose = { consume(Action.Click.CloseMenuVariants) },
                onMenuItemClick = { consume(Action.Click.OnMenuItemClick(it)) },
                onValueChange = { consume(Action.Input.PropertyName(it)) }
            )
        }

        item { Spacer(Modifier.height(AppDimension.Padding.large)) }

        item {
            Column {
                Text(
                    text = stringResource(R.string.feature_exercise_field_label_sets) + ":",
                    style = MaterialTheme.typography.headlineLarge
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = AppDimension.Padding.big))
                AnimatedVisibility(state.sets.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(vertical = AppDimension.Padding.medium),
                        text = stringResource(R.string.feature_exercise_field_label_sets_are_empty)
                    )
                }
            }
        }

        state.sets.forEach { set ->
            item {
                ExerciseSetsField(
                    property = set,
                    onClick = { item -> consume(Action.Click.DialogSets.OpenEdit(item)) }
                )
            }
            item { Spacer(Modifier.height(AppDimension.Padding.medium)) }
        }

        item { Spacer(Modifier.height(AppDimension.Padding.medium)) }

        item {
            ExerciseSetsCreateWidget(
                onClick = { consume(Action.Click.DialogSets.OpenCreate) }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = AppDimension.Padding.big))
        }

        item {
            DateInputField(
                property = state.dateProperty,
                labelRes = R.string.feature_exercise_field_label_date,
                onClick = {
                    consume(Action.Click.PickDate)
                },
            )
        }
    }
}

@Composable
@Preview(showSystemUi = true, showBackground = false)
private fun ExercisedColumnPreview() {
    AppTheme {
        var state by remember {
            mutableStateOf(
                State.INITIAL.copy(
                    dateProperty = PropertyHolder.DateProperty().update(System.currentTimeMillis())
                )
            )
        }

        Box {
            ExercisedColumn(
                modifier = Modifier
                    .padding(AppDimension.Padding.big),
                state = state,
                consume = {}
            )
        }
    }
}