// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** G1: the anchored action is visible initially; every card offers 48dp after scrolling into view. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearTouchTargetGateTest {

    // One composition per Robolectric sandbox.
    @Test
    @DisplayName("English targets have visible 48dp bounds and do not overlap at either screen or font extreme")
    fun everyClickTargetIsVisiblyReachable() = runComposeUiTest {
        assertVisibleTouchTargets()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertVisibleTouchTargets() {
    var screen by mutableStateOf(WearScreen.SMALL_ROUND)
    var fontScale by mutableFloatStateOf(1f)
    var model by mutableStateOf(touchFixture(SyntheticSurfaceFixtures.LOADING))
    setContent {
        WearGateHost(screen, fontScale) {
            Box(Modifier.fillMaxSize().testTag(TOUCH_SCREEN_TAG)) {
                WearControllerScreen(state = model, onAction = {})
            }
        }
    }
    val controllers = listOf(
        SyntheticSurfaceFixtures.ACTIVE_BOUNDARY to 3,
        SyntheticSurfaceFixtures.WEIGHTLESS to 2,
        SyntheticSurfaceFixtures.REFRESH_REQUIRED to 3,
        SyntheticSurfaceFixtures.DISCONNECTED to 3,
        SyntheticSurfaceFixtures.RETRYABLE to 1,
    )
    val configurations = WearScreen.entries.flatMap { current ->
        listOf(1f, LARGEST_WEAR_FONT_SCALE).map { scale -> current to scale }
    }
    configurations.forEach { (current, scale) ->
        screen = current
        fontScale = scale
        controllers.forEach { (id, expectedCount) ->
            // Remove the previous scaffold so the initial assertion cannot inherit its scroll.
            model = touchFixture(SyntheticSurfaceFixtures.LOADING)
            waitForIdle()
            model = touchFixture(id)
            waitForIdle()
            val where = "screen=$current scale=$scale fixture=$id"
            assertDeclaredTargetsAndOverlap(where, expectedCount)
            val primary = if (id == SyntheticSurfaceFixtures.RETRYABLE) "retry" else "complete_set"
            assertVisibleTarget(primary, "$where initial primary")
            if (id != SyntheticSurfaceFixtures.RETRYABLE) {
                assertReachableCards(model, where, expectedCount)
            }
        }

        listOf("reps_card", "weight_card").forEach { card ->
            model = touchFixture(SyntheticSurfaceFixtures.LOADING)
            waitForIdle()
            model = touchFixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            waitForIdle()
            onNodeWithTag(card).performScrollTo().performClick()
            waitForIdle()
            val where = "screen=$current scale=$scale editor=$card"
            assertDeclaredTargetsAndOverlap(where, expectedCount = 2)
            assertVisibleTarget("editor_increase", where)
            assertVisibleTarget("editor_decrease", where)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertReachableCards(model: WearSurfaceModel, where: String, expectedCount: Int) {
    val cards = if (model.weighted) listOf("weight_card", "reps_card") else listOf("reps_card")
    cards.forEach { tag ->
        onNodeWithTag(tag).performScrollTo()
        waitForIdle()
        assertVisibleTarget(tag, "$where scrolled card")
        assertDeclaredTargetsAndOverlap(where, expectedCount)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertVisibleTarget(tag: String, where: String) {
    val screen = onNodeWithTag(TOUCH_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().wearVisibleBounds(screen)
    assertTrue(
        bounds.meetsMinimumSize(MIN_TARGET_DP),
        "$where: $tag declared=${bounds.declaredSizeDp}dp visible=${bounds.visibleSizeDp}dp; " +
            "ancestorClip=${bounds.ancestorClippedRect} screenClip=${bounds.screenClippedRect}",
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertDeclaredTargetsAndOverlap(where: String, expectedCount: Int) {
    val screen = onNodeWithTag(TOUCH_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
    assertEquals(expectedCount, nodes.size, "$where click target count")
    val measured = nodes.map { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged"
        tag to node.wearVisibleBounds(screen)
    }
    measured.forEach { (tag, bounds) ->
        assertTrue(
            bounds.declaredSizeDp.width >= MIN_TARGET_DP && bounds.declaredSizeDp.height >= MIN_TARGET_DP,
            "$where: $tag declared=${bounds.declaredSizeDp}dp is under ${MIN_TARGET_DP}dp",
        )
    }
    measured.forEachIndexed { index, (firstTag, first) ->
        measured.drop(index + 1).forEach { (secondTag, second) ->
            assertTrue(
                !first.ancestorClippedRect.overlaps(second.ancestorClippedRect),
                "$where: $firstTag ${first.ancestorClippedRect} overlaps $secondTag ${second.ancestorClippedRect}",
            )
        }
    }
}

private fun touchFixture(id: String): WearSurfaceModel = requireNotNull(SyntheticSurfaceFixtures.find(id))

private const val TOUCH_SCREEN_TAG = "touch_gate_screen"
private const val MIN_TARGET_DP = 48f
