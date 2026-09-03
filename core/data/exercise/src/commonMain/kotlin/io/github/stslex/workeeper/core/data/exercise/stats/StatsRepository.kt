// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.stats

/** Read-only access to whole-session aggregates for the Achievement block and Stats dashboard. */
interface StatsRepository {

    /** Top finished sessions by volume since [sinceMillis], highest-first; weighted sets only. */
    suspend fun getBestSessionVolumes(
        sinceMillis: Long,
        limit: Int,
    ): List<BestSessionVolumeDataModel>
}
