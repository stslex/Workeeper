// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel

/**
 * The disclosure automaton (v3 §7) — which exercise cards are expanded.
 *
 * This is deliberately ONE function with the whole transition table in it, rather than the
 * conditionals it replaces (which were spread across `ClickHandler.processExerciseHeaderClick`,
 * `StateStatusMapper.recomputeOnly` and `LiveWorkoutMapper`'s load-time seed). A reviewer must
 * be able to read the rules against §7 in one place and diff them.
 *
 * ### Transition table
 *
 * Evaluated per exercise, **top rule wins**. `prev` is the previous expanded set.
 *
 * | # | Condition | Expanded |
 * |---|---|---|
 * | 1 | skipped | `false` |
 * | 2 | manually collapsed | `false` |
 * | 3 | manually expanded | `true` |
 * | 4 | completed | `uuid in prev` when muted, else `false` |
 * | 5 | has progress | `true` |
 * | 6 | otherwise | `uuid == autoSlot` |
 *
 * Against §7, rule by rule:
 *
 * - **1** is not in §7's table; a skipped card has nothing to disclose.
 * - **2** and **3** are "manually expanded → sticky, holds for the screen session". Sticky
 *   cuts both ways: an explicit collapse is as durable as an explicit expand.
 * - **3** is also the last row of §7's table — manual expansion is the only way a completed
 *   card's add/remove-set buttons become reachable.
 * - **4** is "completed → collapses automatically", with "muted" meaning
 *   [DisclosureIntent.hasManualAction]: "after the first manual action the auto rule stops
 *   collapsing anything until the screen is left".
 * - **5** is "has progress → always expanded".
 * - **6** is "no progress → exactly one, the first by position among unfinished".
 *
 * `autoSlot` is the first exercise **in position order** that is not skipped, not completed,
 * has no progress, and has not been manually collapsed. Exactly one card can hold it, which is
 * what makes rule 6 a single-selection rule.
 *
 * ### Why manual and auto cannot fight
 *
 * Rules 2 and 3 sit above every automatic rule, so **auto never overrides manual**. Rule 4 is
 * the only rule that ever *removes* expansion, and [DisclosureIntent.hasManualAction] mutes it
 * globally — so **manual action mutes auto-collapse, not the reverse**. There is no ordering in
 * which the two can disagree, which is why this is a table and not a negotiation.
 *
 * Rule 4 reads `prev` rather than returning a constant because "stops collapsing" is a
 * statement about a transition, not about a state: a card that was open when it completed
 * stays open; a card that was already closed does not spring open.
 */
internal object DisclosureAutomaton {

    /**
     * The user's explicit disclosure intent for the screen session.
     *
     * All three fields are inputs owned by `Store.State` and reset when the screen is left —
     * the Store is scoped to the `NavBackStackEntry`, so a back-stack pop clears them while a
     * configuration change does not. That is exactly §7's "sticky for the screen session".
     */
    data class DisclosureIntent(
        /** Cards the user explicitly opened. Additive and sticky. */
        val expanded: Set<String> = emptySet(),
        /** Cards the user explicitly closed. Beats every automatic rule. */
        val collapsed: Set<String> = emptySet(),
        /**
         * Set by the first manual expand/collapse and never cleared for the screen session.
         * Mutes rule 4 so the automaton stops collapsing anything the user did not ask it to.
         */
        val hasManualAction: Boolean = false,
    )

    /**
     * Resolves the expanded set. Pure: same inputs, same output, no I/O and no clock.
     *
     * [exercises] must be in display order — rule 6 selects by position, so an unsorted list
     * would silently pick a different card.
     */
    fun resolve(
        exercises: List<LiveExerciseUiModel>,
        intent: DisclosureIntent,
        previouslyExpanded: Set<String>,
    ): Set<String> {
        val autoSlot = exercises.firstOrNull { exercise ->
            exercise.status != ExerciseStatusUiModel.SKIPPED &&
                exercise.status != ExerciseStatusUiModel.DONE &&
                !exercise.hasProgress &&
                exercise.performedExerciseUuid !in intent.collapsed
        }?.performedExerciseUuid

        return exercises.mapNotNullTo(mutableSetOf()) { exercise ->
            val uuid = exercise.performedExerciseUuid
            val isExpanded = when {
                exercise.status == ExerciseStatusUiModel.SKIPPED -> false
                uuid in intent.collapsed -> false
                uuid in intent.expanded -> true
                exercise.status == ExerciseStatusUiModel.DONE ->
                    intent.hasManualAction && uuid in previouslyExpanded

                exercise.hasProgress -> true
                else -> uuid == autoSlot
            }
            uuid.takeIf { isExpanded }
        }
    }

    /**
     * "Has progress" means started but not finished: at least one visible row marked done, and
     * at least one still not.
     *
     * This reads `visibleSets`, **not** `performedSets`. `performedSets` holds only rows that
     * were persisted, and a row is persisted only once it is marked done — so
     * `performedSets.all { it.isDone }` is vacuously true whenever it is non-empty, and using
     * it here would make this predicate permanently false. `visibleSets` is the full row list
     * the user actually sees, which is the same list the mockup's `hasProgress` reads.
     */
    private val LiveExerciseUiModel.hasProgress: Boolean
        get() = visibleSets.any { it.isDone } && !visibleSets.all { it.isDone }
}
