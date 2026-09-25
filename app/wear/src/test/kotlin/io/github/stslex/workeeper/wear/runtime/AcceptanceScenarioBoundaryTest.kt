// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import io.github.stslex.workeeper.wear.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AcceptanceScenarioBoundaryTest {
    @Test
    fun receiverExistsOnlyInDebugAndRequiresShellPermission() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context.packageName, RECEIVER_CLASS)
        if (BuildConfig.DEBUG) {
            assertNotNull(Class.forName(RECEIVER_CLASS))
            val info = context.packageManager.getReceiverInfo(component, 0)
            assertTrue(info.exported)
            assertTrue(info.enabled)
            assertEquals(Manifest.permission.DUMP, info.permission)
        } else {
            assertThrows(ClassNotFoundException::class.java) { Class.forName(RECEIVER_CLASS) }
            assertThrows(PackageManager.NameNotFoundException::class.java) {
                context.packageManager.getReceiverInfo(component, 0)
            }
        }
    }
}

private const val RECEIVER_CLASS = "io.github.stslex.workeeper.wear.runtime.AcceptanceScenarioReceiver"
