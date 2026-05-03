// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import kotlinx.coroutines.flow.Flow

internal interface HomeInteractor {

    fun observeActiveSession(): Flow<ActiveSessionWithStatsDomain?>

    fun observeRecent(limit: Int): Flow<List<RecentSessionDomain>>

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
