// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.acceptance

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
class WearAmbientDeviceAcceptanceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun systemSleepWakePreservesEligibleSurface() {
        WearAcceptanceSession(compose).run("systemSleepWakePreservesEligibleSurface") {
            assertEquals(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY, fixture)
            expectedModel()
            val editor = argument("editor", "none")
            require(editor in setOf("none", "reps", "weight"))
            val expireAuthority = argument("expireAuthority", "false").toBooleanStrict()
            receipt.put("editor", editor).put("expireAuthority", expireAuthority)
            prepareAmbientEntry(editor)
            val before = runtimeModel()
            val beforeScroll = if (editor == "none") scrollPosition() else null
            capture("before-sleep")
            try {
                systemKey("SLEEP")
                awaitSystemAmbient()
                assertReadOnlyAmbient()
                capture("system-ambient")
                holdAmbient(expireAuthority)
            } finally {
                systemKey("WAKEUP")
            }
            rule.waitUntil(argument("wakeTimeoutMs", "15000").toLong()) { !hasAmbientSummary() }
            rule.waitForIdle()
            val after = runtimeModel()
            assertEquals("Sleep/wake preserves reps", before.reps, after.reps)
            assertEquals("Sleep/wake preserves nullable weight", before.weightHundredthsKg, after.weightHundredthsKg)
            assertEquals("Sleep/wake preserves unsent state", before.hasUnsubmittedDraft, after.hasUnsubmittedDraft)
            if (expireAuthority) {
                assertFalse("Real elapsed-time expiry removes editing authority", after.controlsEnabled)
                assertFalse("Real elapsed-time expiry removes completion authority", after.completeEnabled)
                rule.onNodeWithTag("editor").assertDoesNotExist()
                rule.onNodeWithTag("controller_scroll").assertIsFocused()
            } else if (editor == "none") {
                assertTrue("Short trial remains eligible", after.controlsEnabled)
                rule.onNodeWithTag("controller_scroll").assertIsFocused()
                assertEquals(
                    "Actual ambient preserves controller scroll", requireNotNull(beforeScroll), scrollPosition(), 0.5f,
                )
            } else {
                assertTrue("Short trial remains eligible", after.controlsEnabled)
                rule.onNodeWithTag("editor_rotary").assertIsFocused()
            }
            capture("after-wake")
            receipt.put("systemAmbientObserved", true).put("wakeModel", modelJson(after))
        }
    }
}

private fun WearAcceptanceSession.holdAmbient(expireAuthority: Boolean) {
    val started = SystemClock.elapsedRealtime()
    val deadline = if (expireAuthority) {
        launchedElapsedRealtimeMs + NATURAL_AUTHORITY_WINDOW_MS + EXPIRY_MARGIN_MS
    } else {
        started + AMBIENT_HOLD_MS
    }
    receipt.put("authorityHoldStartedElapsedMs", started).put("authorityHoldDeadlineElapsedMs", deadline)
    while (SystemClock.elapsedRealtime() < deadline) {
        SystemClock.sleep(minOf(HOLD_SAMPLE_MS, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
    }
    receipt.put("authorityHoldFinishedElapsedMs", SystemClock.elapsedRealtime())
}

private fun WearAcceptanceSession.prepareAmbientEntry(editor: String) {
    if (editor == "none") {
        rotate(120f)
    } else {
        openEditor("${editor}_card")
        rule.onNodeWithTag("editor_decrease").performTouchInput { click() }
        rule.waitForIdle()
        assertTrue("Editor trial must carry actual unsent values", runtimeModel().hasUnsubmittedDraft)
    }
}

private fun WearAcceptanceSession.awaitSystemAmbient() {
    try {
        rule.waitUntil(argument("ambientWaitMs", "15000").toLong()) { hasAmbientSummary() }
    } catch (_: ComposeTimeoutException) {
        receipt.put("status", "BLOCKED").put("systemAmbientObserved", false)
            .put("blockedReason", "System SLEEP did not produce observable ambient UI; no provider was substituted")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {
            saveBitmap(it, "ambient-unobserved-system.png")
        }
        assumeTrue("System ambient capability/configuration was not observed; not acceptance PASS", false)
    }
}

private fun WearAcceptanceSession.hasAmbientSummary(): Boolean = rule.onAllNodesWithTag("ambient_summary")
    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

private fun WearAcceptanceSession.assertReadOnlyAmbient() {
    rule.onNodeWithTag("editor").assertDoesNotExist()
    rule.onNodeWithTag("complete_set").assertDoesNotExist()
    val actions = rule.onAllNodes(
        SemanticsMatcher("interactive semantics") {
            it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetText)
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
    assertTrue("Real ambient must expose no interactive semantics", actions.isEmpty())
    val summary = node("ambient_summary", merged = true)
    assertEquals(
        activity.getString(R.string.ambient_read_only), summary.config.getOrNull(SemanticsProperties.StateDescription),
    )
    val description = summary.config[SemanticsProperties.ContentDescription].joinToString()
    val model = runtimeModel()
    assertTrue(description.contains(activity.getString(R.string.ambient_reps, model.formattedValues.reps)))
    assertTrue(description.contains(activity.getString(R.string.weight_value, model.formattedValues.weight)))
    assertEquals(
        "Ambient unsent marker", model.hasUnsubmittedDraft,
        description.contains(activity.getString(R.string.ambient_not_sent)),
    )
    val before = runtimeModel()
    rotate(49f)
    assertEquals("Ambient rotary cannot change reps", before.reps, runtimeModel().reps)
    assertEquals("Ambient rotary cannot change weight", before.weightHundredthsKg, runtimeModel().weightHundredthsKg)
    assertFalse(
        "Ambient carries no editing node", rule.onAllNodesWithTag("editor_rotary").fetchSemanticsNodes().isNotEmpty(),
    )
}

private fun WearAcceptanceSession.systemKey(key: String) {
    val command = "input keyevent KEYCODE_$key"
    val started = SystemClock.elapsedRealtime()
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    writeJson(
        "system-${key.lowercase()}.json",
        JSONObject().put("command", command).put("startedElapsedRealtimeMs", started)
            .put("endedElapsedRealtimeMs", SystemClock.elapsedRealtime()).put("output", output),
    )
}

private const val AMBIENT_HOLD_MS = 5_000L
private const val NATURAL_AUTHORITY_WINDOW_MS = 120_000L
private const val EXPIRY_MARGIN_MS = 2_000L
private const val HOLD_SAMPLE_MS = 250L
