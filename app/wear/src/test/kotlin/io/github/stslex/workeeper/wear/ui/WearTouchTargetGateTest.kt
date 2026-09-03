// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G1 of the Wear controller redesign spec §7, at both [WearScreen] extremes: every
 * semantics node carrying a click action has layout bounds of at least 48dp on both axes, and
 * no two such nodes overlap. Size comes from the laid-out node (unclipped — the target's own
 * dimensions); overlap from the clipped bounds (what is actually tappable — content clipped by
 * the scroll viewport cannot receive the touch the bottom-edge button owns). On the small
 * screen the cards rest below the fold, so the gate scrolls them into view before tapping,
 * exactly as a user must.
 *
 * Red when the bottom-edge button's height is set to 40dp.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearTouchTargetGateTest {

    @Test
    @DisplayName("every click target is at least 48dp with no overlap, on both screen extremes")
    fun everyClickTargetIsAtLeast48dpWithNoOverlap() = runComposeUiTest {
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            model = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            waitForIdle()
            assertClickTargets(surface = "screen=$current active controller", expectedCount = 3)

            onNodeWithTag("reps_card").performScrollTo().performClick()
            waitForIdle()
            assertClickTargets(surface = "screen=$current numeric editor", expectedCount = 2)

            model = fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED)
            waitForIdle()
            assertClickTargets(surface = "screen=$current read-only controller", expectedCount = 3)

            model = fixture(SyntheticSurfaceFixtures.RETRYABLE)
            waitForIdle()
            assertClickTargets(surface = "screen=$current retryable error", expectedCount = 1)
        }
    }

    private fun ComposeUiTest.assertClickTargets(surface: String, expectedCount: Int) {
        val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
        assertEquals(
            expectedCount,
            nodes.size,
            "$surface: expected $expectedCount click targets, found ${nodes.size}",
        )
        val bounds = nodes.map { node ->
            val density = node.layoutInfo.density.density
            Target(
                tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged",
                widthDp = node.size.width / density,
                heightDp = node.size.height / density,
                rect = node.boundsInRoot,
            )
        }
        bounds.forEach { target ->
            assertTrue(
                target.widthDp >= MIN_TARGET_DP && target.heightDp >= MIN_TARGET_DP,
                "$surface: «${target.tag}» is ${target.widthDp} × ${target.heightDp} dp, " +
                    "under $MIN_TARGET_DP dp",
            )
        }
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertTrue(
                    !first.rect.overlaps(second.rect),
                    "$surface: «${first.tag}» ${first.rect} overlaps «${second.tag}» ${second.rect}",
                )
            }
        }
    }

    private data class Target(
        val tag: String,
        val widthDp: Float,
        val heightDp: Float,
        val rect: Rect,
    )

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {
        const val MIN_TARGET_DP = 48f
    }
}
