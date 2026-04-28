// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel

internal interface PastSessionInteractor {

    suspend fun getSessionDetail(sessionUuid: String): SessionDetailDataModel?

    suspend fun updateSet(
        performedExerciseUuid: String,
        position: Int,
        set: SetsDataModel,
    )

    suspend fun deleteSession(sessionUuid: String)
}
