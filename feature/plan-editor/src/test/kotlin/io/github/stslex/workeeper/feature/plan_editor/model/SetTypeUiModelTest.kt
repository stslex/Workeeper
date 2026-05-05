// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.model

import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SetTypeUiModelTest {

    @Test
    fun `toUiKitType maps every variant to the kit's chip enum`() {
        // The kit's SetType uses FAIL (not FAILURE) — this mapping is the bridge.
        assertEquals(SetType.WARMUP, SetTypeUiModel.WARMUP.toUiKitType())
        assertEquals(SetType.WORK, SetTypeUiModel.WORK.toUiKitType())
        assertEquals(SetType.FAIL, SetTypeUiModel.FAILURE.toUiKitType())
        assertEquals(SetType.DROP, SetTypeUiModel.DROP.toUiKitType())
    }

    @Test
    fun `DROP labelRes resolves to drop string and not failure`() {
        assertEquals(R.string.core_ui_kit_plan_editor_set_type_drop, SetTypeUiModel.DROP.labelRes)
        assertNotEquals(SetTypeUiModel.FAILURE.labelRes, SetTypeUiModel.DROP.labelRes)
    }

    @Test
    fun `every SetTypeUiModel has a unique labelRes`() {
        val labels = SetTypeUiModel.entries.map { it.labelRes }
        assertEquals(labels.size, labels.toSet().size)
    }
}
