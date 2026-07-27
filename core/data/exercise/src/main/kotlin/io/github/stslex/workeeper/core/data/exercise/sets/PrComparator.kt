// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.sets

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel

/**
 * The in-memory arm of the PR rule, used at session finish and for the live per-set badge —
 * both compare against a snapshot rather than re-running the DAO query, because the sets in
 * question belong to a session that has not finished yet and so are invisible to SQL.
 *
 * The rule it implements is the one in `SessionDao`'s `PR_ELIGIBILITY` / `PR_ORDER`:
 *
 *  - eligible iff `reps > 0`, and the exercise is WEIGHTLESS or the set carries a weight;
 *  - ordered by weight DESC (WEIGHTED only), then reps DESC, then `finished_at` ASC, then
 *    `position` ASC.
 *
 * Two criteria collapse here rather than being dropped:
 *
 *  - **`finished_at` ASC.** Every candidate this object sees belongs to the same unfinished
 *    session, so they share a `finished_at` and the criterion cannot separate them *within*
 *    [bestOf]. Across the snapshot boundary it is what makes [beats] strict: the live session
 *    finishes after every session already recorded, so its `finished_at` is the largest, and
 *    ties therefore go to the incumbent. `>` is not a second rule — it is criterion (3)
 *    applied to a set whose timestamp is guaranteed to lose.
 *  - **`position` ASC.** [bestOf] returns the first maximal element, so a tie is resolved in
 *    favour of the earlier list entry. That is criterion (4) **only if the caller's list is
 *    ordered by position** — see the precondition on [bestOf].
 *
 * Agreement with the SQL path is asserted by `PrRuleParityTest` in `core/data/exercise`,
 * which runs one fixture through every PR site — this object, both single-exercise queries,
 * the batch query, and the repository — and requires them to name the same set.
 */
object PrComparator {

    /**
     * True if [candidate] would take the record from [baseline] for an exercise of [type].
     * A null [baseline] means no PR exists yet — any *eligible* candidate takes it.
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
     * [hasBaseline] carries the one bit the nullable values cannot: whether a PR exists at
     * all. For a WEIGHTED exercise a real PR always has a weight — eligibility excludes
     * weight-null rows — so `hasBaseline = true` with a null [baselineWeight] is a state no
     * caller can produce; it is coerced to 0.0 only to keep the function total.
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
     * Picks the record-holding set among [sets] for an exercise of [type], or null when none
     * is eligible (all weight-null for a WEIGHTED exercise, all zero-rep, or empty).
     *
     * **Precondition: [sets] is ordered by set position, ascending.** Ties are broken in
     * favour of the earlier entry, which is criterion (4) of the rule only under that
     * ordering. The sole caller satisfies it: the list originates from
     * `SetDao.getByPerformedExercises` (`ORDER BY performed_exercise_uuid, position ASC`) and
     * every state transition that rewrites it preserves position order — replacement in
     * place, append-then-`sortBy(position)`, `filterNot`, or `map`.
     */
    fun bestOf(
        sets: List<PlanSetDataModel>,
        type: ExerciseTypeDataModel,
    ): PlanSetDataModel? = sets
        .filter { it.isEligible(type) }
        .maxWithOrNull(comparatorFor(type))

    /**
     * Shared eligibility: the in-memory half of `SessionDao.PR_ELIGIBILITY`. The session-state
     * clauses have no counterpart here — these sets are in an unfinished session by
     * construction.
     */
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
        // Eligibility already dropped the weight-null rows; the elvis is unreachable and
        // exists only because the model types weight as nullable for both exercise kinds.
        { it.weight ?: 0.0 },
        { it.reps },
    )

    /** Weight drops out entirely, mirroring the `CASE WHEN e.type = 'WEIGHTED'` in SQL. */
    private val WEIGHTLESS_COMPARATOR: Comparator<PlanSetDataModel> =
        compareBy { it.reps }
}
