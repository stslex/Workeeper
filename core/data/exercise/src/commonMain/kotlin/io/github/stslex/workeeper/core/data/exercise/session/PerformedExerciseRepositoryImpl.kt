// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.toData
import io.github.stslex.workeeper.core.data.exercise.session.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PerformedExerciseRepositoryImpl @Inject internal constructor(
    private val dao: PerformedExerciseDao,
    private val transition: DbTransitionRunner,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PerformedExerciseRepository {

    override suspend fun getBySession(
        sessionUuid: String,
    ): List<PerformedExerciseDataModel> = withContext(ioDispatcher) {
        dao.getBySession(Uuid.parse(sessionUuid)).map { it.toData() }
    }

    override suspend fun insert(rows: List<PerformedExerciseDataModel>) = transition.mutate {
        dao.insert(rows.map { it.toEntity() })
    }

    override suspend fun setSkipped(uuid: String, skipped: Boolean) = transition.mutate {
        dao.setSkipped(Uuid.parse(uuid), skipped)
    }

    override suspend fun insertForSession(
        sessionUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    ) {
        if (exerciseUuids.isEmpty()) return
        transition.mutate {
            dao.insert(
                exerciseUuids.map { (exerciseUuid, position) ->
                    PerformedExerciseEntity(
                        sessionUuid = Uuid.parse(sessionUuid),
                        exerciseUuid = Uuid.parse(exerciseUuid),
                        position = position,
                        skipped = false,
                    )
                },
            )
        }
    }
}
