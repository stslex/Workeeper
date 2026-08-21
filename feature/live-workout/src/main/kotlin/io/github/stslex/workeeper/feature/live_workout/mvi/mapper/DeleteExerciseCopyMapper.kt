// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.DeleteExerciseCopyUiModel

internal object DeleteExerciseCopyMapper {

    fun map(
        isAdhocSession: Boolean,
        isMidSessionAdded: Boolean,
    ): DeleteExerciseCopyUiModel = when {
        isAdhocSession -> DeleteExerciseCopyUiModel.ADHOC_WORKOUT
        isMidSessionAdded -> DeleteExerciseCopyUiModel.TEMPLATE_ADDITION
        else -> DeleteExerciseCopyUiModel.TEMPLATE_PLANNED
    }
}
