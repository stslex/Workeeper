// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G9 — the instrument that closes a whole class of defect.
 *
 * Six defects across three review rounds of #284 were one defect wearing six faces: a string
 * that no fixture ever rendered, so no gate could see it (`field_error`, `weight_invalid`,
 * both name fallbacks, `weight_unset`, and the editor's unset layout). Each round's audit was
 * a careful manual enumeration, and each round the next enumeration found more. Enumeration by
 * hand is the defect; this walks the resources instead.
 *
 * It reflects over every id in `R.string`, renders the whole fixture set across both screen
 * extremes plus all four editor surfaces, and requires each id's value to appear somewhere in
 * the resulting semantics — as text, a content description, or a state description. Format
 * strings are matched on their literal segments, since their arguments are substituted at
 * render time.
 *
 * The walk itself is asserted non-empty: a gate that iterates zero ids passes silently, which
 * is precisely the failure it exists to prevent.
 *
 * Runs in the default locale. Coverage is structural — which fixture reaches which id — and
 * the id set is identical in `values/` and `values-ru/`; G6 is the gate that measures both
 * locales, because layout is what differs between them.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearStringCoverageGateTest {

    @Test
    @DisplayName("every Wear string resource is rendered by at least one fixture")
    fun everyStringResourceIsRenderedBySomeFixture() = runComposeUiTest {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val ids = R.string::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.name to it.getInt(null) }

        assertTrue(
            ids.size >= MINIMUM_EXPECTED_IDS,
            "The resource walk found only ${ids.size} string id(s); it must not silently " +
                "iterate an empty or truncated set. Expected at least $MINIMUM_EXPECTED_IDS.",
        )
        val staleAllowlist = UNREACHABLE_BY_DESIGN.keys - ids.keys
        assertTrue(
            staleAllowlist.isEmpty(),
            "The allowlist names string id(s) that no longer exist: $staleAllowlist",
        )

        val rendered = renderEveryFixture()
        assertTrue(
            rendered.isNotEmpty(),
            "No semantics were collected at all; the corpus must not be empty.",
        )

        val checked = ids.filterKeys { it !in UNREACHABLE_BY_DESIGN }
        val unreached = checked.filterNot { (_, id) ->
            literalSegments(resources.getString(id)).all { segment ->
                rendered.any { it.contains(segment) }
            }
        }

        // A gate that reports nothing is indistinguishable from a gate that ran nothing, so
        // the counts are emitted on the passing path too, into the suite's captured output.
        println(
            "G9 string coverage: walked ${ids.size} id(s) — ${checked.size} required, " +
                "${UNREACHABLE_BY_DESIGN.size} allowlisted by design, " +
                "${checked.size - unreached.size} reached, ${unreached.size} unreached.",
        )
        assertTrue(
            unreached.isEmpty(),
            buildString {
                appendLine("${unreached.size} string id(s) are rendered by NO fixture:")
                unreached.keys.sorted().forEach { name ->
                    appendLine("  - $name = «${resources.getString(checked.getValue(name))}»")
                }
                appendLine(
                    "Add a fixture that renders each, or — if one is genuinely unreachable " +
                        "from the controller — add it to UNREACHABLE_BY_DESIGN with its reason.",
                )
                appendLine(
                    "Walked ${ids.size} id(s): ${checked.size} required, " +
                        "${UNREACHABLE_BY_DESIGN.size} allowlisted, " +
                        "${checked.size - unreached.size} reached.",
                )
            },
        )
    }

    /**
     * Every fixture on both screen extremes, plus all four editor surfaces, reduced to the set
     * of strings their semantics expose.
     */
    private fun ComposeUiTest.renderEveryFixture(): Set<String> {
        val corpus = mutableSetOf<String>()
        val fixtures = SyntheticSurfaceFixtures.allKinds()
        val weighted = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
        val unsetWeight = fixture(SyntheticSurfaceFixtures.UNSET_WEIGHT)
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(fixtures.first())
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            fixtures.forEach { candidate ->
                model = candidate
                waitForIdle()
                corpus += collectSemantics()
            }
            listOf(
                weighted to "reps_card",
                weighted to "weight_card",
                unsetWeight to "weight_card",
                unsetWeight to "reps_card",
            ).forEach { (source, card) ->
                model = source
                waitForIdle()
                onNodeWithTag(card).performScrollTo().performClick()
                waitForIdle()
                corpus += collectSemantics()
                // Authority loss closes the editor, resetting for the next surface.
                model = fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED)
                waitForIdle()
            }
        }
        return corpus
    }

    private fun ComposeUiTest.collectSemantics(): Set<String> =
        onAllNodes(SemanticsMatcher("every node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                buildList {
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach(::add)
                    node.config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
                    node.config.getOrNull(SemanticsProperties.Error)?.let(::add)
                }
            }
            .toSet()

    /**
     * The literal parts of a resource value, with format specifiers removed: their arguments
     * are substituted at render time, so only the surrounding text can be matched.
     */
    private fun literalSegments(value: String): List<String> = value
        .split(FORMAT_SPECIFIER)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(value.trim()) }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {

        val FORMAT_SPECIFIER = Regex("%\\d+\\$[a-zA-Z]|%[a-zA-Z]")

        /**
         * A floor under the walk, not a count of it: reflection returning an empty or
         * truncated field set must fail rather than pass over nothing. Raise it only when
         * strings are added, never to accommodate a shrinking walk.
         */
        const val MINIMUM_EXPECTED_IDS = 30

        /**
         * Strings that no controller surface can reach, each with the reason it cannot. Both
         * are platform labels read outside the composition entirely; neither is copy the
         * controller could render if a fixture existed. Anything else belongs in a fixture.
         */
        val UNREACHABLE_BY_DESIGN = mapOf(
            "app_name" to "the launcher/manifest label, read by the system, never composed",
            "tile_name" to "the Tile service label in the tile picker; the Tile is raw " +
                "ProtoLayout and is out of this screen's scope entirely",
        )
    }
}
