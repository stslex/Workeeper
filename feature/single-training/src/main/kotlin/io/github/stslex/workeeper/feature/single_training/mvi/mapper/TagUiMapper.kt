// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.mvi.model.TagUiModel

internal fun TagDomain.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)

internal fun ExerciseTypeDomain.toUi(): ExerciseTypeUiModel = when (this) {
    ExerciseTypeDomain.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
    ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
}

internal fun ExerciseTypeUiModel.toDomain(): ExerciseTypeDomain = when (this) {
    ExerciseTypeUiModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
    ExerciseTypeUiModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
}

internal fun SetTypeDomain.toUi(): SetTypeUiModel = when (this) {
    SetTypeDomain.WARMUP -> SetTypeUiModel.WARMUP
    SetTypeDomain.WORK -> SetTypeUiModel.WORK
    SetTypeDomain.FAILURE -> SetTypeUiModel.FAILURE
    SetTypeDomain.DROP -> SetTypeUiModel.DROP
}

internal fun SetTypeUiModel.toDomain(): SetTypeDomain = when (this) {
    SetTypeUiModel.WARMUP -> SetTypeDomain.WARMUP
    SetTypeUiModel.WORK -> SetTypeDomain.WORK
    SetTypeUiModel.FAILURE -> SetTypeDomain.FAILURE
    SetTypeUiModel.DROP -> SetTypeDomain.DROP
}

internal fun PlanSetDomain.toUi(): PlanSetUiModel = PlanSetUiModel(
    weight = weight,
    reps = reps,
    type = type.toUi(),
)

internal fun PlanSetUiModel.toDomain(): PlanSetDomain = PlanSetDomain(
    weight = weight,
    reps = reps,
    type = type.toDomain(),
)
