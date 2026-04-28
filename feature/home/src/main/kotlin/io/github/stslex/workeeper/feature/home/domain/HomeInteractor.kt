// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import kotlinx.coroutines.flow.Flow

internal interface HomeInteractor {

    fun observeActiveSession(): Flow<SessionRepository.ActiveSessionWithStats?>

    fun observeRecent(limit: Int): Flow<List<RecentSessionDataModel>>

    fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItem>>

    /**
     * Resolves the at-most-one-active-session invariant for the Start CTA flow. The Home
     * picker hands the chosen training uuid here; same-training conflicts silently resume,
     * different-training conflicts surface the modal, no conflict means a fresh session.
     */
    suspend fun resolveStartConflict(
        requestedTrainingUuid: String,
    ): SessionConflictResolver.Resolution

    /** Look up a template name (used by the conflict modal label). */
    suspend fun getTrainingName(trainingUuid: String): String?

    suspend fun deleteSession(sessionUuid: String)
}
