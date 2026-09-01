// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/** Phase-1 watch-write validation. Phone-only values remain intentionally outside this policy. */
object CommandValidation {

    fun validate(body: CompleteCurrentSetBody): CompleteCommandOutcome.InvalidValues? {
        return validate(
            reps = body.reps,
            weightHundredthsKg = body.weightHundredthsKg,
            exerciseType = body.exerciseType,
        )
    }

    fun validate(
        reps: Int,
        weightHundredthsKg: Int?,
        exerciseType: ExerciseTypeWire,
    ): CompleteCommandOutcome.InvalidValues? {
        if (reps < 1) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.REPS,
                reason = InvalidValueReason.BELOW_MINIMUM,
            )
        }
        if (reps > WearProtocol.MAX_WEAR_REPS) {
            return CompleteCommandOutcome.InvalidValues(
                field = NumericField.REPS,
                reason = InvalidValueReason.ABOVE_MAXIMUM,
            )
        }

        val weight = weightHundredthsKg
        if (exerciseType == ExerciseTypeWire.WEIGHTLESS && weight != null) {
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
