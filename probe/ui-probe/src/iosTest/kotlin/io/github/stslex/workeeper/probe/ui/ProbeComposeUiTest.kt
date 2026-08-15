// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.ui

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * P6: the intersection smoke — AGP-KMP module × CMP × Kotlin/Native, executed IN the iOS
 * simulator by the standard KGP test task (:probe:ui-probe:iosSimulatorArm64Test). Not a
 * walking skeleton: instantiating ComposeUIViewController forces the CMP iOS runtime,
 * Skiko, and the UIKit interop to link and initialize on device (simulator) — the exact
 * stack Phase 7 stands on. Rendering a frame into a window is deliberately out of scope.
 */
internal class ProbeComposeUiTest {

    @Test
    fun composeUIViewControllerInstantiates() {
        val controller = ComposeUIViewController {
            ProbeCard(label = "ios-smoke")
        }
        assertNotNull(controller.view)
    }
}
