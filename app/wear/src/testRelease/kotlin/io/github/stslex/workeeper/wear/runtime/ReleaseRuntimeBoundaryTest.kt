// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.ui.currentSurface
import io.github.stslex.workeeper.wear.ui.ongoingStatus
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class ReleaseRuntimeBoundaryTest {
    @Test
    fun releaseRejectsSyntheticEventsAndExcludesTheirSourceClass() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(WatchRuntimeFactory.handleDebugScenario(context, "active_boundary"))
        val runtime = WatchRuntimeFactory.get(context)
        assertFalse(runtime.currentSurface().completeEnabled)
        assertEquals(WatchActionResult.Rejected, runtime.onAction(ControllerAction.CompleteSet))
        assertEquals(OngoingStatus.Inactive, runtime.ongoingStatus.value)
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.stslex.workeeper.wear.runtime.DebugSnapshotDriver")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver")
        }
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context.packageName,
                    "io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver",
                ),
                0,
            )
        }
    }
}
