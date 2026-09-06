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
import java.io.File

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
 * the resulting semantics — as text, a content description, or a state description.
 *
 * The walk itself is checked against an independent parse of the resource XML — not a floor.
 * A floor rots: at forty strings a walk returning thirty still clears "at least thirty", the
 * very under-coverage this gate exists to prevent.
 *
 * KNOWN LIMIT, measured rather than assumed. The six substituted ids (`set_progress`,
 * `weight_value`, `decrease_reps`, `increase_reps`, `decrease_weight`, `increase_weight`)
 * cannot be matched whole, because their arguments are substituted at render time; they are
 * matched on their literal segments instead. That is a substring test, so in principle one
 * id could be credited to text another id rendered — `weight_value`'s only literal is «kg»,
 * two characters. Probed directly rather than reasoned about: each of the six had its sole
 * renderer neutralised in turn, with the other five verified untouched on every pass, and
 * every one of the six turned this gate red naming its own id. No false positive exists
 * today. What would create one is a new string sharing every literal segment of an existing
 * substituted id — if that lands, this matcher must become exact.
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

        // The walk must cover exactly what the resource file declares. A floor would rot:
        // at forty strings a walk returning thirty still clears "at least thirty", which is
        // the under-coverage this gate exists to prevent, reintroduced by its own guard.
        val declared = declaredStringNames()
        assertTrue(
            declared.size >= MINIMUM_DECLARED_IDS,
            "Parsed only ${declared.size} <string> declaration(s) from $STRINGS_XML; the " +
                "independent parse must not itself be reading nothing.",
        )
        val missedByWalk = declared - ids.keys
        val unknownToFile = ids.keys - declared
        assertTrue(
            missedByWalk.isEmpty() && unknownToFile.isEmpty(),
            "The resource walk and $STRINGS_XML disagree — the walk must cover exactly what " +
                "the file declares.\n" +
                "  declared ${declared.size}, reflected ${ids.size}\n" +
                "  declared but NOT reflected (the walk is short): ${missedByWalk.sorted()}\n" +
                "  reflected but NOT declared here: ${unknownToFile.sorted()}",
        )
        val staleAllowlist = UNREACHABLE_BY_DESIGN.keys - ids.keys
        assertTrue(
            staleAllowlist.isEmpty(),
            "The allowlist names string id(s) that no longer exist: $staleAllowlist",
        )

        // TRIPWIRE. This gate matches format strings on their literal segments, which is a
        // substring test, and a one-character orphan was measured slipping through it: «x» was
        // credited to «Exercise». The blind spot is exactly where the gate matters most — a
        // NEWLY ADDED string with no fixture — so the file is stopped from entering it. Exact
        // attribution (recording which ids actually resolve during composition) is the real
        // fix and is deliberately separate work; this holds the line until then.
        val tooShort = declared.associateWith { name ->
            resources.getString(requireNotNull(ids[name]))
        }.filterValues { it.length < MINIMUM_VALUE_LENGTH }
        assertTrue(
            tooShort.isEmpty(),
            "String value(s) shorter than $MINIMUM_VALUE_LENGTH characters cannot be told " +
                "apart from a substring of unrelated text, so this gate could credit them to " +
                "a string that merely contains them: $tooShort. Lengthen the value, or land " +
                "exact attribution first.",
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

    /**
     * The `<string>` names in the module's own resource file, parsed straight from XML so the
     * expected set never comes from the same place as the actual one.
     */
    private fun declaredStringNames(): Set<String> {
        val file = File(STRINGS_XML)
        assertTrue(file.isFile, "Expected the string resources at ${file.absolutePath}")
        return STRING_DECLARATION.findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {

        val FORMAT_SPECIFIER = Regex("%\\d+\\$[a-zA-Z]|%[a-zA-Z]")

        const val STRINGS_XML = "src/main/res/values/strings.xml"

        val STRING_DECLARATION = Regex("""<string name="([^"]+)"""")

        /** A floor under the PARSE, so a regex that stops matching cannot pass over nothing. */
        const val MINIMUM_DECLARED_IDS = 30

        /**
         * Shortest value this gate can distinguish from an accident. The shortest real value
         * today is «Вес» at 3 characters, so the margin is exactly zero — deliberately: any
         * shorter value is the case the tripwire exists to stop.
         */
        const val MINIMUM_VALUE_LENGTH = 3

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
