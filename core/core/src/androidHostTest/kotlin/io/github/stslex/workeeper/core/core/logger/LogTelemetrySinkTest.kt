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
    private val keyed = mutableListOf<Pair<String, Int>>()
    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        mockkObject(FirebaseCrashlyticsHolder)
        every { FirebaseCrashlyticsHolder.log(any<String>()) } answers { sinks += firstArg<String>() }
        every { FirebaseCrashlyticsHolder.log(any<String>(), any<Int>()) } answers {
            sinks += firstArg<String>()
            keyed += firstArg<String>() to secondArg<Int>()
        }
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

    @Test
    fun `a dedupe key reaches the debounce and never the line`() {
        Log.isLogging = false
        val logger = Log.tag(TAG)

        logger.d("label", DEDUPE_KEY)
        logger.i("label", DEDUPE_KEY)
        logger.w("label", DEDUPE_KEY)

        assertEquals(List(3) { "$TAG: label" to DEDUPE_KEY }, keyed)
        assertTrue(sinks.none { DEDUPE_KEY.toString() in it }, "the key never enters a line: $sinks")
    }

    private companion object {
        const val DEDUPE_KEY = 918_273_645
        const val TAG = "SinkProbe"
        const val SENTINEL = "Sentinel-Holiday-7f3a.jpg"
    }
}
