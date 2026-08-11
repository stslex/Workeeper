package io.github.stslex.workeeper.core.core.utils

import kotlin.math.roundToLong

object NumUiUtils {

    private const val THOUSAND_NUM = 1000
    private const val THOUSAND_NUM_D = 1000.0
    private const val DECIMAL_SHIFT = 10.0

    fun roundThousand(value: Double): Double = if (value >= THOUSAND_NUM) {
        (value / THOUSAND_NUM_D * DECIMAL_SHIFT).roundToLong() / DECIMAL_SHIFT
    } else {
        value
    }

    infix fun Double.safeDiv(divisor: Int): Double = if (divisor == 0) {
        0.0
    } else {
        this / divisor
    }
}
