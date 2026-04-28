// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel

interface PerformedExerciseRepository {

    suspend fun getBySession(sessionUuid: String): List<PerformedExerciseDataModel>

    suspend fun insert(rows: List<PerformedExerciseDataModel>)

    suspend fun setSkipped(uuid: String, skipped: Boolean)

    /**
     * Convenience seeder for Live workout: creates one performed_exercise row per
     * `(exerciseUuid, position)` pair under [sessionUuid], all with `skipped = false`.
     * The caller is responsible for ordering the pairs in render order.
     */
    suspend fun insertForSession(
        sessionUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    )
}
