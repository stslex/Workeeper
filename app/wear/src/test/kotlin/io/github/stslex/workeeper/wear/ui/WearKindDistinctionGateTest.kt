// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G3 of the Wear controller redesign spec §7, at both [WearScreen] extremes: all eleven
 * [WearSurfaceKind] values render a non-empty status string, and no two kinds produce the same
 * one. The fixture list is the production [SyntheticSurfaceFixtures.allKinds], so a kind cannot
 * fall out of coverage without this test noticing the missing key. Default graphics mode on
 * purpose: this gate asserts semantics-tree contents, not geometry.
 *
 * Red when two kinds are pointed at the same string resource.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearKindDistinctionGateTest {

    @Test
    @DisplayName("all eleven kinds render pairwise-distinct statuses, on both screen extremes")
    fun everyKindCarriesItsOwnNonEmptyStatusString() = runComposeUiTest {
        val fixtures = SyntheticSurfaceFixtures.allKinds()
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(fixtures.first())
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            val statusByKind = mutableMapOf<WearSurfaceKind, String>()
            fixtures.forEach { fixture ->
                model = fixture
                waitForIdle()
                val node = onNodeWithTag("status").fetchSemanticsNode()
                val drawn = node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString { it.text }
                    .orEmpty()
                val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.joinToString()
                    .orEmpty()
                val status = drawn.ifBlank { spoken }
                assertTrue(
                    status.isNotBlank(),
                    "screen=$current kind=${fixture.kind} rendered a blank status",
                )
                // The ACTIVE surface drops the word from the DRAWING — the filled dot says it —
                // and keeps it spoken. Every other kind must still DRAW it: in a degraded state
                // the word is the whole message, and losing it there is the regression this
                // clause exists to catch.
                if (fixture.kind == WearSurfaceKind.ACTIVE) {
                    assertTrue(drawn.isBlank(), "screen=$current ACTIVE status must remain spoken-only")
                    assertTrue(spoken.isNotBlank(), "screen=$current ACTIVE status must remain accessible")
                    val expectedStatus = RuntimeEnvironment.getApplication().getString(fixture.statusCopy().resource)
                    assertEquals(expectedStatus, spoken, "screen=$current spoken ACTIVE status")
                    onAllNodes(hasText(expectedStatus), useUnmergedTree = true).assertCountEquals(0)
                } else {
                    assertTrue(
                        drawn.isNotBlank(),
                        "screen=$current kind=${fixture.kind} must draw its status word, " +
                            "not only speak it; drawn=«$drawn» spoken=«$spoken»",
                    )
                }
                if (fixture.totalSets != null) {
                    val scaleNode = onNodeWithTag("set_scale").fetchSemanticsNode()
                    val setDescription = scaleNode.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.joinToString()
                        .orEmpty()
                    val expectedProgress = RuntimeEnvironment.getApplication().getString(
                        R.string.set_progress,
                        fixture.setOrdinal,
                        fixture.totalSets,
                    )
                    assertEquals(expectedProgress, setDescription, "screen=$current spoken set progress")
                    onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                            (hasTestTag("set_scale") or hasAnyAncestor(hasTestTag("set_scale"))),
                        useUnmergedTree = true,
                    ).assertCountEquals(0)
                    onAllNodes(hasText(expectedProgress), useUnmergedTree = true).assertCountEquals(0)
                }
                statusByKind.merge(fixture.kind, status) { first, second ->
                    assertEquals(
                        first,
                        second,
                        "screen=$current kind=${fixture.kind} rendered two different " +
                            "statuses across fixtures",
                    )
                    first
                }
            }

            assertEquals(
                WearSurfaceKind.entries.toSet(),
                statusByKind.keys,
                "screen=$current: every kind must render a status; missing kinds never " +
                    "reached the assertion",
            )
            val shared = statusByKind.entries
                .groupBy({ it.value }, { it.key })
                .filterValues { it.size > 1 }
            assertTrue(shared.isEmpty(), "screen=$current kinds sharing one status string: $shared")
        }
    }
}
