// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ChartPointsAnimationTest {

    @Test
    fun `same-day sessions keep distinct animation targets and scrub positions`() {
        val day = LocalDate.of(2026, 4, 28)
        val targets = listOf(
            ChartPointUiModel(day, 1_777_334_400_000L, "morning", 80.0, 2),
            ChartPointUiModel(day, 1_777_334_400_000L, "evening", 100.0, 1),
        ).toTargets()

        assertEquals(listOf("morning", "evening"), targets.map { it.key })
        assertEquals(listOf(0f, 1f), targets.map { it.x })
    }
}
