// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel

/**
 * Single source of truth for "is this exercise done". Two entry points exist
 * because the live path also has draft rows the user has typed but not yet
 * checked, while the load path is replaying persisted state and never sees
 * drafts.
 *
 * Both entry points compute an `expectedPositions` set and require every
 * position in that set to map to a performed-and-done row. The set always
 * includes plan indices and the positions of performed rows; the live path
 * additionally folds in `visibleSets.indices` so a typed-but-unchecked draft
 * at the bottom of an adhoc card keeps the exercise CURRENT instead of
 * promoting it to DONE.
 *
 * See `documentation/feature-specs/live-workout.md` for the broader status
 * derivation pipeline.
 */
internal object ExerciseDoneRule {

    /** Live path: drafts may exist, so visibleSets indices count as expected. */
    fun isDoneLive(
        planSets: List<*>,
        performedSets: List<LiveSetUiModel>,
        visibleSets: List<*>,
        skipped: Boolean,
    ): Boolean {
        if (skipped) return false
        val expectedPositions = buildSet {
            addAll(planSets.indices)
            addAll(visibleSets.indices)
            performedSets.forEach { add(it.position) }
        }
        if (expectedPositions.isEmpty()) return false
        val performedByPosition = performedSets.associateBy { it.position }
        return expectedPositions.all { performedByPosition[it]?.isDone == true }
    }

    /** Load path: no drafts (not persisted), only plan + performed are expected. */
    fun isDoneLoad(
        planSets: List<*>,
        performedSets: List<LiveSetUiModel>,
        skipped: Boolean,
    ): Boolean {
        if (skipped) return false
        val expectedPositions = buildSet {
            addAll(planSets.indices)
            performedSets.forEach { add(it.position) }
        }
        if (expectedPositions.isEmpty()) return false
        val performedByPosition = performedSets.associateBy { it.position }
        return expectedPositions.all { performedByPosition[it]?.isDone == true }
    }
}
