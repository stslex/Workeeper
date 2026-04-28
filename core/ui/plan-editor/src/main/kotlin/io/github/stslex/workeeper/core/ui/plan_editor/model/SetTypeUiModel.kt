// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType

enum class SetTypeUiModel(
    val labelRes: Int,
) {
    WARMUP(R.string.core_ui_kit_plan_editor_set_type_warmup),
    WORK(R.string.core_ui_kit_plan_editor_set_type_work),
    FAILURE(R.string.core_ui_kit_plan_editor_set_type_failure),
    DROP(R.string.core_ui_kit_plan_editor_set_type_failure),
    ;

    fun toData(): SetTypeDataModel = when (this) {
        WARMUP -> SetTypeDataModel.WARMUP
        WORK -> SetTypeDataModel.WORK
        FAILURE -> SetTypeDataModel.FAILURE
        DROP -> SetTypeDataModel.DROP
    }

    fun toUiKitType(): SetType = when (this) {
        WARMUP -> SetType.WARMUP
        WORK -> SetType.WORK
        FAILURE -> SetType.FAIL
        DROP -> SetType.DROP
    }

    companion object {

        fun SetTypeDataModel.toUi(): SetTypeUiModel = when (this) {
            SetTypeDataModel.WARMUP -> WARMUP
            SetTypeDataModel.WORK -> WORK
            SetTypeDataModel.FAILURE -> FAILURE
            SetTypeDataModel.DROP -> DROP
        }
    }
}
