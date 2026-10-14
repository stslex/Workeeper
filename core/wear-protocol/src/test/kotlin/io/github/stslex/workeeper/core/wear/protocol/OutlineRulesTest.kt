// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** wear-phase-2-exercise-list.md §5.3. */
class OutlineRulesTest {

    @Test
    fun `status is skipped first then finished then no sets then open`() {
        assertEquals(OutlineStatus.SKIPPED, OutlineRules.status(exercise(1, skipped = true, sets = listOf(done(0)))))
        assertEquals(OutlineStatus.SKIPPED, OutlineRules.status(exercise(1, skipped = true, sets = emptyList())))
        assertEquals(OutlineStatus.FINISHED, OutlineRules.status(exercise(1, sets = listOf(done(0), done(2)))))
        assertEquals(OutlineStatus.NO_SETS, OutlineRules.status(exercise(1, sets = emptyList())))
        assertEquals(OutlineStatus.OPEN, OutlineRules.status(exercise(1, sets = listOf(done(0), open(1)))))
        assertEquals(OutlineStatus.OPEN, OutlineRules.status(exercise(1, sets = listOf(done(0), blocked(1)))))
        assertFalse(OutlineRules.isFinished(exercise(1, skipped = true, sets = listOf(done(0)))))
        assertFalse(OutlineRules.isFinished(exercise(1, sets = emptyList())))
        assertTrue(OutlineRules.isFinished(exercise(1, sets = listOf(done(0)))))
    }

    @Test
    fun `the first incomplete set is the open or blocked set with the lowest position`() {
        assertEquals(blocked(2), firstIncomplete(done(0), blocked(2), open(5)))
        assertEquals(open(1), firstIncomplete(done(0), open(1), blocked(2)))
        assertNull(OutlineRules.firstIncompleteSet(exercise(1, sets = listOf(done(0), done(1)))))
        assertNull(OutlineRules.firstIncompleteSet(exercise(1, sets = emptyList())))
    }

    @Test
    fun `rule R selects the first open or set-less exercise and passes over finished and skipped ones`() {
        val vectors = listOf(
            // A set-less exercise stops the search (P12).
            listOf(
                exercise(1, sets = listOf(done(0))),
                exercise(2, sets = emptyList()),
                exercise(3, sets = listOf(open(0))),
            ) to OutlineSelection(id(2), null),
            // Skipped ones are passed over, even with open sets.
            listOf(exercise(1, skipped = true, sets = listOf(open(0))), exercise(2, sets = listOf(done(0), open(1))))
                to OutlineSelection(id(2), 1),
            listOf(exercise(1, sets = listOf(done(0), blocked(1), open(2)))) to OutlineSelection(id(1), 1),
            listOf(exercise(1, skipped = true, sets = emptyList()), exercise(2, sets = emptyList()))
                to OutlineSelection(id(2), null),
            // Nothing left gives none.
            listOf(exercise(1, sets = listOf(done(0))), exercise(2, skipped = true, sets = listOf(open(0)))) to null,
            emptyList<OutlineExercise>() to null,
        )
        vectors.forEach { (exercises, expected) ->
            val selection = OutlineRules.defaultSelection(outline(exercises))
            assertEquals(expected?.performedExerciseUuid, selection?.performedExerciseUuid, "$exercises")
            assertEquals(expected?.setPosition, selection?.setPosition, "$exercises")
        }
    }

    @Test
    fun `the workout is complete when no exercise is open or set-less, an empty outline included`() {
        assertTrue(OutlineRules.isWorkoutComplete(outline(emptyList())))
        assertTrue(
            OutlineRules.isWorkoutComplete(
                outline(listOf(exercise(1, sets = listOf(done(0))), exercise(2, skipped = true, sets = emptyList()))),
            ),
        )
        assertFalse(OutlineRules.isWorkoutComplete(outline(listOf(exercise(1, sets = emptyList())))))
        assertFalse(OutlineRules.isWorkoutComplete(outline(listOf(exercise(1, sets = listOf(blocked(0)))))))
    }

    @Test
    fun `exercise progress counts finished exercises out of the ones that are not skipped`() {
        val progress = OutlineRules.exerciseProgress(
            outline(
                listOf(
                    exercise(1, sets = listOf(done(0))),
                    exercise(2, sets = listOf(open(0))),
                    exercise(3, skipped = true, sets = listOf(done(0))),
                    exercise(4, sets = emptyList()),
                    exercise(5, sets = listOf(done(0), done(1))),
                ),
            ),
        )
        assertEquals(ExerciseProgress(finished = 2, total = 4), progress)
    }

    @Test
    fun `completable needs the exercise not skipped and an open set at that position`() {
        val payload = outline(
            listOf(
                exercise(1, sets = listOf(done(0), open(1), blocked(2))),
                exercise(2, skipped = true, sets = listOf(open(0))),
            ),
        )
        assertTrue(OutlineRules.completable(payload, id(1), 1))
        assertFalse(OutlineRules.completable(payload, id(1), 0), "a done set")
        assertFalse(OutlineRules.completable(payload, id(1), 2), "a blocked set")
        assertFalse(OutlineRules.completable(payload, id(1), 3), "an unknown position")
        assertFalse(OutlineRules.completable(payload, id(2), 0), "a skipped exercise")
        assertFalse(OutlineRules.completable(payload, id(9), 0), "an unknown exercise")
        val tooLarge = SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 1)
        assertFalse(OutlineRules.completable(tooLarge, id(1), 1))
        assertFalse(OutlineRules.completable(SnapshotPayload.NoSession, id(1), 1))
    }

    @Test
    fun `skippable needs the exercise and a skip flag that differs`() {
        val payload = outline(
            listOf(
                exercise(1, sets = listOf(done(0))),
                exercise(2, skipped = true, sets = emptyList()),
            ),
        )
        assertTrue(OutlineRules.skippable(payload, id(1), skipped = true), "a finished exercise can be skipped (P7)")
        assertFalse(OutlineRules.skippable(payload, id(1), skipped = false))
        assertTrue(OutlineRules.skippable(payload, id(2), skipped = false))
        assertFalse(OutlineRules.skippable(payload, id(2), skipped = true))
        assertFalse(OutlineRules.skippable(payload, id(9), skipped = true))
        assertFalse(
            OutlineRules.skippable(SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 1), id(1), true),
        )
        assertTrue(OutlineRules.allows(payload, WatchCommandBody.SetExerciseSkipped(id(1), skipped = true)))
        assertFalse(OutlineRules.allows(payload, WatchCommandBody.SetExerciseSkipped(id(2), skipped = true)))
    }

    @Test
    fun `a selection's toString names its type only`() {
        assertEquals("OutlineSelection", OutlineSelection(id(1), 2).toString())
    }

    private fun firstIncomplete(vararg sets: OutlineSet) =
        OutlineRules.firstIncompleteSet(exercise(1, sets = sets.toList()))

    private fun outline(exercises: List<OutlineExercise>) = ProtocolFixtures.activePayload(exercises = exercises)

    private fun exercise(n: Int, skipped: Boolean = false, sets: List<OutlineSet>) =
        OutlineExercise(id(n), BoundedDisplayName.Value("Exercise $n"), ExerciseTypeWire.WEIGHTED, skipped, sets)

    private fun id(n: Int) = ProtocolFixtures.uuid("bbbbbbbb-0000-4000-8000-${n.toString().padStart(12, '0')}")

    private fun open(position: Int) = OutlineSet.Open(position, SetTypeWire.WORK, reps = 8, weightHundredthsKg = 5_000)

    private fun done(position: Int) = OutlineSet.Done(position, SetTypeWire.WORK, reps = 8, weightHundredthsKg = 5_000)

    private fun blocked(position: Int) = OutlineSet.Blocked(position, SetTypeWire.WORK, NumericField.WEIGHT)
}
