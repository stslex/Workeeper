// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G11 — no rendered text overlaps other rendered text.
 *
 * The unavailability word, once moved outside the arc, printed on top of «Подход 2 из 5». A
 * screenshot caught it; no gate did. G1 has an overlap clause but compares only nodes carrying
 * a click action, and this was text over text.
 *
 * SEPARATE from G1 rather than folded into it, deliberately. G1's population is clickable
 * nodes and its subject is touch: a target must be 48dp and must not share space with another
 * target, or a tap is ambiguous. This gate's population is every rendered text node and its
 * subject is legibility: two strings drawn over each other are unreadable whether or not
 * either can be tapped. Same word, two different invariants — folding them would give one
 * gate two node sets, two rationales, and two unrelated reasons to go red.
 *
 * Bounds are the CLIPPED [androidx.compose.ui.semantics.SemanticsNode.boundsInRoot], which is
 * what the user actually sees: text scrolled out of the viewport is clipped away and cannot
 * collide with anything. Empty rects are skipped for that reason, and reported, so a pass over
 * nothing is visible rather than silent.
 *
 * The tightest surviving gap is reported too. Adjacency is normal and must stay green; a run
 * whose closest pair is comfortably far apart would mean the gate never saw a hard case.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearTextOverlapGateTest {

    @Test
    @DisplayName("no rendered text overlaps other rendered text, on both screens at both scales")
    fun noRenderedTextOverlapsOtherRenderedText() = runComposeUiTest {
        val fixtures = SyntheticSurfaceFixtures.allKinds()
        val weighted = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var fontScale by mutableFloatStateOf(1.0f)
        var model by mutableStateOf(fixtures.first())
        setContent {
            WearGateHost(screen = screen, fontScale = fontScale) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        val tally = Tally()
        WearScreen.entries.forEach { currentScreen ->
            listOf(1.0f, LARGEST_WEAR_FONT_SCALE).forEach { scale ->
                screen = currentScreen
                fontScale = scale
                val where = "screen=$currentScreen scale=$scale"
                fixtures.forEach { candidate ->
                    model = candidate
                    waitForIdle()
                    inspect("$where kind=${candidate.kind}", tally)
                }
                model = weighted
                waitForIdle()
                onNodeWithTag("reps_card").performScrollTo().performClick()
                waitForIdle()
                inspect("$where reps editor", tally)
                model = fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED)
                waitForIdle()
            }
        }

        println(
            "G11 text overlap: compared ${tally.pairs} visible text pair(s) " +
                "(${tally.skipped} clipped-away node(s) skipped), tightest gap " +
                "${"%.1f".format(tally.tightestGap)}dp, ${tally.overlaps.size} overlapping.",
        )
        assertTrue(
            tally.pairs >= MINIMUM_EXPECTED_PAIRS,
            "Only ${tally.pairs} text pair(s) were compared; the gate must not pass over an " +
                "empty or truncated set. Expected at least $MINIMUM_EXPECTED_PAIRS.",
        )
        assertTrue(
            tally.overlaps.isEmpty(),
            buildString {
                appendLine("${tally.overlaps.size} pair(s) of rendered text overlap:")
                tally.overlaps.distinct().forEach { appendLine("  - $it") }
            },
        )
    }

    private class Tally {
        var pairs = 0
        var skipped = 0
        var tightestGap = Float.MAX_VALUE
        val overlaps = mutableListOf<String>()
    }

    private fun ComposeUiTest.inspect(where: String, tally: Tally) {
        val nodes = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val visible = nodes.mapNotNull { node ->
            val rect = node.boundsInRoot
            val density = node.layoutInfo.density.density
            if (rect.width <= 0f || rect.height <= 0f) {
                tally.skipped++
                null
            } else {
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                val text = node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString { it.text }
                    .orEmpty()
                Drawn(tag ?: "untagged", text, rect, density)
            }
        }
        visible.forEachIndexed { index, first ->
            visible.drop(index + 1).forEach { second ->
                tally.pairs++
                if (first.rect.overlaps(second.rect)) {
                    tally.overlaps += "$where: «${first.name}» ${first.rect} " +
                        "overlaps «${second.name}» ${second.rect}"
                } else {
                    val gap = verticalGap(first.rect, second.rect) / first.density
                    if (gap >= 0f && gap < tally.tightestGap) tally.tightestGap = gap
                }
            }
        }
    }

    /** Vertical clearance between two horizontally-overlapping rects; negative when apart. */
    private fun verticalGap(a: Rect, b: Rect): Float {
        val horizontallyShared = a.left < b.right && b.left < a.right
        if (!horizontallyShared) return -1f
        return if (a.top >= b.bottom) a.top - b.bottom else b.top - a.bottom
    }

    private class Drawn(
        val tag: String,
        val text: String,
        val rect: Rect,
        val density: Float,
    ) {
        val name: String get() = if (tag != "untagged") tag else text
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {
        /** A floor under the walk, not a count of it. */
        const val MINIMUM_EXPECTED_PAIRS = 100
    }
}
