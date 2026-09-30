// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The invariant behind two round-4/5 decisions: where the value card shrinks what it *draws*
 * to fit a 192dp screen, it must lose nothing it *says*. The unit «kg» and the spelled-out
 * absence «Not set» both left the drawn value; both must still reach TalkBack through the
 * value's content description.
 *
 * Red when either content description is dropped and only the glyph remains.
 *
 * Default graphics mode on purpose: this asserts semantics-tree contents, not geometry.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearUnsetWeightGateTest {

    @Test
    @DisplayName("the shortened weight value still speaks its unit and its absence in full")
    fun shortenedWeightValueKeepsItsSpokenMeaning() = runComposeUiTest {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val unsetWords = resources.getString(R.string.weight_unset)
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(fixture(SyntheticSurfaceFixtures.UNSET_WEIGHT))
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current

            model = fixture(SyntheticSurfaceFixtures.UNSET_WEIGHT)
            waitForIdle()
            val unset = onNodeWithTag("weight_value", useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(
                EM_DASH,
                unset.config[SemanticsProperties.Text].joinToString { it.text },
                "screen=$current: an absent weight draws the em-dash mark",
            )
            assertEquals(
                listOf(unsetWords),
                unset.config.getOrNull(SemanticsProperties.ContentDescription),
                "screen=$current: …and still says «$unsetWords» in full",
            )

            model = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            waitForIdle()
            val set = onNodeWithTag("weight_value", useUnmergedTree = true).fetchSemanticsNode()
            val drawn = set.config[SemanticsProperties.Text].joinToString { it.text }
            val spoken = set.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue(
                EM_DASH !in drawn,
                "screen=$current: a present weight draws its number, not the absence mark",
            )
            assertEquals(
                listOf(resources.getString(R.string.weight_value, drawn)),
                spoken,
                "screen=$current: …and speaks that number with its unit",
            )
        }
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {
        const val EM_DASH = "—"
    }
}
