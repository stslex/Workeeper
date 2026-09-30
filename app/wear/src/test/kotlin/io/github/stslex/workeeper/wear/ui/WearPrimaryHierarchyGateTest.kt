// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G7 (#284 review), at both [WearScreen] extremes: the enabled primary action is never
 * smaller than the disabled one, in any state where both exist — an unavailable action more
 * prominent than an available one inverts the hierarchy §4 requires. Sizes are the laid-out
 * node dimensions, measured under real text metrics, compared within each screen.
 *
 * Red when the sizes are swapped back (enabled Small, disabled Medium).
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearPrimaryHierarchyGateTest {

    @Test
    @DisplayName("the enabled primary action is never smaller than disabled, on both extremes")
    fun enabledPrimaryActionIsNeverSmallerThanDisabled() = runComposeUiTest {
        val enabledFixture =
            requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(enabledFixture)
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            model = enabledFixture
            waitForIdle()
            val enabled = completeSetSize()

            listOf(
                "ACTIVE with an invalid draft" to enabledFixture.copy(completeEnabled = false),
                "REFRESH_REQUIRED" to
                    requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED)),
                "DISCONNECTED" to
                    requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.DISCONNECTED)),
            ).forEach { (label, state) ->
                model = state
                waitForIdle()
                val disabled = completeSetSize()
                assertTrue(
                    enabled.width >= disabled.width && enabled.height >= disabled.height,
                    "screen=$current $label: the disabled complete-set button ($disabled px) " +
                        "is larger than the enabled one ($enabled px) — the unavailable " +
                        "action may never be the more prominent one",
                )
            }
        }
    }

    private fun ComposeUiTest.completeSetSize(): IntSize =
        onNodeWithTag("complete_set").fetchSemanticsNode().size
}
