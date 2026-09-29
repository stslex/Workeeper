package io.github.stslex.workeeper.core.core.logger

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Pins what each [Log] method hands Crashlytics, the premise of the wear-paired-transport.md
 * §9.2 audit: an `e()` message and every `v()` line stay in Logcat, and `e()` reports only the
 * throwable and the tag.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class LogTelemetrySinkTest {

    private val sinks = mutableListOf<String>()
    private val recordedTags = mutableListOf<String>()
    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        mockkObject(FirebaseCrashlyticsHolder)
        every { FirebaseCrashlyticsHolder.log(any()) } answers { sinks += firstArg<String>() }
        every { FirebaseCrashlyticsHolder.recordException(any(), any()) } answers {
            recordedTags += secondArg<String>()
            sinks += "${secondArg<String>()} ${firstArg<Throwable>().message}"
        }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<String>()) } answers {
            sinks += "${firstArg<String>()}=${secondArg<String>()}"
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(FirebaseCrashlyticsHolder)
        Log.isLogging = wasLogging
    }

    @Test
    fun `e and v messages never reach Crashlytics in either logging mode`() {
        listOf(false, true).forEach { logging ->
            Log.isLogging = logging
            val logger = Log.tag(TAG)

            logger.e(IllegalStateException("fixed text"), SENTINEL)
            logger.v(SENTINEL)
            logger.v { SENTINEL }
        }

        assertEquals(listOf(TAG, TAG), recordedTags, "e() reports the throwable under its tag, once per call")
        val leaks = sinks.filter { SENTINEL in it }
        assertTrue(leaks.isEmpty(), "an e() message or v() line reached Crashlytics: $leaks")
    }

    private companion object {
        const val TAG = "SinkProbe"
        const val SENTINEL = "Sentinel-Holiday-7f3a.jpg"
    }
}
