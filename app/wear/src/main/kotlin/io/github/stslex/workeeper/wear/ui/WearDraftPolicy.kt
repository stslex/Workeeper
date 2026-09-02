// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol

internal object WearDraftPolicy {
    const val WEIGHT_STEP_HUNDREDTHS_KG = 250

    fun incrementReps(value: Int): Int? = (value + 1).takeIf {
        value < WearProtocol.MAX_WEAR_REPS
    }

    fun decrementReps(value: Int): Int? = (value - 1).takeIf { value > 0 }

    fun incrementWeight(value: Int?): Int? = when {
        value == null -> 0
        value > WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG - WEIGHT_STEP_HUNDREDTHS_KG -> null
        else -> value + WEIGHT_STEP_HUNDREDTHS_KG
    }

    fun decrementWeight(value: Int?): WeightChange? = when {
        value == null -> null
        value == 0 -> WeightChange(null)
        value >= WEIGHT_STEP_HUNDREDTHS_KG -> WeightChange(value - WEIGHT_STEP_HUNDREDTHS_KG)
        else -> null
    }
}

internal data class WeightChange(val value: Int?)
