// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SetSummaryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toUi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ExerciseUiMapperTest {

    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true).apply {
        every { formatDayMonth(any()) } returns "22 июля"
        every { formatMediumDate(any()) } returns "12 июля 2026 г."
    }

    @Test
    fun `weighted record splits into trimmed weight and reps with the medium date`() {
        val ui = record(weight = 9.0).toUi(resourceWrapper, ExerciseTypeUiModel.WEIGHTED)

        assertEquals("9", ui.weightLabel)
        assertEquals("12", ui.repsLabel)
        assertEquals("12 июля 2026 г.", ui.absoluteDateLabel)
    }

    @Test
    fun `fractional record weight trims trailing zeros`() {
        val ui = record(weight = 62.50).toUi(resourceWrapper, ExerciseTypeUiModel.WEIGHTED)

        assertEquals("62.5", ui.weightLabel)
    }

    @Test
    fun `weightless record carries no weight label`() {
        val ui = record(weight = null).toUi(resourceWrapper, ExerciseTypeUiModel.WEIGHTLESS)

        assertNull(ui.weightLabel)
        assertEquals("12", ui.repsLabel)
    }

    @Test
    fun `history entry maps day-month date and the tight summary form`() {
        val ui = historyEntry(
            sets = listOf(set(7.0, 12), set(7.0, 12)),
        ).toUi(resourceWrapper)

        assertEquals("22 июля", ui.dateLabel)
        assertEquals("7×12 · 7×12", ui.setsSummaryLabel)
    }

    @Test
    fun `history summary caps at five sets and appends an ellipsis`() {
        val ui = historyEntry(
            sets = List(7) { set(60.0, 10) },
        ).toUi(resourceWrapper)

        assertEquals("60×10 · 60×10 · 60×10 · 60×10 · 60×10 · …", ui.setsSummaryLabel)
    }

    @Test
    fun `weightless history sets summarize as bare reps`() {
        val ui = historyEntry(
            sets = listOf(set(null, 8), set(null, 8)),
        ).toUi(resourceWrapper)

        assertEquals("8 · 8", ui.setsSummaryLabel)
    }

    private fun record(weight: Double?): PersonalRecordDomain = PersonalRecordDomain(
        sessionUuid = "s-1",
        performedExerciseUuid = "pe-1",
        setUuid = "set-1",
        weight = weight,
        reps = 12,
        type = SetTypeDomain.WORK,
        finishedAt = 1_752_300_000_000L,
    )

    private fun historyEntry(sets: List<SetSummaryDomain>): HistoryEntryDomain = HistoryEntryDomain(
        sessionUuid = "s-1",
        finishedAt = 1_753_164_000_000L,
        trainingName = "верх (с подтягиваниями)",
        isAdhoc = false,
        sets = sets,
    )

    private fun set(weight: Double?, reps: Int): SetSummaryDomain = SetSummaryDomain(
        weight = weight,
        reps = reps,
        type = SetTypeDomain.WORK,
    )
}
