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
 * overflow — except the exercise name, which may ellipsize at its second line, and the
 * disabled completion label in exactly one cell, [ELLIPSIS_CELL], where the accepted residual
 * is asserted positively rather than skipped.
 *
 * Red when the status row is given a fixed width narrower than its longest string.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertNoTextOverflowAcrossAllSurfaces(locale: GateLocale) {
    val fixtures = SyntheticSurfaceFixtures.allKinds()
    val weighted = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
    val unsetWeight = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.UNSET_WEIGHT))
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
            val cell = GateCell(locale, currentScreen, scale)
            fixtures.forEach { fixture ->
                model = fixture
                waitForIdle()
                assertNoOverflow(cell, surface = "screen=$currentScreen kind=${fixture.kind} scale=$scale")
            }
            // Both editors, opened from a full weight AND from an absent one: the editor
            // spells the absence out in full («Not set»), where the card draws only «—».
            editorCases.forEach { (source, card, surface) ->
                model = source
                waitForIdle()
                onNodeWithTag(card).performScrollTo().performClick()
                waitForIdle()
                assertNoOverflow(cell, surface = "screen=$currentScreen $surface scale=$scale")
                // Authority loss closes the editor, resetting for the next surface.
                model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
                waitForIdle()
            }
        }
    }
}

/** The locale a G6 test class renders under. Each class states it; the shared body never infers it. */
internal enum class GateLocale { EN, RU }

/** One cell of the G6 cross product: locale × screen × font scale. */
internal data class GateCell(val locale: GateLocale, val screen: WearScreen, val scale: Float)

/**
 * The one cell where the disabled completion label is accepted to ellipsize (bottom-band
 * rebudget — copy decision): ru «Отключено» exceeds the Medium EdgeButton's 159 px content lane
 * on the small round screen at the largest font scale, and fits with margin in every other cell.
 */
private val ELLIPSIS_CELL = GateCell(GateLocale.RU, WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE)

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertNoOverflow(cell: GateCell, surface: String) {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
        .fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$surface: no text nodes found — the surface never rendered")
    nodes.forEach { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged"
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val disabledCompletion = tag == "complete_set" && node.config.contains(SemanticsProperties.Disabled)
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        results.forEach { layout ->
            if (tag == "exercise_name") {
                assertTrue(
                    layout.lineCount <= 2,
                    "$surface: the exercise name may ellipsize at its second line, " +
                        "not overflow past it («$text», ${layout.lineCount} lines)",
                )
            } else if (disabledCompletion && cell == ELLIPSIS_CELL) {
                // The disabled completion label in the one accepted cell. The in-lane ellipsis
                // there is a decided residual (bottom-band rebudget — copy decision): the full
                // text stays in the button's content description, and G10 forbids a mid-word
                // split. Keyed on the node's `Disabled` semantics so the enabled state stays under
                // the strict branch below, and on the single cell so every other disabled cell
                // does too. Not a skip: the residual is asserted as accepted — exactly one line,
                // ellipsized — so if it ever disappears (a shorter string, a wider lane) this
                // reds and the exemption is removed rather than left exempting nothing.
                assertTrue(
                    layout.lineCount == 1 && layout.hasVisualOverflow,
                    "$surface: the disabled completion label is accepted to ellipsize on exactly " +
                        "one line in this cell only («$text», ${layout.lineCount} line(s), " +
                        "overflow=${layout.hasVisualOverflow}) — if the residual is gone, " +
                        "remove the exemption",
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
