// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertFailsWith

/** wear-phase-2-exercise-list.md §5.2: every constructor check of the outline models. */
class WireModelsTest {

    @Test
    fun `revisions and positions are non-negative`() {
        assertFailsWith<IllegalArgumentException> { ProtocolFixtures.activePayload().copy(sessionRevision = -1) }
        assertFailsWith<IllegalArgumentException> { SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, -1) }
        assertFailsWith<IllegalArgumentException> { open(position = -1) }
        assertFailsWith<IllegalArgumentException> { done(position = -1) }
        assertFailsWith<IllegalArgumentException> { OutlineSet.Blocked(-1, SetTypeWire.WORK, NumericField.REPS) }
        ProtocolFixtures.activePayload().copy(sessionRevision = 0)
        SnapshotPayload.PayloadTooLarge(ProtocolFixtures.sessionUuid, 0)
        open(position = 0)
        done(position = 0)
        OutlineSet.Blocked(0, SetTypeWire.WORK, NumericField.REPS)
    }

    @Test
    fun `an exercise's positions are strictly ascending`() {
        assertFailsWith<IllegalArgumentException> { exercise(sets = listOf(open(position = 1), open(position = 1))) }
        assertFailsWith<IllegalArgumentException> { exercise(sets = listOf(open(position = 2), done(position = 1))) }
        exercise(sets = listOf(done(position = 0), open(position = 2), done(position = 7)))
        exercise(sets = emptyList())
    }

    @Test
    fun `the outline's performed exercises are distinct`() {
        val first = exercise(uuid = ProtocolFixtures.exerciseUuid, sets = emptyList())
        assertFailsWith<IllegalArgumentException> {
            ProtocolFixtures.activePayload(
                exercises = listOf(first, first.copy(name = BoundedDisplayName.Value("Other"))),
            )
        }
        ProtocolFixtures.activePayload(
            exercises = listOf(first, first.copy(performedExerciseUuid = ProtocolFixtures.secondExerciseUuid)),
        )
    }

    @Test
    fun `open reps and every weight stay inside the watch domain`() {
        assertFailsWith<IllegalArgumentException> { open(reps = -1) }
        assertFailsWith<IllegalArgumentException> { open(reps = WearProtocol.MAX_WEAR_REPS + 1) }
        assertFailsWith<IllegalArgumentException> { open(weight = -1) }
        assertFailsWith<IllegalArgumentException> { open(weight = WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG + 1) }
        assertFailsWith<IllegalArgumentException> { done(weight = -1) }
        assertFailsWith<IllegalArgumentException> { done(weight = WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG + 1) }
        open(reps = 0, weight = 0)
        open(reps = WearProtocol.MAX_WEAR_REPS, weight = WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG)
        open(weight = null)
        done(weight = null)
    }

    @Test
    fun `done reps are null or inside the watch domain`() {
        assertFailsWith<IllegalArgumentException> { done(reps = -1) }
        assertFailsWith<IllegalArgumentException> { done(reps = WearProtocol.MAX_WEAR_REPS + 1) }
        done(reps = null)
        done(reps = 0)
        done(reps = WearProtocol.MAX_WEAR_REPS)
    }

    @Test
    fun `a weightless exercise has no non-null weight in any set`() {
        assertFailsWith<IllegalArgumentException> {
            exercise(type = ExerciseTypeWire.WEIGHTLESS, sets = listOf(open(weight = 0)))
        }
        assertFailsWith<IllegalArgumentException> {
            exercise(type = ExerciseTypeWire.WEIGHTLESS, sets = listOf(done(weight = 100)))
        }
        exercise(
            type = ExerciseTypeWire.WEIGHTLESS,
            sets = listOf(
                done(position = 0, weight = null),
                open(position = 1, weight = null),
                OutlineSet.Blocked(2, SetTypeWire.WORK, NumericField.REPS),
            ),
        )
    }

    private fun exercise(
        uuid: CanonicalUuid = ProtocolFixtures.exerciseUuid,
        type: ExerciseTypeWire = ExerciseTypeWire.WEIGHTED,
        sets: List<OutlineSet>,
    ) = OutlineExercise(uuid, BoundedDisplayName.Value("Squat"), type, skipped = false, sets = sets)

    private fun open(position: Int = 0, reps: Int = 8, weight: Int? = 10_000) =
        OutlineSet.Open(position, SetTypeWire.WORK, reps, weight)

    private fun done(position: Int = 0, reps: Int? = 8, weight: Int? = 10_000) =
        OutlineSet.Done(position, SetTypeWire.WORK, reps, weight)
}
