package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The real Firebase holders against mocked sinks: a redacted line or event is dropped as a repeat
 * only when its payload text repeats, never when two payloads share a `String.hashCode()`
 * (wear-paired-transport.md §9.2 item 6: the redaction removes no telemetry).
 *
 * GUARD: each holder reads its sink once, lazily; nothing else in this module's host tests may
 * reach the real holders first, or the sink is cached as absent and nothing is counted.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class FirebaseHolderDebounceTest {

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val analytics = mockk<FirebaseAnalytics>(relaxed = true)

    @BeforeEach
    fun setUp() {
        assertEquals(PAYLOAD.hashCode(), COLLIDING_PAYLOAD.hashCode(), "the payloads must share a hash")
        assertNotEquals(PAYLOAD, COLLIDING_PAYLOAD)
        mockkStatic(CRASHLYTICS_FACADE, ANALYTICS_FACADE)
        every { Firebase.crashlytics } returns crashlytics
        every { Firebase.analytics } returns analytics
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(CRASHLYTICS_FACADE, ANALYTICS_FACADE)
    }

    @Test
    fun `a breadcrumb is dropped only when its payload repeats`() {
        FirebaseCrashlyticsHolder.log(OTHER_LINE)
        FirebaseCrashlyticsHolder.log(LINE, telemetryDedupeKey(PAYLOAD))
        FirebaseCrashlyticsHolder.log(LINE, telemetryDedupeKey(COLLIDING_PAYLOAD))
        FirebaseCrashlyticsHolder.log(LINE, telemetryDedupeKey(COLLIDING_PAYLOAD))

        verify(exactly = 1) { crashlytics.log(OTHER_LINE) }
        verify(exactly = 2) { crashlytics.log(LINE) }
    }

    @Test
    fun `an analytics event is dropped only when its payload repeats`() {
        FirebaseAnalyticsHolder.log(FirebaseEvent.Common(OTHER_EVENT))
        FirebaseAnalyticsHolder.log(storeAction(PAYLOAD))
        FirebaseAnalyticsHolder.log(storeAction(COLLIDING_PAYLOAD))
        FirebaseAnalyticsHolder.log(storeAction(COLLIDING_PAYLOAD))

        verify(exactly = 1) { analytics.logEvent(OTHER_EVENT, any()) }
        verify(exactly = 2) { analytics.logEvent("store_${STORE}_action", any()) }
    }

    @Test
    fun `a dedupe key prints no payload`() {
        val key = telemetryDedupeKey(PAYLOAD)

        assertEquals(false, PAYLOAD in key.toString(), "key: $key")
        assertEquals(false, PAYLOAD in storeAction(PAYLOAD).toString(), "event: ${storeAction(PAYLOAD)}")
    }

    private fun storeAction(payload: String) = FirebaseEvent.Store.Action(
        action = ACTION,
        storeName = STORE,
        dedupeKey = telemetryDedupeKey(payload),
    )

    private companion object {
        const val CRASHLYTICS_FACADE = "com.google.firebase.crashlytics.FirebaseCrashlyticsKt"
        const val ANALYTICS_FACADE = "com.google.firebase.analytics.AnalyticsKt"
        const val LINE = "Probe: consume: Recorded"
        const val OTHER_LINE = "Probe: warm-up"
        const val OTHER_EVENT = "probe_warm_up"
        const val ACTION = "Recorded"
        const val STORE = "Probe"

        // "FB" and "Ea" share a String.hashCode() (2236), so do these equal-length payloads.
        const val PAYLOAD = "Recorded(name=Sentinel-Bench-7f3aFB, weight=987.25, reps=4321)"
        const val COLLIDING_PAYLOAD = "Recorded(name=Sentinel-Bench-7f3aEa, weight=987.25, reps=4321)"
    }
}
