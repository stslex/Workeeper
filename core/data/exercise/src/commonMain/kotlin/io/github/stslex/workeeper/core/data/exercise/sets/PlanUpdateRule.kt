// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.sets

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel

object PlanUpdateRule {

    /**
     * Apply the hybrid grow-but-not-shrink rule when a session finishes:
     * - empty performed → keep existing plan (the user skipped the exercise)
     * - performed.size >= existing.size → replace plan with performed
     * - performed.size < existing.size → replace first N positions, keep tail
     */
    fun update(
        existingPlan: List<PlanSetDataModel>?,
        performed: List<PlanSetDataModel>,
    ): List<PlanSetDataModel>? {
        if (performed.isEmpty()) return existingPlan
        val existing = existingPlan.orEmpty()
        return if (performed.size >= existing.size) {
            performed
        } else {
            performed + existing.drop(performed.size)
        }
    }
}
