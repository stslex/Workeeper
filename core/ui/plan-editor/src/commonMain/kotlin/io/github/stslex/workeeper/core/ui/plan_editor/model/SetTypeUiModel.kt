// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_drop
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_failure
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_warmup
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_set_type_work
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
enum class SetTypeUiModel(
    val labelRes: StringResource,
) {
    WARMUP(Res.string.core_ui_kit_plan_editor_set_type_warmup),
    WORK(Res.string.core_ui_kit_plan_editor_set_type_work),
    FAILURE(Res.string.core_ui_kit_plan_editor_set_type_failure),
    DROP(Res.string.core_ui_kit_plan_editor_set_type_drop),
    ;

    fun toUiKitType(): SetType = when (this) {
        WARMUP -> SetType.WARMUP
        WORK -> SetType.WORK
        FAILURE -> SetType.FAIL
        DROP -> SetType.DROP
    }

    fun next(): SetTypeUiModel = entries[ordinal.inc() % entries.size]
}
