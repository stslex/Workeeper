// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.sets

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel

/**
 * Pure-function PR comparator. Mirrors the SQL `ORDER BY` in
 * `SessionDao.observePersonalRecord`; used at session finish where the comparison happens
 * against an in-memory snapshot rather than re-running the DAO query.
 *
 * Parity with the SQL path is asserted by `PrComparatorTest` against a Room in-memory DB.
 */
object PrComparator {

    /**
     * True if [candidate] strictly beats [baseline] for an exercise of [type].
     * `null` baseline means no PR exists yet — any non-zero candidate beats it.
     */
    fun beats(
        candidate: PlanSetDataModel,
        baseline: PersonalRecordDataModel?,
        type: ExerciseTypeDataModel,
    ): Boolean = beats(
        candidate = candidate,
        baselineWeight = baseline?.weight,
        baselineReps = baseline?.reps,
        type = type,
        hasBaseline = baseline != null,
    )

    /**
     * Primitive-baseline overload used by surfaces that store the snapshot as plain values
     * rather than a [PersonalRecordDataModel] (e.g. `LiveWorkoutStore.State.PrSnapshotItem`).
     * [hasBaseline] distinguishes "no PR yet" from "PR with null weight" — important for
     * weighted exercises where a null baseline weight (no PR) and a real-but-null-weighted
     * PR are different states.
     */
    fun beats(
        candidate: PlanSetDataModel,
        baselineWeight: Double?,
        baselineReps: Int?,
        type: ExerciseTypeDataModel,
        hasBaseline: Boolean,
    ): Boolean = when (type) {
        ExerciseTypeDataModel.WEIGHTED -> beatsWeighted(candidate, baselineWeight, baselineReps, hasBaseline)
        ExerciseTypeDataModel.WEIGHTLESS -> beatsWeightless(candidate, baselineReps, hasBaseline)
    }

    /**
     * Picks the best set in [sets] for an exercise of [type]. Returns null if [sets] is empty
     * or none of the candidates qualify (e.g. weighted exercise with all weight-null sets).
     * "Best" follows the same ordering as the SQL PR query.
     */
    fun bestOf(
        sets: List<PlanSetDataModel>,
        type: ExerciseTypeDataModel,
    ): PlanSetDataModel? = when (type) {
        ExerciseTypeDataModel.WEIGHTED ->
            sets
                .filter { it.weight != null }
                .maxWithOrNull(WEIGHTED_COMPARATOR)
        ExerciseTypeDataModel.WEIGHTLESS -> sets.maxByOrNull { it.reps }
    }

    private fun beatsWeighted(
        candidate: PlanSetDataModel,
        baselineWeight: Double?,
        baselineReps: Int?,
        hasBaseline: Boolean,
    ): Boolean {
        val candidateWeight = candidate.weight ?: return false
        if (!hasBaseline) return candidateWeight > 0.0 || candidate.reps > 0
        val resolvedBaselineWeight = baselineWeight ?: 0.0
        return when {
            candidateWeight > resolvedBaselineWeight -> true
            candidateWeight < resolvedBaselineWeight -> false
            else -> candidate.reps > (baselineReps ?: 0)
        }
    }

    private fun beatsWeightless(
        candidate: PlanSetDataModel,
        baselineReps: Int?,
        hasBaseline: Boolean,
    ): Boolean {
        if (!hasBaseline) return candidate.reps > 0
        return candidate.reps > (baselineReps ?: 0)
    }

    private val WEIGHTED_COMPARATOR: Comparator<PlanSetDataModel> = compareBy(
        { it.weight ?: 0.0 },
        { it.reps },
    )
}
