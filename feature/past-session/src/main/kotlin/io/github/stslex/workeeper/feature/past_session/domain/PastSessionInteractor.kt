// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import kotlinx.coroutines.flow.Flow

internal interface PastSessionInteractor {

    /**
     * Combined session-detail + per-exercise PR map. Detail is fetched once at subscription;
     * the PR map is observed reactively so badges refresh after edits / new finished sessions
     * land on other screens. Returns null only when the session itself is missing.
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
        val prMap: Map<String, PersonalRecordDataModel?>,
    )
}
