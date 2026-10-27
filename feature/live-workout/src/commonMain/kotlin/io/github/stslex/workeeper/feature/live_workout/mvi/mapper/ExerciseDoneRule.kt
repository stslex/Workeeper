// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel

/**
 * Single source of truth for "is this exercise done"; two entry points because only the live
 * path sees drafts. See documentation/feature-specs/live-workout.md.
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
