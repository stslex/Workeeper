// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.DisclosureAutomaton.DisclosureIntent
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * One case per row of the §7 transition table, plus the two interaction rules the table's
 * ordering encodes (manual beats auto; manual action mutes auto-collapse).
 *
 * These are written against the spec text rather than against the implementation, so a
 * reviewer can read the test names beside §7 and check the correspondence directly.
 */
internal class DisclosureAutomatonTest {

    // --- Rule 5: has progress -> always expanded --------------------------------------

    @Test
    fun `an exercise with progress is expanded alongside the auto slot`() {
        // Rules 5 and 6 are independent: "a" is open because it has progress, and "b" is open
        // because it is the first unfinished card WITHOUT progress, which is what the slot
        // selects. An in-progress card never consumes the slot.
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.CURRENT, done = 1, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(setOf("a", "b"), resolve(exercises))
    }

    @Test
    fun `an exercise with progress is expanded when nothing else can take the slot`() {
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.CURRENT, done = 1, total = 3),
            exercise("b", ExerciseStatusUiModel.DONE, done = 3, total = 3),
        )

        assertEquals(setOf("a"), resolve(exercises))
    }

    @Test
    fun `every exercise with progress is expanded, not just one`() {
        // Rule 5 is unconditional — it is rule 6 that is a single-selection rule.
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.CURRENT, done = 1, total = 3),
            exercise("b", ExerciseStatusUiModel.CURRENT, done = 2, total = 3),
        )

        assertEquals(setOf("a", "b"), resolve(exercises))
    }

    // --- Rule 6: no progress -> exactly one, the first by position ---------------------

    @Test
    fun `with no progress anywhere exactly the first unfinished exercise is expanded`() {
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
            exercise("c", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(setOf("a"), resolve(exercises))
    }

    @Test
    fun `the auto slot is chosen by position, not by list identity`() {
        // Guards the "must be in display order" precondition: a completed first entry hands
        // the slot to the next one down rather than leaving nothing expanded.
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.DONE, done = 3, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
            exercise("c", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(setOf("b"), resolve(exercises))
    }

    @Test
    fun `a skipped exercise never takes the auto slot and is never expanded`() {
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.SKIPPED, done = 0, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(setOf("b"), resolve(exercises))
    }

    // --- Rule 4: completed -> collapses automatically ----------------------------------

    @Test
    fun `a completed exercise collapses automatically before any manual action`() {
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.DONE, done = 3, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(
            setOf("b"),
            resolve(exercises, previouslyExpanded = setOf("a", "b")),
        )
    }

    // --- Rule 3: manual expansion is sticky -------------------------------------------

    @Test
    fun `a manually expanded completed exercise stays expanded`() {
        // Otherwise its add/remove-set buttons are unreachable — the §7 note on the last row.
        val exercises = listOf(exercise("a", ExerciseStatusUiModel.DONE, done = 3, total = 3))

        assertEquals(
            setOf("a"),
            resolve(
                exercises,
                intent = DisclosureIntent(expanded = setOf("a"), hasManualAction = true),
            ),
        )
    }

    // --- Rule 2: manual collapse beats every automatic rule ---------------------------

    @Test
    fun `a manually collapsed exercise stays collapsed even with progress`() {
        val exercises = listOf(exercise("a", ExerciseStatusUiModel.CURRENT, done = 1, total = 3))

        assertEquals(
            emptySet(),
            resolve(
                exercises,
                intent = DisclosureIntent(collapsed = setOf("a"), hasManualAction = true),
            ),
        )
    }

    @Test
    fun `a manually collapsed exercise hands the auto slot to the next one`() {
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
            exercise("b", ExerciseStatusUiModel.PENDING, done = 0, total = 3),
        )

        assertEquals(
            setOf("b"),
            resolve(
                exercises,
                intent = DisclosureIntent(collapsed = setOf("a"), hasManualAction = true),
            ),
        )
    }

    // --- The mute: after the first manual action, auto stops collapsing ANYTHING -------

    @Test
    fun `after a manual action a completing exercise is no longer auto-collapsed`() {
        // The whole point of the mute. "a" was open when it completed, and the user has
        // already taken a manual action elsewhere, so it must stay open.
        val exercises = listOf(
            exercise("a", ExerciseStatusUiModel.DONE, done = 3, total = 3),
            exercise("b", ExerciseStatusUiModel.CURRENT, done = 1, total = 3),
        )

        assertEquals(
            setOf("a", "b"),
            resolve(
                exercises,
                intent = DisclosureIntent(expanded = setOf("b"), hasManualAction = true),
                previouslyExpanded = setOf("a", "b"),
            ),
        )
    }

    @Test
    fun `the mute does not spring open a completed exercise that was already closed`() {
        // "stops collapsing" is a statement about a transition, not a state — a card that was
        // shut before the mute must not open by itself afterwards.
        val exercises = listOf(exercise("a", ExerciseStatusUiModel.DONE, done = 3, total = 3))

        assertEquals(
            emptySet(),
            resolve(
                exercises,
                intent = DisclosureIntent(hasManualAction = true),
                previouslyExpanded = emptySet(),
            ),
        )
    }

    @Test
    fun `auto never overrides manual in either direction`() {
        // Both manual rules sit above every automatic rule, so a single resolve settles it.
        val exercises = listOf(
            // would be auto-expanded by rule 5, manually collapsed
            exercise("a", ExerciseStatusUiModel.CURRENT, done = 1, total = 3),
            // would be auto-collapsed by rule 4, manually expanded
            exercise("b", ExerciseStatusUiModel.DONE, done = 3, total = 3),
        )

        assertEquals(
            setOf("b"),
            resolve(
                exercises,
                intent = DisclosureIntent(
                    expanded = setOf("b"),
                    collapsed = setOf("a"),
                    hasManualAction = true,
                ),
            ),
        )
    }

    @Test
    fun `an empty session expands nothing`() {
        assertEquals(emptySet(), resolve(emptyList()))
    }
}

private fun resolve(
    exercises: List<LiveExerciseUiModel>,
    intent: DisclosureIntent = DisclosureIntent(),
    previouslyExpanded: Set<String> = emptySet(),
): Set<String> = DisclosureAutomaton.resolve(exercises, intent, previouslyExpanded)

private fun exercise(
    uuid: String,
    status: ExerciseStatusUiModel,
    done: Int,
    total: Int,
): LiveExerciseUiModel {
    val sets = (0 until total).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = position < done,
        )
    }
    return LiveExerciseUiModel(
        performedExerciseUuid = uuid,
        exerciseUuid = "ex-$uuid",
        exerciseName = "Exercise $uuid",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        statusLabel = "",
        planSets = persistentListOf(),
        performedSets = sets.filter { it.isDone }.toImmutableList(),
        visibleSets = sets.toImmutableList(),
    )
}
