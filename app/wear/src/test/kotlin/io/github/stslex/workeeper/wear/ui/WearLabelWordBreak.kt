// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
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
 * Gate G10 — no button label breaks inside a word.
 *
 * «Завершить» rendered as «Завершит» / «ь» on a 192dp screen: the app's primary action split
 * mid-word. G6 was green and right to be — the label allows two lines and neither overflowed,
 * so a break *inside* the allowance is invisible to it. The instrument was correct; the
 * invariant had never been stated.
 *
 * Compose has no layout flag for "never break a word": when a single word exceeds the line,
 * text layout falls back to breaking it at grapheme boundaries, because the alternative is
 * losing the text. So the invariant is enforced here instead, by reading the line boundaries
 * out of the [TextLayoutResult] and requiring every one of them to fall at a space or after a
 * hyphen. Once a label cannot break inside a word, anything that does not fit shows up as
 * overflow, which G6 already catches.
 *
 * Scope is deliberately WIDER than the button labels this was asked for: it walks every
 * rendered text node. Two reasons. The unavailability word had to move outside the arc to fit,
 * so a button-only walk would have stopped covering the very text that motivated the gate; and
 * a structural walk needs no tags, so text added later is covered without anyone remembering
 * to enrol it. Measured before adopting: 272 nodes, none splitting.
 *
 * The counts are asserted and reported, including how many wrapped LEGALLY at a space — a gate
 * that walks zero nodes, or that cannot tell a legal wrap from a split, passes silently.
 *
 * One consequence worth knowing: a fixture whose exercise name is a single overlong word would
 * red this, because such a word cannot be laid out without breaking. That would be a real
 * rendering finding to look at, not a false alarm.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
internal object WearLabelWordBreak {

    @OptIn(ExperimentalTestApi::class)
    fun ComposeUiTest.assertNoButtonLabelSplitsAWord() {
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

        var walked = 0
        var wrapped = 0
        val split = mutableListOf<String>()
        WearScreen.entries.forEach { currentScreen ->
            listOf(1.0f, LARGEST_WEAR_FONT_SCALE).forEach { scale ->
                screen = currentScreen
                fontScale = scale
                val tally = sweep(
                    where = "screen=$currentScreen scale=$scale",
                    fixtures = fixtures,
                    weighted = weighted,
                    split = split,
                    show = { model = it },
                )
                walked += tally.first
                wrapped += tally.second
            }
        }

        // `wrapped` separates the two cases this gate must tell apart: a label that took a
        // second line at a SPACE is legal and counted here, a label that split a WORD is not.
        println(
            "G10 label word-break: walked $walked text node(s), " +
                "$wrapped wrapped at a space, ${split.size} split mid-word.",
        )
        assertTrue(
            walked >= MINIMUM_EXPECTED_LABELS,
            "Only $walked text node(s) were walked; the gate must not pass over an empty or " +
                "truncated set. Expected at least $MINIMUM_EXPECTED_LABELS.",
        )
        assertTrue(
            split.isEmpty(),
            buildString {
                appendLine("${split.size} rendered text(s) break inside a word:")
                split.distinct().forEach { appendLine("  - $it") }
                appendLine(
                    "A label may wrap at a space or after a hyphen, never mid-word. Shorten " +
                        "the label, or move it to a content description and draw a glyph.",
                )
            },
        )
    }

    /** One screen/scale pass over every fixture and the editor; returns (inspected, wrapped). */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.sweep(
        where: String,
        fixtures: List<WearSurfaceModel>,
        weighted: WearSurfaceModel,
        split: MutableList<String>,
        show: (WearSurfaceModel) -> Unit,
    ): Pair<Int, Int> {
        var walked = 0
        var wrapped = 0
        fixtures.forEach { candidate ->
            show(candidate)
            waitForIdle()
            val tally = inspectLabels("$where kind=${candidate.kind}", split)
            walked += tally.first
            wrapped += tally.second
        }
        // The editor's own controls, which no fixture reaches without a tap.
        show(weighted)
        waitForIdle()
        onNodeWithTag("reps_card").performScrollTo().performClick()
        waitForIdle()
        val editor = inspectLabels("$where reps editor", split)
        show(fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
        waitForIdle()
        return (walked + editor.first) to (wrapped + editor.second)
    }

    /** Every rendered text node, checked; returns (inspected, legally wrapped). */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.inspectLabels(
        where: String,
        split: MutableList<String>,
    ): Pair<Int, Int> {
        var count = 0
        var wrapped = 0
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                val text = node.config[SemanticsProperties.Text].joinToString { it.text }
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { layout ->
                    count++
                    val detail = intraWordBreak(text, layout)
                    if (detail != null) {
                        split += "$where: «$text» $detail"
                    } else if (layout.lineCount > 1) {
                        wrapped++
                    }
                }
            }
        return count to wrapped
    }

    /**
     * The first line boundary that falls between two alphanumerics — a break the renderer made
     * inside a word, rather than at a space or after a hyphen.
     */
    private fun intraWordBreak(text: String, layout: TextLayoutResult): String? {
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            if (before.isLetterOrDigit() && after.isLetterOrDigit()) {
                return "splits as «${text.take(end)}» / «${text.drop(end)}»"
            }
        }
        return null
    }

    private fun SemanticsNode.textDescendants(): List<SemanticsNode> = buildList {
        if (config.contains(SemanticsActions.GetTextLayoutResult) &&
            config.getOrNull(SemanticsProperties.Text) != null
        ) {
            add(this@textDescendants)
        }
        children.forEach { addAll(it.textDescendants()) }
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    /** A floor under the walk, not a count of it. */
    private const val MINIMUM_EXPECTED_LABELS = 120
}
