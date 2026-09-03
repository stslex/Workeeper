// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType

@Suppress("TooManyFunctions")
interface SetRepository {

    suspend fun getByPerformedExercise(performedExerciseUuid: String): List<SetsDataModel>

    suspend fun getByPerformedExercises(performedExerciseUuids: List<String>): Map<String, List<SetsDataModel>>

    @Deprecated(
        message = "v5 plan-first model: prev-set hint comes from training_exercise.plan_sets" +
            " or exercise.last_adhoc_sets, not from history. Kept temporarily until existing" +
            " call sites migrate.",
    )
    suspend fun getLastFinishedSet(exerciseUuid: String): SetsDataModel?

    suspend fun insert(performedExerciseUuid: String, set: SetsDataModel)

    suspend fun update(performedExerciseUuid: String, set: SetsDataModel)

    /** Atomically rewrites the sets of [performedExerciseUuid] into [orderedSetUuids] order. */
    suspend fun reorderSets(performedExerciseUuid: String, orderedSetUuids: List<String>)

    suspend fun delete(uuid: String)

    /** Insert-or-update the set at `(performedExerciseUuid, position)`, preserving its uuid. */
    suspend fun upsert(
        performedExerciseUuid: String,
        position: Int,
        weight: Double?,
        reps: Int,
        type: SetsDataType,
    )

    suspend fun deleteByPerformedAndPosition(performedExerciseUuid: String, position: Int)

    suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String)

    suspend fun hasAnyForPerformed(performedExerciseUuid: String): Boolean

    suspend fun countByPerformedExercise(performedExerciseUuid: String): Int
}
