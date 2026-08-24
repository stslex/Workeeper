// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NumUiUtilsTest {

    @Test
    fun `roundThousand returns zero unchanged`() {
        assertEquals(0.0, NumUiUtils.roundThousand(0.0), 0.0)
    }

    @Test
    fun `roundThousand returns small value unchanged`() {
        assertEquals(42.0, NumUiUtils.roundThousand(42.0), 0.0)
    }

    @Test
    fun `roundThousand returns value just below threshold unchanged`() {
        assertEquals(999.0, NumUiUtils.roundThousand(999.0), 0.0)
    }

    @Test
    fun `roundThousand divides by 1000 exactly at threshold`() {
        assertEquals(1.0, NumUiUtils.roundThousand(1000.0), 0.0)
    }

    @Test
    fun `roundThousand rounds down when fraction is below half`() {
        assertEquals(1.2, NumUiUtils.roundThousand(1234.0), 1e-9)
    }

    @Test
    fun `roundThousand rounds up when fraction is at half`() {
        assertEquals(1.3, NumUiUtils.roundThousand(1250.0), 1e-9)
    }

    @Test
    fun `roundThousand handles large values correctly`() {
        assertEquals(15.5, NumUiUtils.roundThousand(15500.0), 1e-9)
    }

    // GUARD: roundThousand stays pure math — String.format threw on comma-decimal locales.

    @Test
    fun `roundThousand result is locale-independent for value that would have used comma decimal`() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ru", "RU"))
            assertEquals(1.2, NumUiUtils.roundThousand(1234.0), 1e-9)
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `roundThousand result matches US locale result for the same input`() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val usResult = NumUiUtils.roundThousand(1234.0)
            java.util.Locale.setDefault(java.util.Locale("ru", "RU"))
            val ruResult = NumUiUtils.roundThousand(1234.0)
            assertEquals(usResult, ruResult, 1e-9)
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }
}
