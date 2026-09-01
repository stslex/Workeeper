// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/** Phase-1 watch-write validation. Phone-only values remain intentionally outside this policy. */
object CommandValidation {

    fun validate(body: CompleteCurrentSetBody): CompleteCommandOutcome.InvalidValues? {
        if (body.reps < 1) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.REPS,
                reason = InvalidValueReason.BELOW_MINIMUM,
            )
        }
        if (body.reps > WearProtocol.MAX_WEAR_REPS) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.REPS,
                reason = InvalidValueReason.ABOVE_MAXIMUM,
            )
        }

        val weight = body.weightHundredthsKg
        if (body.exerciseType == ExerciseTypeWire.WEIGHTLESS && weight != null) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.WEIGHT,
                reason = InvalidValueReason.MUST_BE_NULL_FOR_WEIGHTLESS,
            )
        }
        if (weight != null && weight < 0) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.WEIGHT,
                reason = InvalidValueReason.BELOW_MINIMUM,
            )
        }
        if (weight != null && weight > WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.WEIGHT,
                reason = InvalidValueReason.ABOVE_MAXIMUM,
            )
        }
        return null
    }
}
