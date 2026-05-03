// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.stats

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.session.BestSessionVolumeRow
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class StatsRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : StatsRepository {

    override suspend fun getBestSessionVolumes(
        sinceMillis: Long,
        limit: Int,
    ): List<BestSessionVolumeDataModel> = withContext(ioDispatcher) {
        sessionDao
            .getBestSessionVolumes(sinceMillis = sinceMillis, limit = limit)
            .map { it.toData() }
    }

    private fun BestSessionVolumeRow.toData(): BestSessionVolumeDataModel = BestSessionVolumeDataModel(
        sessionUuid = sessionUuid.toString(),
        trainingUuid = trainingUuid.toString(),
        finishedAt = finishedAt,
        volume = volume,
    )
}
