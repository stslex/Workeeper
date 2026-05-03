// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
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
        position: Int,
        set: SetsDataModel,
    )

    suspend fun deleteSession(sessionUuid: String)

    data class DetailWithPrs(
        val detail: SessionDetailDataModel,
        val prSetUuids: Set<String>,
    )
}
