// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mappers

import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorUIMapper.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

internal class PlanEditorMapperTest {

    @Test
    fun `formatPlanSummary joins rows with bullet separators and formats integer weights`() {
        val ui = persistentListOf(
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WARMUP),
            PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        assertEquals("60×10 · 80×8", ui.formatPlanSummary())
    }

    @Test
    fun `formatPlanSummary keeps decimals for non-integer weights`() {
        val ui = persistentListOf(
            PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
        )
        assertEquals("102.5×5", ui.formatPlanSummary())
    }

    @Test
    fun `formatPlanSummary falls back to reps-only when weight is null`() {
        val ui = persistentListOf(
            PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.FAILURE),
        )
        assertEquals("12 · 8", ui.formatPlanSummary())
    }

    @Test
    fun `formatPlanSummary truncates after the fifth row with an ellipsis suffix`() {
        val ui = persistentListOf(
            PlanSetUiModel(weight = 50.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 5, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 60.0, reps = 5, type = SetTypeUiModel.WORK),
        )
        val summary = ui.formatPlanSummary()
        assertEquals("50×5 · 50×5 · 50×5 · 50×5 · 50×5 · …", summary)
    }

    @Test
    fun `formatPlanSummary on empty list yields an empty string`() {
        assertEquals("", persistentListOf<PlanSetUiModel>().formatPlanSummary())
    }
}
