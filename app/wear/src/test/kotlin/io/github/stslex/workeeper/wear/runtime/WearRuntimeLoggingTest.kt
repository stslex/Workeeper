// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class WearRuntimeLoggingTest {

    private var previousLogging = true

    @BeforeEach
    fun setup() {
        previousLogging = Log.isLogging
        ShadowLog.clear()
        mockkObject(FirebaseCrashlyticsHolder)
        every { FirebaseCrashlyticsHolder.recordException(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Log.isLogging = previousLogging
        unmockkObject(FirebaseCrashlyticsHolder)
        ShadowLog.clear()
    }

    @Test
    fun releaseRuntimeFailureReportsToCrashlyticsWithoutLogcat() {
        Log.isLogging = false
        val failure = IOException("Synthetic callback failure")

        runWearRuntimeUiEvent<Unit> { throw failure }

        assertTrue(ShadowLog.getLogsForTag("WatchRuntimeUi").isEmpty(), "Release logcat must stay silent")
        verify(exactly = 1) { FirebaseCrashlyticsHolder.recordException(failure, "WatchRuntimeUi") }
    }

    @Test
    fun debugRuntimeFailureReportsToCrashlyticsAndLogcat() {
        Log.isLogging = true
        val failure = IOException("Synthetic callback failure")

        runWearRuntimeUiEvent<Unit> { throw failure }

        assertTrue(ShadowLog.getLogsForTag("WatchRuntimeUi").any { it.throwable === failure })
        verify(exactly = 1) { FirebaseCrashlyticsHolder.recordException(failure, "WatchRuntimeUi") }
    }
}
