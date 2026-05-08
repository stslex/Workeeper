// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.feature.past_session.domain.model.DetailWithPrs
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import kotlinx.coroutines.flow.Flow

internal interface PastSessionInteractor {

    /**
     * Combined session-detail + PR-holder set UUIDs. Detail is re-fetched on every PR
     * re-emission so optimistic edits made through the input handler don't get clobbered
     * by stale captured detail. The shape is `Set<String>` rather than a full PR map
     * because the consumer only needs set-uuid equality for badge rendering.
     * Returns null only when the session itself is missing.
     */
    fun observeDetailWithPrs(sessionUuid: String): Flow<DetailWithPrs?>

    suspend fun updateSet(
        performedExerciseUuid: String,
        set: SetDomain,
    )

    /**
     * Persists a positional permutation of the sets attached to [performedExerciseUuid].
     * [orderedSetUuids] lists the new order; position 0 is the first entry. (v2.4 5.7.)
     */
    suspend fun reorderSets(performedExerciseUuid: String, orderedSetUuids: List<String>)

    suspend fun deleteSession(sessionUuid: String)
}
