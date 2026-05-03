// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExerciseTypeUiModelTest {

    @Test
    fun `toData maps every UI variant to its domain counterpart`() {
        assertEquals(ExerciseTypeDataModel.WEIGHTED, ExerciseTypeUiModel.WEIGHTED.toData())
        assertEquals(ExerciseTypeDataModel.WEIGHTLESS, ExerciseTypeUiModel.WEIGHTLESS.toData())
    }

    @Test
    fun `companion toUi maps every domain variant to its UI counterpart`() {
        assertEquals(ExerciseTypeUiModel.WEIGHTED, ExerciseTypeDataModel.WEIGHTED.toUi())
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, ExerciseTypeDataModel.WEIGHTLESS.toUi())
    }

    @Test
    fun `round-trip via toData and toUi yields the original value`() {
        ExerciseTypeUiModel.entries.forEach { ui ->
            assertEquals(ui, ui.toData().toUi())
        }
    }
}
