// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G3 of the Wear controller redesign spec §7: all eleven [WearSurfaceKind] values render a
 * non-empty status string, and no two kinds produce the same one. The fixture list is the
 * production [SyntheticSurfaceFixtures.allKinds], so a kind cannot fall out of coverage without
 * this test noticing the missing key.
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
    @DisplayName("all eleven kinds render pairwise-distinct non-empty status strings")
    fun everyKindCarriesItsOwnNonEmptyStatusString() = runComposeUiTest {
        val fixtures = SyntheticSurfaceFixtures.allKinds()
        var model by mutableStateOf(fixtures.first())
        setContent { WearControllerScreen(state = model, onAction = {}) }

        val statusByKind = mutableMapOf<WearSurfaceKind, String>()
        fixtures.forEach { fixture ->
            model = fixture
            waitForIdle()
            val status = onNodeWithTag("status").fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString { it.text }
            assertTrue(status.isNotBlank(), "kind ${fixture.kind} rendered a blank status")
            statusByKind.merge(fixture.kind, status) { first, second ->
                assertEquals(
                    first,
                    second,
                    "kind ${fixture.kind} rendered two different statuses across fixtures",
                )
                first
            }
        }

        assertEquals(
            WearSurfaceKind.entries.toSet(),
            statusByKind.keys,
            "every kind must render a status; missing kinds never reached the assertion",
        )
        val shared = statusByKind.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }
        assertTrue(shared.isEmpty(), "kinds sharing one status string: $shared")
    }
}
