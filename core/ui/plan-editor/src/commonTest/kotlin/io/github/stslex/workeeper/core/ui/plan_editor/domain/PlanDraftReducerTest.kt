// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.domain

import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PlanDraftReducerTest {

    private fun draftOf(vararg sets: PlanSetUiModel): ImmutableList<PlanSetUiModel> =
        sets.toList().toImmutableList()

    private fun set(
        weight: Double? = 80.0,
        reps: Int = 8,
        type: SetTypeUiModel = SetTypeUiModel.WORK,
    ): PlanSetUiModel = PlanSetUiModel(weight = weight, reps = reps, type = type)

    @Test
    fun `OnAddSet on empty draft appends a default WORK set`() {
        val result = PlanDraftReducer.reduce(
            draft = persistentListOf(),
            action = PlanEditorBodyAction.OnAddSet,
            isWeighted = true,
        )

        assertEquals(1, result.size)
        val added = result.first()
        assertEquals(SetTypeUiModel.WORK, added.type)
        assertEquals(5, added.reps)
        assertNull(added.weight)
    }

    @Test
    fun `OnAddSet copies reps and weight from previous set but cycles type back to WORK`() {
        val draft = draftOf(set(weight = 80.0, reps = 8, type = SetTypeUiModel.WARMUP))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnAddSet,
            isWeighted = true,
        )

        assertEquals(2, result.size)
        val added = result.last()
        assertEquals(8, added.reps)
        assertEquals(80.0, added.weight)
        assertEquals(SetTypeUiModel.WORK, added.type)
    }

    @Test
    fun `OnSetRemove drops the row at the given index`() {
        val draft = draftOf(
            set(weight = 60.0, reps = 10, type = SetTypeUiModel.WARMUP),
            set(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
            set(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        )

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRemove(index = 1),
            isWeighted = true,
        )

        assertEquals(2, result.size)
        assertEquals(60.0, result[0].weight)
        assertEquals(100.0, result[1].weight)
    }

    @Test
    fun `OnSetRemove at index 0 drops the head row`() {
        val draft = draftOf(set(weight = 60.0), set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRemove(index = 0),
            isWeighted = true,
        )

        assertEquals(1, result.size)
        assertEquals(80.0, result[0].weight)
    }

    @Test
    fun `OnSetRemove at last index drops the tail row`() {
        val draft = draftOf(set(weight = 60.0), set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRemove(index = 1),
            isWeighted = true,
        )

        assertEquals(1, result.size)
        assertEquals(60.0, result[0].weight)
    }

    @Test
    fun `OnSetRemove with out-of-bounds index returns the draft unchanged`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRemove(index = 5),
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnSetRemove with negative index returns the draft unchanged`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRemove(index = -1),
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnSetRemove on an empty draft is a no-op`() {
        val result = PlanDraftReducer.reduce(
            draft = persistentListOf(),
            action = PlanEditorBodyAction.OnSetRemove(index = 0),
            isWeighted = true,
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `OnSetTypeChange updates the type of the row at the given index`() {
        val draft = draftOf(
            set(type = SetTypeUiModel.WORK),
            set(type = SetTypeUiModel.WORK),
        )

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetTypeChange(
                index = 1,
                value = SetTypeUiModel.FAILURE,
            ),
            isWeighted = true,
        )

        assertEquals(SetTypeUiModel.WORK, result[0].type)
        assertEquals(SetTypeUiModel.FAILURE, result[1].type)
    }

    @Test
    fun `OnSetTypeChange with out-of-bounds index returns the draft unchanged`() {
        val draft = draftOf(set(type = SetTypeUiModel.WORK))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetTypeChange(
                index = 7,
                value = SetTypeUiModel.DROP,
            ),
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnSetWeightChange updates the weight of the row at the given index`() {
        val draft = draftOf(set(weight = 80.0), set(weight = 90.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetWeightChange(index = 1, value = 95.5),
            isWeighted = true,
        )

        assertEquals(80.0, result[0].weight)
        assertEquals(95.5, result[1].weight)
    }

    @Test
    fun `OnSetWeightChange with null weight clears the weight on that row`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetWeightChange(index = 0, value = null),
            isWeighted = true,
        )

        assertNull(result[0].weight)
    }

    @Test
    fun `OnSetWeightChange with out-of-bounds index returns the draft unchanged`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetWeightChange(index = 9, value = 100.0),
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnSetRepsChange updates the reps of the row at the given index`() {
        val draft = draftOf(set(reps = 8), set(reps = 5))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRepsChange(index = 0, value = 12),
            isWeighted = true,
        )

        assertEquals(12, result[0].reps)
        assertEquals(5, result[1].reps)
    }

    @Test
    fun `OnSetRepsChange clamps negative reps to zero`() {
        val draft = draftOf(set(reps = 8))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRepsChange(index = 0, value = -3),
            isWeighted = true,
        )

        assertEquals(0, result[0].reps)
    }

    @Test
    fun `OnSetRepsChange with out-of-bounds index returns the draft unchanged`() {
        val draft = draftOf(set(reps = 8))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSetRepsChange(index = 4, value = 10),
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnDismiss returns the draft unchanged`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnDismiss,
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnSave returns the draft unchanged`() {
        val draft = draftOf(set(weight = 80.0))

        val result = PlanDraftReducer.reduce(
            draft = draft,
            action = PlanEditorBodyAction.OnSave,
            isWeighted = true,
        )

        assertEquals(draft, result)
    }

    @Test
    fun `OnAddSet on empty draft uses the same default reps when isWeighted is false`() {
        val result = PlanDraftReducer.reduce(
            draft = persistentListOf(),
            action = PlanEditorBodyAction.OnAddSet,
            isWeighted = false,
        )

        assertEquals(1, result.size)
        assertEquals(5, result.first().reps)
        assertNull(result.first().weight)
    }
}
