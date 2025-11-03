package io.github.stslex.workeeper.core.core.utils

import java.util.Locale

object NumUiUtils {

    private const val FORMAT_PATTERN = "%.1f"
    private const val THOUSAND_NUM = 1000
    private const val THOUSAND_NUM_F = 1000f

    fun roundThousand(
        value: Double,
        locale: Locale,
        pattern: String = FORMAT_PATTERN,
    ): Double = if (value >= THOUSAND_NUM) {
        (value / THOUSAND_NUM_F).let { roundValue ->
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
