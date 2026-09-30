// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandDraft
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchReducerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearSurfaceDraftProjectionTest {

    @Test
    fun activeDraftProjectionMarksUnsubmittedValues() {
        val drafts = listOf(CommandDraft(12, null), CommandDraft(12, 12_345), CommandDraft(8, 10_000))
        ActiveFreshness.entries.forEach { freshness ->
            drafts.forEach { draft ->
                val state = WatchReducerState(
                    display = WatchDisplayState.Active(ReducerTestFixtures.active(), freshness),
                    draft = draft,
                )
                val model = WearSurfaceMapper.map(state)
                assertEquals(draft.reps, model.reps)
                assertEquals(draft.weightHundredthsKg, model.weightHundredthsKg)
                assertTrue(model.hasUnsubmittedDraft, "Every projected active draft must retain its unsent marker")

                val canonical = WearSurfaceMapper.map(state.copy(draft = null))
                assertEquals(8, canonical.reps)
                assertEquals(10_000, canonical.weightHundredthsKg)
                assertFalse(canonical.hasUnsubmittedDraft, "Clearing the draft must clear its unsent marker")
            }
        }
    }

    @Test
    fun canonicalAndTargetlessProjectionsDoNotClaimUnsubmittedValues() {
        val canonical = WatchReducerState(
            display = WatchDisplayState.Active(ReducerTestFixtures.active(), ActiveFreshness.FRESH),
        )
        assertFalse(WearSurfaceMapper.map(canonical).hasUnsubmittedDraft)
        val targetless = listOf(
            WatchReducerState(),
            WatchReducerState(display = WatchDisplayState.NoSession(ReducerTestFixtures.epoch)),
        )
        targetless.forEach { state ->
            listOf(null, CommandDraft(12, null)).forEach { draft ->
                val model = WearSurfaceMapper.map(state.copy(draft = draft))
                assertFalse(model.controlsVisible)
                assertFalse(model.hasUnsubmittedDraft, "A targetless projection does not apply a numeric draft")
            }
        }
    }
}
