// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandValidationTest {

    @Test
    fun `reps boundaries use exact typed outcomes`() {
        assertNull(CommandValidation.validate(ProtocolFixtures.commandBody(reps = 1)))
        assertNull(CommandValidation.validate(ProtocolFixtures.commandBody(reps = 999)))
        assertInvalid(
            actual = CommandValidation.validate(ProtocolFixtures.commandBody(reps = 0)),
            field = NumericField.REPS,
            reason = InvalidValueReason.BELOW_MINIMUM,
        )
        assertInvalid(
            actual = CommandValidation.validate(ProtocolFixtures.commandBody(reps = -1)),
            field = NumericField.REPS,
            reason = InvalidValueReason.BELOW_MINIMUM,
        )
        assertInvalid(
            actual = CommandValidation.validate(ProtocolFixtures.commandBody(reps = 1_000)),
            field = NumericField.REPS,
            reason = InvalidValueReason.ABOVE_MAXIMUM,
        )
    }

    @Test
    fun `weighted boundaries accept null zero and maximum without clamping`() {
        assertNull(CommandValidation.validate(ProtocolFixtures.commandBody(weightHundredthsKg = null)))
        assertNull(CommandValidation.validate(ProtocolFixtures.commandBody(weightHundredthsKg = 0)))
        assertNull(CommandValidation.validate(ProtocolFixtures.commandBody(weightHundredthsKg = 99_999)))
        assertInvalid(
            actual = CommandValidation.validate(ProtocolFixtures.commandBody(weightHundredthsKg = -1)),
            field = NumericField.WEIGHT,
            reason = InvalidValueReason.BELOW_MINIMUM,
        )
        assertInvalid(
            actual = CommandValidation.validate(ProtocolFixtures.commandBody(weightHundredthsKg = 100_000)),
            field = NumericField.WEIGHT,
            reason = InvalidValueReason.ABOVE_MAXIMUM,
        )
    }

    @Test
    fun `weightless command rejects every non-null weight including zero`() {
        assertNull(
            CommandValidation.validate(
                ProtocolFixtures.commandBody(
                    exerciseType = ExerciseTypeWire.WEIGHTLESS,
                    weightHundredthsKg = null,
                ),
            ),
        )
        assertInvalid(
            actual = CommandValidation.validate(
                ProtocolFixtures.commandBody(
                    exerciseType = ExerciseTypeWire.WEIGHTLESS,
                    weightHundredthsKg = 0,
                ),
            ),
            field = NumericField.WEIGHT,
            reason = InvalidValueReason.MUST_BE_NULL_FOR_WEIGHTLESS,
        )
    }

    private fun assertInvalid(
        actual: CompleteCommandOutcome.InvalidValues?,
        field: NumericField,
        reason: InvalidValueReason,
    ) {
        assertEquals(CompleteCommandOutcome.InvalidValues(field, reason), actual)
    }
}
