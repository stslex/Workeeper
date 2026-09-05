// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G4 of the Wear controller redesign spec §7, at both [WearScreen] extremes: in every
 * state where `completeEnabled` is false and the bottom-edge button is present, the semantics
 * tree contains the disabled label — disabled is never signalled by colour alone. The enabled
 * state proves the other direction, so the label is a signal and not a constant. Default
 * graphics mode on purpose: this gate asserts semantics-tree contents, not geometry.
 *
 * Red when the label is removed and only the fill changes.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearDisabledLabelGateTest {

    @Test
    @DisplayName("a disabled completion always carries the disabled word, on both screen extremes")
    fun disabledCompletionAlwaysCarriesTheDisabledWord() = runComposeUiTest {
        val disabledWord = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.control_disabled)
        val disabledStates = listOf(
            "REFRESH_REQUIRED" to fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED),
            "DISCONNECTED" to fixture(SyntheticSurfaceFixtures.DISCONNECTED),
            "ACTIVE with an invalid draft" to
                fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY).copy(completeEnabled = false),
        )
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(disabledStates.first().second)
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            disabledStates.forEach { (label, state) ->
                model = state
                waitForIdle()
                onNodeWithTag("complete_set").assertExists()
                val word = onNodeWithTag("complete_unavailable", useUnmergedTree = true)
                    .assertExists()
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .joinToString { it.text }
                assertEquals(
                    disabledWord,
                    word,
                    "screen=$current $label: the disabled completion must carry " +
                        "«$disabledWord» beneath its label",
                )
            }

            model = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            waitForIdle()
            onNodeWithTag("complete_set").assertExists()
            onNodeWithTag("complete_unavailable", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))
}
