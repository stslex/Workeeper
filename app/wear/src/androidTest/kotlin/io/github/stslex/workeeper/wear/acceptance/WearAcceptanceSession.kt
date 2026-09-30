// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.acceptance

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.util.Locale

internal class WearAcceptanceSession(val rule: ComposeTestRule) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    val fixture: String = requiredArgument("fixture")
    private val cell = requiredArgument("acceptanceCellId").also {
        require(it.matches(Regex("[A-Za-z0-9_.-]+"))) { "Unsafe acceptanceCellId" }
    }
    val directory = File(
        requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "wear-acceptance/$cell",
    )
    val receipt = JSONObject().put("schema", 1).put("acceptanceCellId", cell).put("fixture", fixture)
        .put("status", "FAIL").put("hostType", "real-MainActivity")
        .put("outputDirectory", directory.absolutePath)
    lateinit var scenario: ActivityScenario<MainActivity>
    lateinit var activity: MainActivity
    var launchedElapsedRealtimeMs: Long = 0L
        private set

    fun run(method: String, block: WearAcceptanceSession.() -> Unit) {
        assertTrue("Artifact directory", directory.mkdirs() || directory.isDirectory)
        receipt.put("method", method).put("startedAtEpochMs", System.currentTimeMillis())
        try {
            launch()
            assertConfiguration()
            block()
            receipt.put("status", "PASS")
        } finally {
            receipt.put("finishedAtEpochMs", System.currentTimeMillis())
                .put("artifacts", JSONArray(directory.listFiles().orEmpty().map { it.name }.sorted()))
            writeJson("receipt.json", receipt)
            if (::scenario.isInitialized) scenario.close()
        }
    }

    private fun launch() {
        // WAKEUP does not reset idle time while already awake; reset it before interactive captures.
        val keys = listOf("SLEEP", "WAKEUP")
        receipt.put("initialPowerKeys", JSONArray(keys))
            .put("initialPowerResetStartedElapsedMs", SystemClock.elapsedRealtime())
        keys.forEach { key ->
            val descriptor = instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_$key")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        receipt.put("initialPowerResetFinishedElapsedMs", SystemClock.elapsedRealtime())
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra(SyntheticSurfaceFixtures.EXTRA_ID, "fixture:$fixture")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch(intent)
        scenario.onActivity { activity = it }
        rule.waitForIdle()
        launchedElapsedRealtimeMs = SystemClock.elapsedRealtime()
    }

    fun dispatchScenario(id: String) {
        scenario.onActivity { current ->
            current.startActivity(
                Intent(current, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(SyntheticSurfaceFixtures.EXTRA_ID, id),
            )
        }
        rule.waitForIdle()
    }

    fun runtimeModel(): WearSurfaceModel = WearSurfaceMapper.map(WatchRuntimeFactory.get(activity).snapshot.value)

    fun expectedModel(): WearSurfaceModel {
        val expected = requireNotNull(SyntheticSurfaceFixtures.find(fixture)).copy(
            selectedLocale = activity.resources.configuration.locales[0],
        )
        val preview = fixture in PREVIEW_FIXTURES
        receipt.put("sourceClassification", if (preview) "STATIC_PREVIEW" else "SYNTHETIC_RUNTIME")
            .put("classificationBasis", "Existing MainActivity/DebugSnapshotDriver fixture route")
            .put("expectedKind", expected.kind.name)
            .put("runtimeModel", modelJson(runtimeModel()))
        if (preview) return expected
        val actual = runtimeModel()
        assertEquals("Runtime kind for $fixture", expected.kind, actual.kind)
        assertEquals("Runtime reps for $fixture", expected.reps, actual.reps)
        assertEquals("Runtime weight for $fixture", expected.weightHundredthsKg, actual.weightHundredthsKg)
        assertEquals("Runtime controls for $fixture", expected.controlsEnabled, actual.controlsEnabled)
        assertEquals("Runtime completion for $fixture", expected.completeEnabled, actual.completeEnabled)
        assertEquals(
            "Runtime reason for $fixture",
            expected.completionUnavailableReason,
            actual.completionUnavailableReason,
        )
        return actual
    }

    private fun assertConfiguration() {
        val config = activity.resources.configuration
        val density = activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode()
        val actual = JSONObject().put("api", Build.VERSION.SDK_INT).put("isScreenRound", config.isScreenRound)
            .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
            .put(
                "screenOffTimeoutMs",
                Settings.System.getLong(activity.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT),
            )
            .put("screenWidthDp", config.screenWidthDp).put("screenHeightDp", config.screenHeightDp)
            .put("locale", config.locales[0].language).put("localeTag", config.locales[0].toLanguageTag())
            .put("fontScale", config.fontScale)
            .put("density", density).put("rootWidthPx", root.size.width).put("rootHeightPx", root.size.height)
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        writeJson("config.json", actual)
        receipt.put("configuration", actual)
        assertEquals("Actual Android API", requiredArgument("expectedApi").toInt(), Build.VERSION.SDK_INT)
        assertTrue("Actual round configuration required", config.isScreenRound)
        val expectedDp = requiredArgument("expectedDp").toInt()
        assertEquals("Actual width dp", expectedDp, config.screenWidthDp)
        assertEquals("Actual height dp", expectedDp, config.screenHeightDp)
        assertEquals("Actual window width dp", expectedDp.toFloat(), root.size.width / density, 1f)
        assertEquals("Actual window height dp", expectedDp.toFloat(), root.size.height / density, 1f)
        assertEquals("Actual font scale", requiredArgument("expectedFontScale").toFloat(), config.fontScale, 0.005f)
        assertEquals(
            "Actual resource language",
            Locale.forLanguageTag(requiredArgument("expectedLocale")).language,
            config.locales[0].language,
        )
    }

    fun capture(label: String) {
        rule.waitForIdle()
        saveBitmap(rule.onRoot().captureToImage().asAndroidBitmap(), "$label.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { saveBitmap(it, "$label-system.png") }
        File(directory, "$label-merged.txt").writeText(rule.onRoot().printToString())
        File(directory, "$label-unmerged.txt").writeText(rule.onRoot(useUnmergedTree = true).printToString())
        val nodes = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("all") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        val bounds = JSONArray()
        nodes.forEach { node ->
            val rect = node.boundsInRoot
            bounds.put(
                JSONObject().put("id", node.id).put("tag", node.config.getOrNull(SemanticsProperties.TestTag))
                    .put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom)
                    .put("declaredWidthPx", node.size.width).put("declaredHeightPx", node.size.height)
                    .put("density", node.layoutInfo.density.density),
            )
        }
        writeJson("$label-bounds.json", JSONObject().put("nodes", bounds))
    }

    fun saveBitmap(bitmap: Bitmap, name: String) {
        File(directory, name).outputStream().use {
            assertTrue("PNG encoding", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    fun writeJson(name: String, value: JSONObject) {
        File(directory, name).writeText(value.toString(2) + "\n")
    }

    fun argument(name: String, default: String): String = arguments.getString(name) ?: default

    private fun requiredArgument(name: String): String = requireNotNull(arguments.getString(name)) {
        "Missing instrumentation argument $name"
    }
}

internal fun modelJson(model: WearSurfaceModel): JSONObject = JSONObject()
    .put("kind", model.kind.name).put("reps", model.reps).put("weightHundredthsKg", model.weightHundredthsKg)
    .put("controlsEnabled", model.controlsEnabled).put("completeEnabled", model.completeEnabled)
    .put("completionUnavailableReason", model.completionUnavailableReason?.name)
    .put("fieldError", model.fieldError?.name)
    .put("hasUnsubmittedDraft", model.hasUnsubmittedDraft)

private val PREVIEW_FIXTURES = setOf(
    SyntheticSurfaceFixtures.WEIGHT_ERROR,
    SyntheticSurfaceFixtures.RETRYABLE,
    SyntheticSurfaceFixtures.PROTOCOL_MISMATCH,
    SyntheticSurfaceFixtures.LOADING,
)
