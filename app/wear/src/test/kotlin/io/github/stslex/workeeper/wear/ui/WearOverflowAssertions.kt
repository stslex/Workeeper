// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The shared body of gate G6 (spec §7), run once per locale by its two test classes: at font
 * scales 1.0 and the largest Wear OS offers, every rendered text node across all eleven kinds
 * and both editor surfaces reports no visual overflow — except the exercise name, which may
 * ellipsize at its second line.
 *
 * Red when the status row is given a fixed width narrower than its longest string.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertNoTextOverflowAcrossAllSurfaces() {
    val fixtures = SyntheticSurfaceFixtures.allKinds()
    val weighted = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
    var fontScale by mutableFloatStateOf(1.0f)
    var model by mutableStateOf(fixtures.first())
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            WearControllerScreen(state = model, onAction = {})
        }
    }

    listOf(1.0f, LARGEST_WEAR_FONT_SCALE).forEach { scale ->
        fontScale = scale
        fixtures.forEach { fixture ->
            model = fixture
            waitForIdle()
            assertNoOverflow(surface = "kind=${fixture.kind} scale=$scale")
        }
        listOf(
            "reps_card" to "reps editor",
            "weight_card" to "weight editor",
        ).forEach { (card, surface) ->
            model = weighted
            waitForIdle()
            onNodeWithTag(card).performClick()
            waitForIdle()
            assertNoOverflow(surface = "$surface scale=$scale")
            // Authority loss closes the editor, resetting for the next surface.
            model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
            waitForIdle()
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
