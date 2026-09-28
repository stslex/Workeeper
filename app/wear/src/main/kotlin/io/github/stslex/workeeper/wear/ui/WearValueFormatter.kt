// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal data class WearFormattedValues(
    val reps: String?,
    val weight: String?,
)

internal object WearValueFormatter {

    fun format(reps: Int?, weightHundredthsKg: Int?, locale: Locale): WearFormattedValues {
        val numbers = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
        return WearFormattedValues(
            reps = reps?.let { numbers.format(it) },
            weight = weightHundredthsKg?.let { numbers.format(BigDecimal.valueOf(it.toLong(), WEIGHT_SCALE)) },
        )
    }

    private const val WEIGHT_SCALE = 2
}
