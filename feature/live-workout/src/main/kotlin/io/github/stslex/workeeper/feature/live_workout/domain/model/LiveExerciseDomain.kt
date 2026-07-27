// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain.model

data class LiveExerciseDomain(
    val performed: PerformedExerciseDomain,
    val exerciseType: ExerciseTypeDomain,
    val planSets: List<PlanSetDomain>?,
    val performedSets: List<SetDomain>,
    /**
     * Whether this exercise is part of the saved training template, i.e. whether a
     * `training_exercise_table` row exists for the (training, exercise) pair. `false` marks a
     * **one-off**: fully part of this session, counted in progress, but deliberately absent
     * from the plan, so the next session does not inherit it.
     *
     * ### This is not `is_adhoc`, and the two must never be conflated
     *
     * They are different axes over different subjects:
     *
     * | | `exercise_table.is_adhoc` | `isPlanAttached` |
     * |---|---|---|
     * | subject | the **exercise** | the exercise↔training **relation** |
     * | means | "created inline, not from the library" | "is in this training's plan" |
     * | lifecycle | create → graduate → delete (see `CLAUDE.md`) | set once at add time; no lifecycle |
     * | storage | a real column | **absence of a row**; no column, no migration |
     *
     * **The breaking case that separates them:** a library exercise, `is_adhoc = 0`, added
     * mid-session as a one-off today. It is not ad-hoc by any definition — it has existed in
     * the library for months — yet it is not plan-attached. Reusing `is_adhoc` for this would
     * both mislabel the exercise and hand it the ad-hoc delete lifecycle, which would erase a
     * real library entry when the session is cancelled.
     *
     * Encoding note (v3 §6.2): because absence of the row *is* the flag, it is read from key
     * presence in `TrainingExerciseRepository.getPlans` — not from plan nullability. A row
     * with `plan_sets IS NULL` is attached-with-no-plan and is a different thing entirely.
     */
    val isPlanAttached: Boolean,
)
