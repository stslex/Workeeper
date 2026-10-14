// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** wear-phase-2-exercise-list.md §7.1: completion, skip and return eligibility. */
internal class WatchInteractionEligibilityTest {

    private val openSet = OutlineSelection(ReducerTestFixtures.exerciseA, 0)

    @Test
    fun `completion needs Available, no open command, no refresh, an open selected set and valid values`() {
        val ready = readyState()
        assertTrue(WatchInteractionEligibility.from(ready, openSet).completion)

        val blocked = ready.withExercises(
            ReducerTestFixtures.outlineExercise(
                ReducerTestFixtures.exerciseA,
                listOf(ReducerTestFixtures.done(0), OutlineSet.Blocked(1, SetTypeWire.WORK, NumericField.WEIGHT)),
            ),
        )
        val skipped = ready.withExercises(
            ReducerTestFixtures.outlineExercise(
                ReducerTestFixtures.exerciseA,
                listOf(ReducerTestFixtures.open(0)),
                skipped = true,
            ),
        )
        val cases = listOf(
            Case("retired authority", ready.copy(authority = LocalMutationAuthority.Retired), openSet),
            Case("an open command", ready.copy(command = openCommand()), openSet),
            Case("refresh required", ready.copy(refreshRequired = true), openSet),
            Case("no selection", ready, null),
            Case("a blocked set", blocked, OutlineSelection(ReducerTestFixtures.exerciseA, 1)),
            Case("a done set", blocked, OutlineSelection(ReducerTestFixtures.exerciseA, 0)),
            Case("an unknown position", ready, OutlineSelection(ReducerTestFixtures.exerciseA, 9)),
            Case("a skipped exercise", skipped, openSet),
            Case("invalid draft reps", ready.copy(draft = CommandDraft(0, 10_000)), openSet),
            Case("payload too large", ready.copy(display = tooLarge()), openSet),
        )
        cases.forEach { case ->
            assertFalse(WatchInteractionEligibility.from(case.state, case.selection).completion, case.name)
        }
    }

    @Test
    fun `skip and return need Available, no open command, no refresh and skippable`() {
        val ready = readyState()
        assertTrue(WatchInteractionEligibility.canSetSkipped(ready, ReducerTestFixtures.exerciseA, skipped = true))

        val finishedAndSkipped = ready.withExercises(
            ReducerTestFixtures.outlineExercise(ReducerTestFixtures.exerciseA, listOf(ReducerTestFixtures.done(0))),
            ReducerTestFixtures.outlineExercise(ReducerTestFixtures.exerciseB, emptyList(), skipped = true),
        )
        assertTrue(
            WatchInteractionEligibility.canSetSkipped(finishedAndSkipped, ReducerTestFixtures.exerciseA, true),
            "a finished exercise can be skipped (P7)",
        )
        assertTrue(WatchInteractionEligibility.canSetSkipped(finishedAndSkipped, ReducerTestFixtures.exerciseB, false))

        val refused = listOf(
            SkipCase("skip of a skipped exercise", finishedAndSkipped, ReducerTestFixtures.exerciseB, true),
            SkipCase("return of a pending exercise", ready, ReducerTestFixtures.exerciseA, false),
            SkipCase("an unknown exercise", ready, ReducerTestFixtures.id(9), true),
            SkipCase("retired authority", ready.copy(authority = LocalMutationAuthority.Retired), null, true),
            SkipCase("an open command", ready.copy(command = openCommand()), null, true),
            SkipCase("refresh required", ready.copy(refreshRequired = true), null, true),
            SkipCase("payload too large", ready.copy(display = tooLarge()), null, true),
        )
        refused.forEach { case ->
            val uuid = case.uuid ?: ReducerTestFixtures.exerciseA
            assertFalse(WatchInteractionEligibility.canSetSkipped(case.state, uuid, case.skipped), case.name)
        }
    }

    /** A fresh granted outline admitted through the reducer: `Available`, no command. */
    private fun readyState(): WatchReducerState = WatchWorkoutReducer().apply {
        val correlation = ReducerTestFixtures.id(1)
        issueHandshake(correlation, 0)
        receiveSnapshot(correlation, ReducerTestFixtures.active(), 0)
    }.state

    /** The in-flight command of a reducer that just issued one. */
    private fun openCommand(): LogicalCommand = WatchWorkoutReducer().run {
        val handshake = ReducerTestFixtures.id(2)
        issueHandshake(handshake, 0)
        receiveSnapshot(handshake, ReducerTestFixtures.active(), 0)
        issueCommand(ReducerTestFixtures.id(3), 1, ReducerTestFixtures.fingerprint())
        requireNotNull(state.command)
    }

    private fun WatchReducerState.withExercises(vararg exercises: OutlineExercise): WatchReducerState {
        val display = display as WatchDisplayState.Active
        val payload = display.snapshot.payload as SnapshotPayload.Active
        val snapshot = display.snapshot.copy(payload = payload.copy(exercises = exercises.toList()))
        return copy(display = display.copy(snapshot = snapshot))
    }

    private fun tooLarge() = WatchDisplayState.PayloadTooLarge(
        SnapshotData(ReducerTestFixtures.epoch, SnapshotPayload.PayloadTooLarge(ReducerTestFixtures.sessionA, 1)),
    )

    private data class Case(val name: String, val state: WatchReducerState, val selection: OutlineSelection?)

    private data class SkipCase(
        val name: String,
        val state: WatchReducerState,
        val uuid: CanonicalUuid?,
        val skipped: Boolean,
    )
}
