// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.stats

/**
 * Aggregate of one finished session's total volume (Σ weight × reps over weighted sets).
 * Drives the v2.3 Achievement-block volume fallback and Stats dashboard.
 */
data class BestSessionVolumeDataModel(
    val sessionUuid: String,
    val trainingUuid: String,
    val finishedAt: Long,
    val volume: Double,
)
