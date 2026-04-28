// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import kotlinx.coroutines.flow.Flow

internal interface HomeInteractor {

    fun observeActiveSession(): Flow<SessionRepository.ActiveSessionWithStats?>

    fun observeRecent(limit: Int): Flow<List<RecentSessionDataModel>>

    fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItem>>
}
