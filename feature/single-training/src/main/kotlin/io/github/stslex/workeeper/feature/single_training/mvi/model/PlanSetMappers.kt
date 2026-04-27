// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.model

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.kit.components.sheet.PlanEditorSet

/**
 * AppPlanEditor lives in `core/ui/kit` and uses kit-local types ([PlanEditorSet],
 * [SetType]) to keep the design system free of database coupling. These mappers convert
 * between the kit shape and [PlanSetDataModel] used at the persistence boundary.
 */
internal fun PlanSetDataModel.toEditor(): PlanEditorSet = PlanEditorSet(
    weight = weight,
    reps = reps,
    type = when (type) {
        SetTypeDataModel.WARMUP -> SetType.WARMUP
        SetTypeDataModel.WORK -> SetType.WORK
        SetTypeDataModel.FAILURE -> SetType.FAIL
        SetTypeDataModel.DROP -> SetType.DROP
    },
)

internal fun PlanEditorSet.toData(): PlanSetDataModel = PlanSetDataModel(
    weight = weight,
    reps = reps,
    type = when (type) {
        SetType.WARMUP -> SetTypeDataModel.WARMUP
        SetType.WORK -> SetTypeDataModel.WORK
        SetType.FAIL -> SetTypeDataModel.FAILURE
        SetType.DROP -> SetTypeDataModel.DROP
    },
)
