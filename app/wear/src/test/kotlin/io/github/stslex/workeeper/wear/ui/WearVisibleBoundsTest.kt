// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearVisibleBoundsTest {

    // One composition per Robolectric sandbox; every fixture replaces the same content slot.
    @Test
    fun measuresOnlyRealAncestorClipsAndTheDeclaredScreen() = runComposeUiTest {
        var fixture by mutableStateOf(BoundsFixture.VISIBLE)
        setContent {
            Box(Modifier.requiredSize(192.dp).testTag("screen")) {
                BoundsScene(fixture)
            }
        }

        BoundsFixture.entries.forEach { candidate ->
            fixture = candidate
            waitForIdle()
            val screen = onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
            val actual = onNodeWithTag("target").fetchSemanticsNode().wearVisibleBounds(screen)
            assertEquals(candidate.expectedWidthDp, actual.visibleSizeDp.width, 0.01f, "$candidate width")
            assertEquals(candidate.expectedHeightDp, actual.visibleSizeDp.height, 0.01f, "$candidate height")
            assertEquals(candidate.minimumMet, actual.meetsMinimumSize(48f), "$candidate minimum")
            if (candidate == BoundsFixture.CLIPPED || candidate == BoundsFixture.NESTED_CLIPPED) {
                assertTrue(actual.declaredSizeDp.height >= 48f, "$candidate must expose the old size-only blind spot")
                assertFalse(actual.meetsMinimumSize(48f), "$candidate must fail the visible target requirement")
            }
            if (candidate == BoundsFixture.OUTSIDE_SCREEN) {
                assertEquals(Rect.Zero, actual.screenClippedRect)
            }
        }
    }
}

private enum class BoundsFixture(
    val expectedWidthDp: Float,
    val expectedHeightDp: Float,
    val minimumMet: Boolean,
) {
    VISIBLE(48f, 48f, true),
    CLIPPED(40f, 40f, false),
    NESTED_CLIPPED(28f, 28f, false),
    NON_CLIPPING_PARENT(64f, 64f, true),
    SIBLING_VIEWPORT(64f, 64f, true),
    PARTIAL_SCREEN(32f, 32f, false),
    OUTSIDE_SCREEN(0f, 0f, false),
}

@Composable
private fun BoundsScene(fixture: BoundsFixture) {
    when (fixture) {
        BoundsFixture.VISIBLE -> BoundsTarget(sizeDp = 48)
        BoundsFixture.CLIPPED -> Box(
            Modifier.offset(20.dp, 20.dp).requiredSize(40.dp).clipToBounds(),
        ) { BoundsTarget() }
        BoundsFixture.NESTED_CLIPPED -> Box(
            Modifier.offset(20.dp, 20.dp).requiredSize(56.dp).clipToBounds(),
        ) {
            Box(Modifier.offset(28.dp, 28.dp).requiredSize(40.dp).clipToBounds()) { BoundsTarget() }
        }
        BoundsFixture.NON_CLIPPING_PARENT -> Box(
            Modifier.offset(20.dp, 20.dp).requiredSize(40.dp),
        ) { BoundsTarget() }
        BoundsFixture.SIBLING_VIEWPORT -> {
            Box(Modifier.requiredSize(40.dp).clipToBounds().testTag("sibling_viewport"))
            BoundsTarget(modifier = Modifier.offset(80.dp, 80.dp))
        }
        BoundsFixture.PARTIAL_SCREEN -> BoundsTarget(modifier = Modifier.offset(160.dp, 160.dp))
        BoundsFixture.OUTSIDE_SCREEN -> BoundsTarget(modifier = Modifier.offset(220.dp, 220.dp))
    }
}

@Composable
private fun BoundsTarget(modifier: Modifier = Modifier, sizeDp: Int = 64) {
    Box(modifier.requiredSize(sizeDp.dp).clickable(onClick = {}).testTag("target"))
}
