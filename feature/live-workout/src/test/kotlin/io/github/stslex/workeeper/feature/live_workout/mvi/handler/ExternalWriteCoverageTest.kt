// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExternalSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** wear-live-sync.md §6.4 "Loads", the bookkeeping alone: which writes each load applies again. */
internal class ExternalWriteCoverageTest {

    private val coverage = ExternalWriteCoverage()

    @Test
    fun `a load applies again exactly the writes received after it began, in order`() {
        coverage.receive(write(0))
        val load = ExternalWriteCoverage.Load()
        coverage.begin(load)
        coverage.receive(write(1))
        coverage.receive(write(2))

        assertEquals(listOf(write(1), write(2)), coverage.since(load))
    }

    @Test
    fun `each of two overlapping loads applies again its own writes`() {
        val first = ExternalWriteCoverage.Load()
        val second = ExternalWriteCoverage.Load()
        coverage.begin(first)
        coverage.receive(write(0))
        coverage.begin(second)
        coverage.receive(write(1))
        coverage.end(first)

        assertEquals(listOf(write(1)), coverage.since(second), "the other load's end keeps what this one needs")
    }

    @Test
    fun `the end of a load that never began changes nothing for a load in flight`() {
        val inFlight = ExternalWriteCoverage.Load()
        coverage.begin(inFlight)
        coverage.receive(write(0))

        coverage.end(ExternalWriteCoverage.Load())

        assertEquals(listOf(write(0)), coverage.since(inFlight))
    }

    @Test
    fun `when no load is in flight the kept writes are cleared and new ones are not kept`() {
        val ended = ExternalWriteCoverage.Load()
        coverage.begin(ended)
        coverage.receive(write(0))
        coverage.end(ended)
        coverage.receive(write(1))

        val next = ExternalWriteCoverage.Load()
        coverage.begin(next)

        assertEquals(emptyList<ExternalSetUiModel>(), coverage.since(next))
        assertEquals(emptyList<ExternalSetUiModel>(), coverage.since(ended), "an ended load applies nothing")
    }

    private fun write(position: Int) = ExternalSetUiModel(
        performedExerciseUuid = "pe-1",
        set = LiveSetUiModel(position = position, weight = 105.0, reps = 8, type = SetTypeUiModel.WORK, isDone = true),
    )
}
