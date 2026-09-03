// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.feature.past_session.domain.model.DetailWithPrs
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import kotlinx.coroutines.flow.Flow

interface PastSessionInteractor {

    /**
     * Combined session-detail + PR-holder set UUIDs; null only when the session is missing.
     * Detail is re-fetched on every PR re-emission so optimistic edits are not clobbered.
     */
    fun observeDetailWithPrs(sessionUuid: String): Flow<DetailWithPrs?>

    suspend fun updateSet(
        performedExerciseUuid: String,
        set: SetDomain,
    )

    /** Persists a positional permutation of [performedExerciseUuid]'s sets; position 0 is first. */
    suspend fun reorderSets(performedExerciseUuid: String, orderedSetUuids: List<String>)

    suspend fun deleteSession(sessionUuid: String)
}
