package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.Property
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PropertyValid
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

@Composable
internal fun ExercisedColumn(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        ExerciseItem(state.name) {
            consume(Action.Input.Property(state.name.type, it))
        }
        Spacer(Modifier.height(AppDimension.Padding.medium))
        ExerciseItem(state.sets) {
            consume(Action.Input.Property(state.sets.type, it))
        }
        Spacer(Modifier.height(AppDimension.Padding.medium))
        ExerciseItem(state.reps) {
            consume(Action.Input.Property(state.reps.type, it))
        }
        Spacer(Modifier.height(AppDimension.Padding.medium))
        ExerciseItem(state.weight) {
            consume(Action.Input.Property(state.weight.type, it))
        }
        Spacer(Modifier.height(AppDimension.Padding.medium))
        ExerciseDateData(state.dateProperty.converted) {
            consume(Action.Click.PickDate)
        }
    }
}

@Composable
private fun ExerciseDateData(
    data: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ExercisePropertyTextField(
        text = data,
        label = "Date",
        modifier = modifier,
        isError = false,
        mode = Mode.DATE,
        onClick = onClick,
        onValueChange = {}
    )
}

@Composable
private fun ExerciseItem(
    item: Property,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    ExercisePropertyTextField(
        text = item.value,
        label = item.type.name,
        modifier = modifier,
        isError = item.valid != PropertyValid.VALID,
        mode = if (item.type == PropertyType.NAME) {
            Mode.TITLE
        } else {
            Mode.NUMBER
        },
        onValueChange = onValueChange
    )
}

@Composable
@Preview(showSystemUi = true, showBackground = false)
private fun ExercisedColumnPreview() {
    AppTheme {
        var state by remember {
            mutableStateOf(
                State.INITIAL.copy(
                    dateProperty = DateProperty.new(System.currentTimeMillis())
                )
            )
        }

        fun processInput(property: Action.Input.Property) {
            state = when (property.type) {
                PropertyType.NAME -> state.copy(
                    name = state.name.update(property.value)
                )
                PropertyType.SETS -> state.copy(
                    sets = state.sets.update(property.value)
                )
                PropertyType.REPS -> state.copy(
                    reps = state.reps.update(property.value)
                )
                PropertyType.WEIGHT -> state.copy(
                    weight = state.weight.update(property.value)
                )
            }
        }
        ExercisedColumn(
            modifier = Modifier.fillMaxHeight(),
            state = state,
            consume = {
                when (it) {
                    is Action.Input.Property -> processInput(it)
                    else -> Unit
                }
            }
        )
    }
}