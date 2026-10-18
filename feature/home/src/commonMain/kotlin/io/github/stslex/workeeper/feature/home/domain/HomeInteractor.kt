// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import kotlinx.coroutines.flow.Flow

interface HomeInteractor {

    fun observeActiveSession(): Flow<ActiveSessionWithStatsDomain?>

    /** The readout for [mode] (§3): its data or its own empty state, as of [nowMillis]. */
    fun observeStartCardReadout(
        mode: StartCardModeDomain,
        nowMillis: Long,
    ): Flow<StartCardReadoutDomain>

    /** The persisted readout mode (HS6); «Неделя» while the key is absent or unknown. */
    fun observeStartCardMode(): Flow<StartCardModeDomain>

    /** Persists the chosen readout mode (HS6). */
    suspend fun setStartCardMode(mode: StartCardModeDomain)

    /** The whole finished-session history, newest first, paged. */
    fun pagedRecent(): Flow<PagingData<RecentSessionDomain>>

    fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItemDomain>>

    /** Start-CTA conflict: same training resumes silently, a different one needs a choice. */
    suspend fun resolveStartConflict(
        requestedTrainingUuid: String,
    ): StartSessionConflict

    /** Look up a template name (used by the conflict modal label). */
    suspend fun getTrainingName(trainingUuid: String): String?

    suspend fun deleteSession(sessionUuid: String)
}
