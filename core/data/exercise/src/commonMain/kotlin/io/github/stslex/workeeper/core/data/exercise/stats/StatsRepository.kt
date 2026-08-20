// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.stats

/**
 * Read-only access to whole-session aggregates that drive the v2.3 Achievement block and
 * the Stats dashboard. Reads are always live (no caching layer in v2.0).
 */
interface StatsRepository {

    /**
     * Returns the top finished sessions by total volume since [sinceMillis], ordered
     * highest-first. The query honors the locked decision that only weighted exercises
     * contribute to volume — weightless sets are excluded before the sum.
     */
    suspend fun getBestSessionVolumes(
        sinceMillis: Long,
        limit: Int,
    ): List<BestSessionVolumeDataModel>
}
