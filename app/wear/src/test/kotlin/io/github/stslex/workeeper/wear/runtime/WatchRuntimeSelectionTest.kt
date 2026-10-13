// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.wear.cache.CacheFraming
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.done
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.exerciseA
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.exerciseB
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures.open
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** wear-phase-2-exercise-list.md §7.2 to §7.5: the watch's own selection, in the runtime owner. */
internal class WatchRuntimeSelectionTest {

    @Test
    fun `selecting an exercise or a set moves the controller and the published selection`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        assertEquals(WatchSelection(exerciseA, 0), env.owner.snapshot.value.selection, "rule R")

        assertEquals(WatchActionResult.Updated, env.owner.onAction(ControllerAction.SelectExercise(exerciseB)))
        assertEquals(WatchSelection(exerciseB, 0), env.owner.snapshot.value.selection)
        assertEquals(BENCH, env.owner.surface.value.exerciseName)
        assertEquals(1, env.owner.surface.value.setOrdinal)

        assertEquals(WatchActionResult.Updated, env.owner.onAction(ControllerAction.SelectSet(exerciseB, 1)))
        assertEquals(WatchSelection(exerciseB, 1), env.owner.snapshot.value.selection)
        assertEquals(2, env.owner.surface.value.setOrdinal)
        assertEquals(BENCH_SECOND_REPS, env.owner.surface.value.reps)
        assertTrue(env.owner.surface.value.completeEnabled)

        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SelectSet(exerciseB, 9)))
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SelectExercise(UNKNOWN)))
        assertEquals(WatchSelection(exerciseB, 1), env.owner.snapshot.value.selection)
    }

    @Test
    fun `a changed selection drops the draft`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        env.owner.onAction(ControllerAction.SetReps(12))
        assertTrue(env.owner.surface.value.hasUnsubmittedDraft)

        env.owner.onAction(ControllerAction.SelectSet(exerciseA, 1))
        env.owner.onAction(ControllerAction.SelectSet(exerciseA, 0))

        assertEquals(WatchSelection(exerciseA, 0), env.owner.snapshot.value.selection)
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
        assertEquals(ReducerTestFixtures.open(0).reps, env.owner.surface.value.reps)
    }

    @Test
    fun `a set the phone completed moves the selection to the next set of the same exercise`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        env.owner.onAction(ControllerAction.SelectExercise(exerciseB))

        env.accept(outline(revision = 2, bench = listOf(done(0), benchSecond())))

        assertEquals(WatchSelection(exerciseB, 1), env.owner.snapshot.value.selection)
        assertEquals(BENCH, env.owner.surface.value.exerciseName)
        assertEquals(2, env.owner.surface.value.setOrdinal)
    }

    @Test
    fun `a selected exercise skipped elsewhere falls back to rule R`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        env.owner.onAction(ControllerAction.SelectExercise(exerciseB))

        env.accept(outline(revision = 2, benchSkipped = true))

        assertEquals(WatchSelection(exerciseA, 0), env.owner.snapshot.value.selection)
    }

    @Test
    fun `PayloadTooLarge keeps the selection until an outline arrives and NoSession clears it`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        env.owner.onAction(ControllerAction.SelectSet(exerciseB, 1))

        env.accept(
            SnapshotData(ReducerTestFixtures.epoch, SnapshotPayload.PayloadTooLarge(ReducerTestFixtures.sessionA, 2)),
        )
        assertEquals(WatchSelection(exerciseB, 1), env.owner.snapshot.value.selection)
        env.accept(outline(revision = 3))
        assertEquals(WatchSelection(exerciseB, 1), env.owner.snapshot.value.selection)

        env.accept(ReducerTestFixtures.noSession())
        assertNull(env.owner.snapshot.value.selection)
    }

    @Test
    fun `skip and return send set_exercise_skipped for the exercise the outline allows`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.ReturnExercise(exerciseB)))

        val skip = assertInstanceOf(
            WatchActionResult.CommandIssued::class.java,
            env.owner.onAction(ControllerAction.SkipExercise(exerciseB)),
        )
        assertEquals(WatchCommandBody.SetExerciseSkipped(exerciseB, skipped = true), skip.fingerprint.body)
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SkipExercise(exerciseA)))

        env.accept(outline(revision = 2, leaseGeneration = 2, benchSkipped = true))
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SkipExercise(exerciseB)))
        val giveBack = assertInstanceOf(
            WatchActionResult.CommandIssued::class.java,
            env.owner.onAction(ControllerAction.ReturnExercise(exerciseB)),
        )
        assertEquals(WatchCommandBody.SetExerciseSkipped(exerciseB, skipped = false), giveBack.fingerprint.body)
    }

    @Test
    fun `with a command open a changed selection shows no in-flight state and cannot complete`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        assertInstanceOf(WatchActionResult.CommandIssued::class.java, env.owner.onAction(ControllerAction.CompleteSet))
        assertEquals(
            CompletionUnavailableReason.COMMAND_IN_FLIGHT,
            env.owner.surface.value.completionUnavailableReason,
        )

        assertEquals(WatchActionResult.Updated, env.owner.onAction(ControllerAction.SelectExercise(exerciseB)))

        val elsewhere = env.owner.surface.value
        assertEquals(WearSurfaceKind.ACTIVE, elsewhere.kind)
        assertEquals(BENCH, elsewhere.exerciseName)
        assertFalse(elsewhere.completeEnabled)
        assertNull(elsewhere.completionUnavailableReason)
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.CompleteSet))
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.SkipExercise(exerciseB)))

        env.owner.onAction(ControllerAction.SelectExercise(exerciseA))
        assertEquals(
            CompletionUnavailableReason.COMMAND_IN_FLIGHT,
            env.owner.surface.value.completionUnavailableReason,
            "the command's own target shows it again",
        )
    }

    @Test
    fun `with a retryable command a changed selection names no reason and cannot complete`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        val issued = assertInstanceOf(
            WatchActionResult.CommandIssued::class.java,
            env.owner.onAction(ControllerAction.CompleteSet),
        )
        env.owner.transportTimeout(issued.token.correlationId)
        assertEquals(WearSurfaceKind.RETRYABLE_ERROR, env.owner.surface.value.kind, "the target shows Retry")

        assertEquals(WatchActionResult.Updated, env.owner.onAction(ControllerAction.SelectExercise(exerciseB)))

        // §7.2: the command's state shows only on its target; elsewhere completion is disabled, no reason.
        val elsewhere = env.owner.surface.value
        assertEquals(WearSurfaceKind.ACTIVE, elsewhere.kind)
        assertEquals(BENCH, elsewhere.exerciseName)
        assertFalse(elsewhere.completeEnabled)
        assertNull(elsewhere.completionUnavailableReason)
        assertEquals(WatchActionResult.Rejected, env.owner.onAction(ControllerAction.CompleteSet))

        env.owner.onAction(ControllerAction.SelectExercise(exerciseA))
        assertEquals(WearSurfaceKind.RETRYABLE_ERROR, env.owner.surface.value.kind, "its own target shows Retry again")
    }

    @Test
    fun `every selection change rewrites the cached record with its payload kept`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        val before = requireNotNull(CacheFraming.decode(requireNotNull(env.storage.bytes)))
        assertEquals(WatchSelection(exerciseA, 0), before.selection, "a snapshot write carries the selection")

        env.owner.onAction(ControllerAction.SelectSet(exerciseB, 1))

        val after = requireNotNull(CacheFraming.decode(requireNotNull(env.storage.bytes)))
        assertEquals(WatchSelection(exerciseB, 1), after.selection)
        assertEquals(before.copy(selection = WatchSelection(exerciseB, 1)), after)
    }

    @Test
    fun `the selection survives process death and is reconciled against the restored outline`() {
        val env = RuntimeTestEnvironment()
        env.accept(outline())
        env.owner.onAction(ControllerAction.SelectSet(exerciseB, 1))

        val restored = env.newOwner()

        assertEquals(WatchSelection(exerciseB, 1), restored.snapshot.value.selection)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, restored.surface.value.kind)
        assertEquals(BENCH, restored.surface.value.exerciseName)
        assertEquals(2, restored.surface.value.setOrdinal)

        // A record whose selection no longer holds on its own outline is reconciled on restore (rule 4).
        env.accept(outline(revision = 2, leaseGeneration = 2, bench = listOf(done(0), benchSecond())))
        val record = requireNotNull(CacheFraming.decode(requireNotNull(env.storage.bytes)))
        env.storage.bytes = CacheFraming.encode(record.copy(selection = WatchSelection(exerciseB, 0)))

        val reconciled = env.newOwner()

        assertEquals(WatchSelection(exerciseB, 1), reconciled.snapshot.value.selection)
        val rewritten = requireNotNull(CacheFraming.decode(requireNotNull(env.storage.bytes)))
        assertEquals(WatchSelection(exerciseB, 1), rewritten.selection)
    }

    /** Squat (rule R's first) and Bench, two open sets each; Bench's second set plans other reps. */
    private fun outline(
        revision: Long = 1,
        leaseGeneration: Long = revision,
        bench: List<OutlineSet> = listOf(open(0), benchSecond()),
        benchSkipped: Boolean = false,
    ): SnapshotData {
        val source = ReducerTestFixtures.active(revision = revision, leaseGeneration = leaseGeneration)
        val exercises = listOf(
            ReducerTestFixtures.outlineExercise(exerciseA, listOf(open(0), open(1)))
                .copy(name = BoundedDisplayName.Value(SQUAT)),
            ReducerTestFixtures.outlineExercise(exerciseB, bench, benchSkipped)
                .copy(name = BoundedDisplayName.Value(BENCH)),
        )
        return source.copy(payload = (source.payload as SnapshotPayload.Active).copy(exercises = exercises))
    }

    private fun benchSecond() = open(1).copy(reps = BENCH_SECOND_REPS)

    private companion object {
        const val SQUAT = "Squat"
        const val BENCH = "Bench"
        const val BENCH_SECOND_REPS = 5
        val UNKNOWN: CanonicalUuid = ReducerTestFixtures.id(77)
    }
}
