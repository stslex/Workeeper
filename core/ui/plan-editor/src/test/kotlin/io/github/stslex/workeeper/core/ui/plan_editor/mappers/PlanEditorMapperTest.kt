// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mappers

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel.Companion.toUi
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlanEditorMapperTest {

    @Test
    fun `toUi maps a single PlanSetDataModel preserving weight, reps, and type`() {
        val data = PlanSetDataModel(
            weight = 80.0,
            reps = 5,
            type = SetTypeDataModel.WORK,
        )
        val ui = data.toUi()
        assertEquals(80.0, ui.weight)
        assertEquals(5, ui.reps)
        assertEquals(SetTypeUiModel.WORK, ui.type)
    }

    @Test
    fun `toUi propagates a null weight`() {
        val data = PlanSetDataModel(
            weight = null,
            reps = 12,
            type = SetTypeUiModel.WORK.toData(),
        )
        val ui = data.toUi()
        assertEquals(null, ui.weight)
        assertEquals(12, ui.reps)
    }

    @Test
    fun `List toUi maps a non-empty list to ImmutableList of UI rows`() {
        val data = listOf(
            PlanSetDataModel(60.0, 10, SetTypeDataModel.WARMUP),
            PlanSetDataModel(80.0, 8, SetTypeDataModel.WORK),
            PlanSetDataModel(70.0, 6, SetTypeDataModel.FAILURE),
            PlanSetDataModel(50.0, 4, SetTypeDataModel.DROP),
        )
        val ui = data.toUi()
        assertEquals(4, ui.size)
        assertEquals(SetTypeUiModel.WARMUP, ui[0].type)
        assertEquals(SetTypeUiModel.WORK, ui[1].type)
        assertEquals(SetTypeUiModel.FAILURE, ui[2].type)
        assertEquals(SetTypeUiModel.DROP, ui[3].type)
    }

    @Test
    fun `List toUi maps an empty list to an empty ImmutableList`() {
        val ui = emptyList<PlanSetDataModel>().toUi()
        assertEquals(0, ui.size)
    }

    @Test
    fun `toData round-trips back to the equivalent PlanSetDataModel`() {
        val original = PlanSetDataModel(80.0, 5, SetTypeDataModel.WORK)
        val roundTripped = original.toUi().toData()
        assertEquals(original, roundTripped)
    }

    @Test
    fun `SetTypeUiModel toData maps every variant correctly`() {
        assertEquals(SetTypeDataModel.WARMUP, SetTypeUiModel.WARMUP.toData())
        assertEquals(SetTypeDataModel.WORK, SetTypeUiModel.WORK.toData())
        assertEquals(SetTypeDataModel.FAILURE, SetTypeUiModel.FAILURE.toData())
        assertEquals(SetTypeDataModel.DROP, SetTypeUiModel.DROP.toData())
    }

    @Test
    fun `SetTypeDataModel toUi maps every variant correctly`() {
        assertEquals(SetTypeUiModel.WARMUP, SetTypeDataModel.WARMUP.toUi())
        assertEquals(SetTypeUiModel.WORK, SetTypeDataModel.WORK.toUi())
        assertEquals(SetTypeUiModel.FAILURE, SetTypeDataModel.FAILURE.toUi())
        assertEquals(SetTypeUiModel.DROP, SetTypeDataModel.DROP.toUi())
    }

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
