// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** The Russian resource context runs the same visibility contract as [WearTouchTargetGateTest]. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearTouchTargetGateRuTest {

    @Test
    @DisplayName("Russian targets have visible 48dp bounds and do not overlap at either screen or font extreme")
    fun everyClickTargetIsVisiblyReachable() = runComposeUiTest {
        assertVisibleTouchTargets()
    }
}
