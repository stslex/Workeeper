// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.stats

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.session.BestSessionVolumeRow
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class StatsRepositoryImpl @Inject internal constructor(
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
