package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType.Companion.toUi
import kotlin.uuid.Uuid

@Stable
data class SetsUiModel(
    val uuid: String,
    val reps: PropertyHolder.IntProperty,
    val weight: PropertyHolder.DoubleProperty,
    val type: SetUiType,
) {

    companion object {

        internal val EMPTY = SetsUiModel(
            uuid = Uuid.random().toString(),
            reps = PropertyHolder.IntProperty.new(),
            weight = PropertyHolder.DoubleProperty.new(),
            type = SetUiType.WORK,
        )
    }
}

internal fun SetsDataModel.toUi() = SetsUiModel(
    uuid = uuid,
    reps = PropertyHolder.IntProperty.new(reps),
    weight = PropertyHolder.DoubleProperty.new(weight),
    type = type.toUi(),
)

internal fun SetsUiModel.toData() = SetsDataModel(
    uuid = uuid,
    reps = reps.value,
    weight = weight.value,
    type = type.toData(),
)
