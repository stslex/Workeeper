// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType

enum class SetTypeUiModel(
    val labelRes: Int,
) {
    WARMUP(R.string.core_ui_kit_plan_editor_set_type_warmup),
    WORK(R.string.core_ui_kit_plan_editor_set_type_work),
    FAILURE(R.string.core_ui_kit_plan_editor_set_type_failure),
    DROP(R.string.core_ui_kit_plan_editor_set_type_drop),
    ;

    fun toUiKitType(): SetType = when (this) {
        WARMUP -> SetType.WARMUP
        WORK -> SetType.WORK
        FAILURE -> SetType.FAIL
        DROP -> SetType.DROP
    }

    fun next(): SetTypeUiModel = entries[ordinal.inc() % entries.size]
}
