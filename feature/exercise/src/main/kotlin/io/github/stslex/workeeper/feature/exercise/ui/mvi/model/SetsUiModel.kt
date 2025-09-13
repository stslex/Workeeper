package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType.Companion.toUi

@Stable
 data class SetsUiModel(
    val reps: Int,
    val weight: Double,
    val type: SetUiType
)

internal fun SetsDataModel.toUi() = SetsUiModel(
    reps = reps,
    weight = weight,
    type = type.toUi()
)
