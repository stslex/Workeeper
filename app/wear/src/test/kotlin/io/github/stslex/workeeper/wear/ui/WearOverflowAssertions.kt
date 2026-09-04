// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The shared body of gate G6 (spec §7), run once per locale by its two test classes, as a
 * cross product of screens × font scales {1.0, largest} — smallest screen at the largest scale
 * is the combination that actually breaks, and a union of extremes would miss it. Every
 * rendered text node across all eleven kinds and both editor surfaces reports no visual
 * overflow — except the exercise name, which may ellipsize at its second line.
 *
 * Red when the status row is given a fixed width narrower than its longest string.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertNoTextOverflowAcrossAllSurfaces() {
    val fixtures = SyntheticSurfaceFixtures.allKinds()
    val weighted = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
    var screen by mutableStateOf(WearScreen.SMALL_ROUND)
    var fontScale by mutableFloatStateOf(1.0f)
    var model by mutableStateOf(fixtures.first())
    setContent {
        WearGateHost(screen = screen, fontScale = fontScale) {
            WearControllerScreen(state = model, onAction = {})
        }
    }

    WearScreen.entries.forEach { currentScreen ->
        listOf(1.0f, LARGEST_WEAR_FONT_SCALE).forEach { scale ->
            screen = currentScreen
            fontScale = scale
            fixtures.forEach { fixture ->
                model = fixture
                waitForIdle()
                assertNoOverflow(surface = "screen=$currentScreen kind=${fixture.kind} scale=$scale")
            }
            listOf(
                "reps_card" to "reps editor",
                "weight_card" to "weight editor",
            ).forEach { (card, surface) ->
                model = weighted
                waitForIdle()
                onNodeWithTag(card).performScrollTo().performClick()
                waitForIdle()
                assertNoOverflow(surface = "screen=$currentScreen $surface scale=$scale")
                // Authority loss closes the editor, resetting for the next surface.
                model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
                waitForIdle()
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertNoOverflow(surface: String) {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
        .fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$surface: no text nodes found — the surface never rendered")
    nodes.forEach { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged"
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        results.forEach { layout ->
            if (tag == "exercise_name") {
                assertTrue(
                    layout.lineCount <= 2,
                    "$surface: the exercise name may ellipsize at its second line, " +
                        "not overflow past it («$text», ${layout.lineCount} lines)",
                )
            } else {
                assertTrue(
                    !layout.hasVisualOverflow,
                    "$surface: «$tag» reports visual overflow rendering «$text»",
                )
            }
        }
    }
}

/**
 * The largest font scale Wear OS offers (display settings: small 0.94, normal 1.0,
 * medium 1.12, large 1.24).
 */
internal const val LARGEST_WEAR_FONT_SCALE = 1.24f
