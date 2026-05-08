// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain.mapper

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.SetTypeDomain

internal object PlanEditorDomainMapper {

    fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }

    fun ExerciseTypeDomain.toData(): ExerciseTypeDataModel = when (this) {
        ExerciseTypeDomain.WEIGHTED -> ExerciseTypeDataModel.WEIGHTED
        ExerciseTypeDomain.WEIGHTLESS -> ExerciseTypeDataModel.WEIGHTLESS
    }

    fun PlanSetDataModel.toDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    fun PlanSetDomain.toData(): PlanSetDataModel = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = type.toData(),
    )

    fun SetTypeDataModel.toDomain(): SetTypeDomain = when (this) {
        SetTypeDataModel.WARMUP -> SetTypeDomain.WARMUP
        SetTypeDataModel.WORK -> SetTypeDomain.WORK
        SetTypeDataModel.FAILURE -> SetTypeDomain.FAILURE
        SetTypeDataModel.DROP -> SetTypeDomain.DROP
    }

    fun SetTypeDomain.toData(): SetTypeDataModel = when (this) {
        SetTypeDomain.WARMUP -> SetTypeDataModel.WARMUP
        SetTypeDomain.WORK -> SetTypeDataModel.WORK
        SetTypeDomain.FAILURE -> SetTypeDataModel.FAILURE
        SetTypeDomain.DROP -> SetTypeDataModel.DROP
    }
}
