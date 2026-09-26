// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.ui.surface
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AcceptanceScenarioReceiverTest {
    private val application get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun allowShellReceiver() {
        shadowOf(application).grantPermissions(Manifest.permission.DUMP)
        Settings.Global.putInt(application.contentResolver, Settings.Global.BOOT_COUNT, 1)
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, "no_session"))
    }

    @AfterEach
    fun finishScenario() {
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, "no_session"))
    }

    @Test
    fun orderedCommandsUseExistingDriverAndDoNotLaunchAnActivity() {
        assertEquals(Ack(Activity.RESULT_CANCELED, "rejected:driver_rejected"), ordered("refresh"))
        seedActive()
        assertEquals(Ack(Activity.RESULT_OK, "accepted:refresh"), ordered("refresh"))
        assertTrue(WatchRuntimeFactory.get(application).surface.value.completeEnabled)
        assertEquals(Ack(Activity.RESULT_OK, "accepted:disconnect"), ordered("disconnect"))
        assertEquals(WearSurfaceKind.DISCONNECTED, WatchRuntimeFactory.get(application).surface.value.kind)
        assertEquals(Ack(Activity.RESULT_OK, "accepted:terminal"), ordered("terminal"))
        assertEquals(WearSurfaceKind.WORKOUT_COMPLETE, WatchRuntimeFactory.get(application).surface.value.kind)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun unexpectedScenariosAndActionsCannotReachDriver() {
        seedActive()
        val original = WatchRuntimeFactory.get(application).surface.value
        listOf(null, "expire", "stop_ongoing", "no_session", "fixture:active_boundary", "unknown").forEach { scenario ->
            assertEquals(Ack(Activity.RESULT_CANCELED, "rejected:invalid_scenario"), ordered(scenario))
            assertEquals(original, WatchRuntimeFactory.get(application).surface.value)
        }
        assertEquals(
            Ack(Activity.RESULT_CANCELED, "rejected:invalid_action"),
            ordered("terminal", action = "unexpected.action"),
        )
        assertEquals(original, WatchRuntimeFactory.get(application).surface.value)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun unorderedCommandCannotMutateRuntime() {
        seedActive()
        val original = WatchRuntimeFactory.get(application).surface.value
        application.sendBroadcast(command("disconnect"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original, WatchRuntimeFactory.get(application).surface.value)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    private fun seedActive() {
        assertTrue(WatchRuntimeFactory.handleDebugScenario(application, SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
    }

    private fun command(scenario: String?, action: String = AcceptanceScenarioReceiver.ACTION) = Intent(action)
        .setComponent(ComponentName(application, AcceptanceScenarioReceiver::class.java))
        .putExtra(AcceptanceScenarioReceiver.EXTRA_SCENARIO, scenario)

    private fun ordered(scenario: String?, action: String = AcceptanceScenarioReceiver.ACTION): Ack {
        var result: Ack? = null
        val reply = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result = Ack(resultCode, resultData)
            }
        }
        application.sendOrderedBroadcast(
            command(scenario, action),
            null,
            reply,
            Handler(Looper.getMainLooper()),
            UNHANDLED_RESULT,
            null,
            null,
        )
        shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(result) { "Ordered broadcast must deliver its final result" }
    }

    private data class Ack(val code: Int, val data: String?)
}

private const val UNHANDLED_RESULT = 99
