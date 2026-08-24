// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel

interface TrainingExerciseRepository {

    suspend fun getPlan(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>?

    /**
     * Plans for [exerciseUuids] within [trainingUuid]. The returned map encodes **three**
     * distinct states, and callers must not collapse them:
     *
     * - **key absent** — no `training_exercise_table` row, so the exercise is **not
     *   plan-attached**: a one-off.
     * - **key present, value `null`** — attached with `plan_sets IS NULL`. Legacy or never
     *   set, and eligible for the `last_adhoc_sets` read-time fallback.
     * - **key present, value a list** — attached with a plan. An **empty** list means the
     *   user deliberately cleared it and must NOT get the fallback.
     *
     * The map is built by `associate` over the rows SQL actually returned, so a missing
     * `(training, exercise)` pair is an absent key rather than a null value. **Key presence
     * is therefore the plan-attached flag** (v3 §6.2) — the encoding is the existence of the
     * row, and there is no column and no migration behind it.
     *
     * Because Kotlin's `map[k]` returns `null` for both "absent" and "present with null
     * value", `get`-and-null-check alone cannot distinguish the first two rows of that table.
     * Use `containsKey` when the distinction matters. Pinned by
     * `TrainingExerciseRepositoryImplDbTest`.
     */
    suspend fun getPlans(
        trainingUuid: String,
        exerciseUuids: List<String>,
    ): Map<String, List<PlanSetDataModel>?>

    suspend fun setPlan(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )

    /**
     * The one-off toggle's write half (v3 §6.2). Attachment IS the row: [attachExercise]
     * inserts a plan row at the training's next position with [planSets]; [detachExercise]
     * deletes the pair's row. Both idempotent at the caller's level of care — attach when a
     * row already exists is the caller's bug (key presence should be read first via
     * [getPlans]).
     */
    suspend fun attachExercise(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )

    /** See [attachExercise]. */
    suspend fun detachExercise(trainingUuid: String, exerciseUuid: String)

    /**
     * Returns the (exerciseUuid, position, plan_sets) tuples for a training, ordered by
     * position. Lets callers join with the exercise table without owning a Dao reference
     * directly.
     */
    suspend fun getRowsForTraining(trainingUuid: String): List<TrainingExerciseRow>

    data class TrainingExerciseRow(
        val exerciseUuid: String,
        val position: Int,
        val planSets: List<PlanSetDataModel>?,
    )
}
