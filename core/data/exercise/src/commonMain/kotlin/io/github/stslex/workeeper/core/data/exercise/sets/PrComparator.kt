// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.sets

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel

/**
 * In-memory arm of the PR rule for sets in a not-yet-finished session, invisible to SQL.
 * Mirrors `SessionDao`'s `PR_ELIGIBILITY` / `PR_ORDER`; parity is pinned by `PrRuleParityTest`.
 */
object PrComparator {

    /** True if [candidate] takes the record from [baseline]; null [baseline] = no PR yet. */
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
     * Primitive-baseline overload; [hasBaseline] carries the bit the nullable values cannot.
     * A null [baselineWeight] with [hasBaseline] is unreachable — coerced to 0.0 for totality.
     */
    fun beats(
        candidate: PlanSetDataModel,
        baselineWeight: Double?,
        baselineReps: Int?,
        type: ExerciseTypeDataModel,
        hasBaseline: Boolean,
    ): Boolean {
        if (!candidate.isEligible(type)) return false
        if (!hasBaseline) return true
        return when (type) {
            ExerciseTypeDataModel.WEIGHTED -> beatsWeighted(candidate, baselineWeight, baselineReps)
            ExerciseTypeDataModel.WEIGHTLESS -> candidate.reps > (baselineReps ?: 0)
        }
    }

    /**
     * The record-holding set among [sets] for [type], or null when none is eligible.
     * GUARD: [sets] must be ordered by position ascending — ties go to the earlier entry.
     */
    fun bestOf(
        sets: List<PlanSetDataModel>,
        type: ExerciseTypeDataModel,
    ): PlanSetDataModel? = sets
        .filter { it.isEligible(type) }
        .maxWithOrNull(comparatorFor(type))

    /** In-memory half of `SessionDao.PR_ELIGIBILITY`; the session-state clauses do not apply. */
    private fun PlanSetDataModel.isEligible(type: ExerciseTypeDataModel): Boolean =
        reps > 0 && (type == ExerciseTypeDataModel.WEIGHTLESS || weight != null)

    private fun beatsWeighted(
        candidate: PlanSetDataModel,
        baselineWeight: Double?,
        baselineReps: Int?,
    ): Boolean {
        val candidateWeight = candidate.weight ?: return false
        val resolvedBaselineWeight = baselineWeight ?: 0.0
        return when {
            candidateWeight > resolvedBaselineWeight -> true
            candidateWeight < resolvedBaselineWeight -> false
            else -> candidate.reps > (baselineReps ?: 0)
        }
    }

    private fun comparatorFor(
        type: ExerciseTypeDataModel,
    ): Comparator<PlanSetDataModel> = when (type) {
        ExerciseTypeDataModel.WEIGHTED -> WEIGHTED_COMPARATOR
        ExerciseTypeDataModel.WEIGHTLESS -> WEIGHTLESS_COMPARATOR
    }

    private val WEIGHTED_COMPARATOR: Comparator<PlanSetDataModel> = compareBy(
        // Eligibility already dropped the weight-null rows, so the elvis is unreachable.
        { it.weight ?: 0.0 },
        { it.reps },
    )

    /** Weight drops out entirely, mirroring the `CASE WHEN e.type = 'WEIGHTED'` in SQL. */
    private val WEIGHTLESS_COMPARATOR: Comparator<PlanSetDataModel> =
        compareBy { it.reps }
}
