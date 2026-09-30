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
import java.util.Locale

/**
 * G6 checks every surface at both screen sizes and font scales in each locale. Only the
 * compact exercise context may abbreviate; the full name in details and all blocking reasons
 * must remain unellipsized. See the Wear controller redesign spec §7.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertNoTextOverflowAcrossAllSurfaces(locale: GateLocale) {
    val selectedLocale = Locale.forLanguageTag(locale.name.lowercase(Locale.ROOT))
    val fixtures = SyntheticSurfaceFixtures.allKinds().map { it.copy(selectedLocale = selectedLocale) }
    val weighted = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        .copy(selectedLocale = selectedLocale)
    val unsetWeight = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.UNSET_WEIGHT))
        .copy(selectedLocale = selectedLocale)
    val editorCases = listOf(
        Triple(weighted, "reps_card", "reps editor"),
        Triple(weighted, "weight_card", "weight editor"),
        Triple(unsetWeight, "weight_card", "weight editor (absent weight)"),
        Triple(unsetWeight, "reps_card", "reps editor (absent weight)"),
    )
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
                assertNoOverflow(surface = "locale=$locale screen=$currentScreen kind=${fixture.kind} scale=$scale")
            }
            // Both editors, opened from a full weight AND from an absent one: the editor
            // spells the absence out in full («Not set»), where the card draws only «—».
            editorCases.forEach { (source, card, surface) ->
                model = source
                waitForIdle()
                onNodeWithTag(card).performScrollTo().performClick()
                waitForIdle()
                assertNoOverflow(surface = "locale=$locale screen=$currentScreen $surface scale=$scale")
                // Authority loss closes the editor, resetting for the next surface.
                model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
                waitForIdle()
            }
        }
    }
}

/** The locale a G6 test class renders under. Each class states it; the shared body never infers it. */
internal enum class GateLocale { EN, RU }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertNoOverflow(surface: String) {
    val nodes = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$surface: no text nodes found — the surface never rendered")
    nodes.forEach { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged"
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        assertTrue(results.isNotEmpty(), "$surface: «$tag» exposes no text layout for «$text»")
        results.forEach { layout ->
            if (tag == "exercise_context") {
                assertTrue(
                    layout.lineCount <= 2,
                    "$surface: compact exercise context must stay within two lines («$text»)",
                )
            } else {
                assertTrue(!layout.hasVisualOverflow, "$surface: «$tag» reports visual overflow rendering «$text»")
                repeat(layout.lineCount) { line ->
                    assertTrue(
                        !layout.isLineEllipsized(line),
                        "$surface: «$tag» ellipsizes line=$line rendering «$text»",
                    )
                }
            }
        }
    }
}

/**
 * The largest font scale Wear OS offers (display settings: small 0.94, normal 1.0,
 * medium 1.12, large 1.24).
 */
internal const val LARGEST_WEAR_FONT_SCALE = 1.24f
