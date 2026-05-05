// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mapper

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.SetTypeDomain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object PlanEditorMapper {

    fun PlanSetDataModel.toUi(): PlanSetUiModel = PlanSetUiModel(
        weight = weight,
        reps = reps,
        type = type.toUi(),
    )

    fun List<PlanSetDataModel>.toUi(): ImmutableList<PlanSetUiModel> =
        map { it.toUi() }.toImmutableList()

    fun PlanSetUiModel.toData(): PlanSetDataModel = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = type.toData(),
    )

    fun SetTypeUiModel.toData(): SetTypeDataModel = when (this) {
        SetTypeUiModel.WARMUP -> SetTypeDataModel.WARMUP
        SetTypeUiModel.WORK -> SetTypeDataModel.WORK
        SetTypeUiModel.FAILURE -> SetTypeDataModel.FAILURE
        SetTypeUiModel.DROP -> SetTypeDataModel.DROP
    }

    fun SetTypeDataModel.toUi(): SetTypeUiModel = when (this) {
        SetTypeDataModel.WARMUP -> SetTypeUiModel.WARMUP
        SetTypeDataModel.WORK -> SetTypeUiModel.WORK
        SetTypeDataModel.FAILURE -> SetTypeUiModel.FAILURE
        SetTypeDataModel.DROP -> SetTypeUiModel.DROP
    }

    internal fun PlanSetDomain.toUi(): PlanSetUiModel = PlanSetUiModel(
        weight = weight,
        reps = reps,
        type = type.toUi(),
    )

    internal fun SetTypeDomain.toUi(): SetTypeUiModel = when (this) {
        SetTypeDomain.WARMUP -> SetTypeUiModel.WARMUP
        SetTypeDomain.WORK -> SetTypeUiModel.WORK
        SetTypeDomain.FAILURE -> SetTypeUiModel.FAILURE
        SetTypeDomain.DROP -> SetTypeUiModel.DROP
    }

    internal fun PlanSetUiModel.toDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    internal fun SetTypeUiModel.toDomain(): SetTypeDomain = when (this) {
        SetTypeUiModel.WARMUP -> SetTypeDomain.WARMUP
        SetTypeUiModel.WORK -> SetTypeDomain.WORK
        SetTypeUiModel.FAILURE -> SetTypeDomain.FAILURE
        SetTypeUiModel.DROP -> SetTypeDomain.DROP
    }
}
