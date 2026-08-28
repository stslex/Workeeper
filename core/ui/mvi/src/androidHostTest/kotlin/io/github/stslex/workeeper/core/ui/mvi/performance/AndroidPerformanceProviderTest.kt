// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.performance

import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.navigation.Screen
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Android keeps real Firebase. These cases prove it two ways: the platform actual the common
 * façade was built on IS the Firebase backend (not the iOS no-op), and the router and screen
 * adapter really delegate — driven through the public common façade against deterministic fakes,
 * with the production provider left untouched.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class AndroidPerformanceProviderTest {

    private var wasLogging: Boolean = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
    }

    @AfterEach
    fun tearDown() {
        Log.isLogging = wasLogging
    }

    @Test
    fun theAndroidPlatformBackendIsTheFirebaseOneAndNotANoOp() {
        assertTrue(
            platformPerformanceBackend is FirebasePerformanceBackend,
            "the Android actual must stay the real Firebase backend; it resolved to " +
                "${platformPerformanceBackend::class.qualifiedName}",
        )
    }

    @Test
    fun theAndroidScreenAdapterKeepsTheFirebaseSinkByDefault() {
        assertSame(
            FirebaseScreenTraceSink,
            FirebaseScreenRenderAdapter(activity = null).sink,
            "the production screen adapter must record through FirebaseScreenRenderRecorder",
        )
    }

    @Test
    fun theScreenAdapterDelegatesStartAndStopAndCarriesTheActivity() {
        val sink = RecordingScreenTraceSink()
        val activity = Activity()

        val adapter = FirebaseScreenRenderAdapter(activity = activity, sink = sink)
        adapter.start("Home")
        adapter.stop("Home")

        assertEquals(listOf("start:Home", "stop:Home"), sink.calls)
        assertSame(
            activity,
            sink.startedWith,
            "the adapter must carry the composition's Activity through to the frame recorder",
        )
    }

    @Test
    fun theComposableAndroidScreenProviderKeepsTheFirebaseAdapter() = runComposeUiTest {
        var recorder: ScreenRenderRecorder? = null

        setContent {
            val current = rememberScreenRenderRecorder()
            SideEffect { recorder = current }
        }
        waitForIdle()

        val adapter = recorder as? FirebaseScreenRenderAdapter
            ?: error("the Android composable provider returned ${recorder?.let { it::class }}")
        assertSame(
            FirebaseScreenTraceSink,
            adapter.sink,
            "the actual composable provider must retain the Firebase screen sink",
        )
    }

    @Test
    fun theCommonFacadeRoutesEveryActionThroughTheAndroidFirebaseRouter() {
        val traces = RecordingTraceFactory()
        withFakeTraces(traces) {
            PerformanceMetricsRecorder.process(RecordAction.ActivityCreated(coldStart = true))
            PerformanceMetricsRecorder.process(RecordAction.AppCreated)
            PerformanceMetricsRecorder.process(
                RecordAction.Navigation.NavTo(Screen.BottomBar.Home::class),
            )

            assertEquals(
                listOf(
                    "ActivityCreate_MainActivity",
                    "AppCreate_App",
                    "TTID_Home",
                ),
                traces.created,
                "every action must map to its current trace name",
            )
            assertEquals(
                mapOf("coldStart" to "true"),
                traces.attributesOf("ActivityCreate_MainActivity"),
                "ActivityCreated must still carry the coldStart attribute",
            )
            assertEquals(
                mapOf("navType" to "nav_to"),
                traces.attributesOf("TTID_Home"),
                "a NavTo must still carry navType=nav_to",
            )

            PerformanceMetricsRecorder.process(
                RecordAction.OnScreenPlaced(Screen.BottomBar.Home::class),
            )
            assertEquals(
                listOf("TTID_Home", "AppCreate_App", "ActivityCreate_MainActivity"),
                traces.stopped,
                "OnScreenPlaced stops the matching TTID first, then the App and Activity create " +
                    "traces, in that order",
            )
        }
    }

    @Test
    fun replaceToCarriesTheReplaceNavType() {
        val traces = RecordingTraceFactory()
        withFakeTraces(traces) {
            PerformanceMetricsRecorder.process(
                RecordAction.Navigation.ReplaceTo(Screen.BottomBar.Home::class),
            )

            assertEquals(mapOf("navType" to "replace"), traces.attributesOf("TTID_Home"))
        }
    }

    @Test
    fun appCreationStaysSingleShotAndClearTracesResetsIt() {
        val traces = RecordingTraceFactory()
        withFakeTraces(traces) {
            PerformanceMetricsRecorder.process(RecordAction.AppCreated)
            PerformanceMetricsRecorder.process(RecordAction.AppCreated)
            assertEquals(
                listOf("AppCreate_App"),
                traces.created,
                "AppCreate is single-shot: a second AppCreated must not open a second trace",
            )

            PerformanceMetricsRecorder.process(RecordAction.ClearTraces)
            PerformanceMetricsRecorder.process(RecordAction.AppCreated)
            assertEquals(
                listOf("AppCreate_App", "AppCreate_App"),
                traces.created,
                "ClearTraces resets the single-shot latch, as it does today",
            )
        }
    }

    private fun <T> withFakeTraces(
        traces: PerfTraceFactory,
        block: () -> T,
    ): T {
        val backend = platformPerformanceBackend as? FirebasePerformanceBackend
            ?: error("Android platform backend is not Firebase-backed")
        return backend.withTraceFactoryForTest(traces, block)
    }
}

private class RecordingScreenTraceSink : ScreenTraceSink {

    val calls = mutableListOf<String>()
    var startedWith: Activity? = null
        private set

    override fun start(screenName: String, activity: Activity?) {
        startedWith = activity
        calls += "start:$screenName"
    }

    override fun stop(screenName: String) {
        calls += "stop:$screenName"
    }
}

/** Stands in for Firebase `Trace.create`, so no network or Firebase app is involved. */
private class RecordingTraceFactory : PerfTraceFactory {

    val created = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    private val attributes = mutableMapOf<String, MutableMap<String, String>>()

    fun attributesOf(name: String): Map<String, String> = attributes[name].orEmpty()

    override fun create(name: String): PerfTrace {
        created += name
        val attrs = attributes.getOrPut(name) { mutableMapOf() }
        return object : PerfTrace {
            override fun putAttribute(key: String, value: String) {
                attrs[key] = value
            }

            override fun start() = Unit

            override fun stop() {
                stopped += name
            }
        }
    }
}
