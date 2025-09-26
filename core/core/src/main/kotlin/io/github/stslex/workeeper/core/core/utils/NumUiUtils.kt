package io.github.stslex.workeeper.core.core.utils

import java.util.Locale

object NumUiUtils {

    private const val FORMAT_PATTERN = "%.1f"

    class test

    fun roundThousand(
        value: Double,
        locale: Locale,
        pattern: String = FORMAT_PATTERN,
    ): Double = if (value >= 1000) {
        (value / 1000.0).let { roundValue ->
            String.format(
                locale = locale,
                format = pattern,
                roundValue,
            ).toDouble()
        }
    } else {
        value
    }

    infix fun Double.safeDiv(divisor: Int): Double = if (divisor == 0) {
        0.0
    } else {
        this / divisor
    }
}
