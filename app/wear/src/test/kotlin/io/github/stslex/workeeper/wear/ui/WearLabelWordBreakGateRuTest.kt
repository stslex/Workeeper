// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.ui.WearLabelWordBreak.assertNoButtonLabelSplitsAWord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Gate G10, Russian half — no button label breaks inside a word. Both locales are needed
 * because the labels differ; see [WearLabelWordBreak] for the invariant and why it is
 * enforced here rather than by a layout flag.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearLabelWordBreakGateRuTest {

    @Test
    @DisplayName("no Russian rendered text splits a word, on both screens at both font scales")
    fun noButtonLabelBreaksInsideAWord() = runComposeUiTest {
        assertNoButtonLabelSplitsAWord()
    }
}
