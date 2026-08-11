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

    /**
     * The start card's readout for [mode] (home-start-card.md §3) — the mode's data or the
     * mode's own empty state, computed against the moment [nowMillis].
     */
    fun observeStartCardReadout(
        mode: StartCardModeDomain,
        nowMillis: Long,
    ): Flow<StartCardReadoutDomain>

    /**
     * The persisted readout mode (HS6) — «Неделя» while the key is absent or holds an
     * unknown value.
     */
    fun observeStartCardMode(): Flow<StartCardModeDomain>

    /** Persists the chosen readout mode (HS6). */
    suspend fun setStartCardMode(mode: StartCardModeDomain)

    /**
     * The whole finished-session history, newest first, paged.
     *
     * Was `observeRecent(limit)` at a hardcoded `HOME_RECENT_LIMIT = 10`. Ten rows is not a
     * decision anybody wrote down — it is the number that made an unpaged query cheap — and it
     * meant a user's eleventh-most-recent session had no route from Home at all.
     */
    fun pagedRecent(): Flow<PagingData<RecentSessionDomain>>

    fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItemDomain>>

    /**
     * Resolves the at-most-one-active-session invariant for the Start CTA flow. The Home
     * picker hands the chosen training uuid here; same-training conflicts silently resume,
     * different-training conflicts surface the modal, no conflict means a fresh session.
     */
    suspend fun resolveStartConflict(
        requestedTrainingUuid: String,
    ): StartSessionConflict

    /** Look up a template name (used by the conflict modal label). */
    suspend fun getTrainingName(trainingUuid: String): String?

    suspend fun deleteSession(sessionUuid: String)
}
