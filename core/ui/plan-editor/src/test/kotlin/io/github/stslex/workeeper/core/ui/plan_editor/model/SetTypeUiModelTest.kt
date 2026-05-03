// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel.Companion.toUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SetTypeUiModelTest {

    @Test
    fun `toData maps every UI variant to its domain counterpart`() {
        assertEquals(SetTypeDataModel.WARMUP, SetTypeUiModel.WARMUP.toData())
        assertEquals(SetTypeDataModel.WORK, SetTypeUiModel.WORK.toData())
        assertEquals(SetTypeDataModel.FAILURE, SetTypeUiModel.FAILURE.toData())
        assertEquals(SetTypeDataModel.DROP, SetTypeUiModel.DROP.toData())
    }

    @Test
    fun `companion toUi maps every domain variant to its UI counterpart`() {
        assertEquals(SetTypeUiModel.WARMUP, SetTypeDataModel.WARMUP.toUi())
        assertEquals(SetTypeUiModel.WORK, SetTypeDataModel.WORK.toUi())
        assertEquals(SetTypeUiModel.FAILURE, SetTypeDataModel.FAILURE.toUi())
        assertEquals(SetTypeUiModel.DROP, SetTypeDataModel.DROP.toUi())
    }

    @Test
    fun `toUiKitType maps every variant to the kit's chip enum`() {
        // The kit's SetType uses FAIL (not FAILURE) — this mapping is the bridge.
        assertEquals(SetType.WARMUP, SetTypeUiModel.WARMUP.toUiKitType())
        assertEquals(SetType.WORK, SetTypeUiModel.WORK.toUiKitType())
        assertEquals(SetType.FAIL, SetTypeUiModel.FAILURE.toUiKitType())
        assertEquals(SetType.DROP, SetTypeUiModel.DROP.toUiKitType())
    }

    @Test
    fun `round-trip via toData and toUi yields the original value`() {
        SetTypeUiModel.entries.forEach { ui ->
            assertEquals(ui, ui.toData().toUi())
        }
    }
}
