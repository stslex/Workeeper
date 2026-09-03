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

/**
 * Gate G6 of the Wear controller redesign spec §7, Russian half — the locale with the longest
 * strings (`update_required` spans the full status width). English is [WearOverflowGateTest].
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearOverflowGateRuTest {

    @Test
    @DisplayName("no Russian text overflows at font scales 1.0 and 1.24 on any surface")
    fun noTextOverflowsOnAnySurfaceAtEitherFontScale() = runComposeUiTest {
        assertNoTextOverflowAcrossAllSurfaces()
    }
}
