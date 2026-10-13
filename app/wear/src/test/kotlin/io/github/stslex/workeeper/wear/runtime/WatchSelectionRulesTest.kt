// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.done
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.exerciseA
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.exerciseB
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.laterExercise
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.open
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** wear-phase-2-exercise-list.md §7.2: every reconciliation rule and both user selections. */
internal class WatchSelectionRulesTest {

    private val ruleR = WatchSelection(exerciseA, 0)

    @Test
    fun `rule 1, no selection or an outline of another session, selects rule R`() {
        val outline = outline(exercise(exerciseA, open(0)), exercise(exerciseB, open(0)))
        val onB = WatchSelection(exerciseB, 0)

        assertEquals(ruleR, reconcileSelection(null, outline))
        assertEquals(ruleR, onB.reconciledWith(snapshot(outline), ReducerTestFixtures.sessionB))
        assertEquals(onB, onB.reconciledWith(snapshot(outline), ReducerTestFixtures.sessionA))
    }

    @Test
    fun `rule 2, a selected exercise gone, skipped or finished, selects rule R`() {
        val onB = WatchSelection(exerciseB, 0)
        listOf(
            "gone" to outline(exercise(exerciseA, open(0))),
            "skipped" to outline(exercise(exerciseA, open(0)), exercise(exerciseB, open(0), skipped = true)),
            "finished" to outline(exercise(exerciseA, open(0)), exercise(exerciseB, done(0))),
        ).forEach { (case, outline) -> assertEquals(ruleR, reconcileSelection(onB, outline), case) }
    }

    @Test
    fun `rule 3, an open or blocked selected position, is kept`() {
        val blocked = OutlineSet.Blocked(1, SetTypeWire.WORK, NumericField.REPS)
        val outline = outline(exercise(exerciseA, open(0)), exercise(exerciseB, open(0), blocked))

        assertEquals(WatchSelection(exerciseB, 0), reconcileSelection(WatchSelection(exerciseB, 0), outline))
        assertEquals(WatchSelection(exerciseB, 1), reconcileSelection(WatchSelection(exerciseB, 1), outline))
    }

    @Test
    fun `rule 4, a set the phone completed, moves to the first incomplete set of the same exercise`() {
        val completedOnPhone = outline(exercise(exerciseA, open(0)), exercise(exerciseB, done(0), done(1), open(2)))
        val positionGone = outline(exercise(exerciseA, open(0)), exercise(exerciseB, open(0)))
        val setsAdded = outline(exercise(exerciseA, open(0)), exercise(exerciseB, open(0), open(1)))
        val stillNoSets = outline(exercise(exerciseA, open(0)), exercise(exerciseB))

        assertEquals(WatchSelection(exerciseB, 2), reconcileSelection(WatchSelection(exerciseB, 0), completedOnPhone))
        assertEquals(WatchSelection(exerciseB, 0), reconcileSelection(WatchSelection(exerciseB, 5), positionGone))
        assertEquals(WatchSelection(exerciseB, 0), reconcileSelection(WatchSelection(exerciseB, null), setsAdded))
        assertEquals(WatchSelection(exerciseB, null), reconcileSelection(WatchSelection(exerciseB, null), stillNoSets))
    }

    @Test
    fun `PayloadTooLarge keeps the selection and its session, NoSession clears both`() {
        val onB = WatchSelection(exerciseB, 0)
        val tooLarge = SnapshotData(
            ReducerTestFixtures.epoch,
            SnapshotPayload.PayloadTooLarge(ReducerTestFixtures.sessionB, 4),
        )
        val noSession = ReducerTestFixtures.noSession()

        assertEquals(onB, onB.reconciledWith(tooLarge, ReducerTestFixtures.sessionA))
        assertEquals(ReducerTestFixtures.sessionA, ReducerTestFixtures.sessionA.selectionSessionAfter(tooLarge))
        assertNull(onB.reconciledWith(noSession, ReducerTestFixtures.sessionA))
        assertNull(ReducerTestFixtures.sessionA.selectionSessionAfter(noSession))
        assertEquals(
            ReducerTestFixtures.sessionA,
            ReducerTestFixtures.sessionB.selectionSessionAfter(snapshot(outline(exercise(exerciseA, open(0))))),
        )
    }

    @Test
    fun `opening an exercise selects its first incomplete set, only for an open or set-less exercise`() {
        val blocked = OutlineSet.Blocked(1, SetTypeWire.WORK, NumericField.WEIGHT)
        val outline = outline(
            exercise(exerciseA, done(0), blocked, open(2)),
            exercise(exerciseB),
            exercise(laterExercise, done(0)),
            exercise(ReducerTestFixtures.id(9), open(0), skipped = true),
        )

        assertEquals(WatchSelection(exerciseA, 1), outline.exerciseSelection(exerciseA))
        assertEquals(WatchSelection(exerciseB, null), outline.exerciseSelection(exerciseB))
        assertNull(outline.exerciseSelection(laterExercise), "finished")
        assertNull(outline.exerciseSelection(ReducerTestFixtures.id(9)), "skipped")
        assertNull(outline.exerciseSelection(ReducerTestFixtures.id(10)), "unknown")
    }

    @Test
    fun `picking a set selects an open or blocked set of an exercise still open`() {
        val blocked = OutlineSet.Blocked(1, SetTypeWire.WORK, NumericField.WEIGHT)
        val outline = outline(
            exercise(exerciseA, done(0), blocked, open(2)),
            exercise(exerciseB, open(0), skipped = true),
        )

        assertEquals(WatchSelection(exerciseA, 1), outline.setSelection(exerciseA, 1))
        assertEquals(WatchSelection(exerciseA, 2), outline.setSelection(exerciseA, 2))
        assertNull(outline.setSelection(exerciseA, 0), "done")
        assertNull(outline.setSelection(exerciseA, 3), "unknown position")
        assertNull(outline.setSelection(exerciseB, 0), "skipped exercise")
    }

    private fun outline(vararg exercises: OutlineExercise): SnapshotPayload.Active =
        (ReducerTestFixtures.active().payload as SnapshotPayload.Active).copy(exercises = exercises.toList())

    private fun snapshot(outline: SnapshotPayload.Active) = ReducerTestFixtures.active().copy(payload = outline)

    private fun exercise(uuid: CanonicalUuid, vararg sets: OutlineSet, skipped: Boolean = false) =
        ReducerTestFixtures.outlineExercise(uuid, sets.toList(), skipped)
}
