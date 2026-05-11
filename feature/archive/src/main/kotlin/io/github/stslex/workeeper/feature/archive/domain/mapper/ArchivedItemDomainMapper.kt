// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain

internal object ArchivedItemDomainMapper {

    fun ExerciseTypeDataModel.toDomain(): ExerciseTypeDomain = when (this) {
        ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeDomain.WEIGHTED
        ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeDomain.WEIGHTLESS
    }
}
