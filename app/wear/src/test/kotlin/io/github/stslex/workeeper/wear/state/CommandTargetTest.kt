// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** wear-phase-2-exercise-list.md §7.1 and §7.4: a command's target, and a value-free toString. */
internal class CommandTargetTest {

    @Test
    fun `a target is the command body's exercise and set, or its exercise and skip flag`() {
        val complete = WatchCommandBody.CompleteSet(
            performedExerciseUuid = ReducerTestFixtures.exerciseA,
            setPosition = 3,
            reps = 8,
            weightHundredthsKg = 10_000,
            exerciseType = ExerciseTypeWire.WEIGHTED,
            setType = SetTypeWire.WORK,
        )
        val skip = WatchCommandBody.SetExerciseSkipped(ReducerTestFixtures.exerciseB, skipped = true)

        assertEquals(CommandTarget.CompleteSet(TargetKey(ReducerTestFixtures.exerciseA, 3)), CommandTarget.of(complete))
        assertEquals(CommandTarget.SetSkipped(ReducerTestFixtures.exerciseB, true), CommandTarget.of(skip))
    }

    @Test
    fun `a target's toString names its type only`() {
        val targets = listOf(
            CommandTarget.CompleteSet(TargetKey(ReducerTestFixtures.exerciseA, 3)),
            CommandTarget.SetSkipped(ReducerTestFixtures.exerciseB, skipped = true),
        )

        assertEquals(
            listOf("CommandTarget.CompleteSet", "CommandTarget.SetSkipped"),
            targets.map(CommandTarget::toString),
        )
    }
}
