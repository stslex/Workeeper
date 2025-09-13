package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType.Companion.toUi

@Stable
data class SetsUiModel(
    val uuid: String,
    val reps: Property,
    val weight: Property,
    val type: SetUiType
)

internal fun SetsDataModel.toUi() = SetsUiModel(
    uuid = uuid,
    reps = Property.new(PropertyType.REPS, reps.toString()),
    weight = Property.new(PropertyType.WEIGHT, weight.toString()),
    type = type.toUi()
)

internal fun SetsUiModel.toData() = SetsDataModel(
    uuid = uuid,
    reps = reps.value.toInt(),
    weight = weight.value.toDouble(),
    type = type.toData()
)
