// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.thumb.AppExerciseThumb
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The `.thumb` and the two NEW exercise type marks (§26, "The image moves into the pushed top bar").
 *
 * Both marks and the thumb itself are **new**, which is the whole reason for the frame: a new
 * drawing has no earlier picture to be checked against, so the first recording IS the contract and
 * everything after it is a diff. Three claims one frame holds:
 *
 *  - the two marks are **different from each other** — a dumbbell and a figure — which is what a
 *    reader would otherwise take on trust from two path strings that both start with `M`;
 *  - **empty is dashed, filled is solid**, which is the only signal distinguishing "tap to choose a
 *    picture" from "tap to look at one";
 *  - both are **strokes**. They replace `Icons.Filled.FitnessCenter` and
 *    `Icons.Filled.AccessibilityNew` (B33(b)'s ×4 and ×3), and a filled glyph re-imported here
 *    would be a solid mass in a box drawn entirely in hairlines.
 *
 * Recorded on both themes because the dashed outline is `borderDefault`, a different value in each
 * (`#627587` / `#748396`), and because a dashed 1dp stroke at 2.75px/dp is the class the hairline
 * canary exists for.
 */
internal class ExerciseThumbGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun thumbStates(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Thumbs() }
    }
}

@Composable
private fun Thumbs() {
    Row(
        modifier = Modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Empty, weighted — the dumbbell in a dashed box.
        AppExerciseThumb(isWeighted = true, onClick = {})
        // Empty, weightless — the figure, same box.
        AppExerciseThumb(isWeighted = false, onClick = {})
        // Filled: solid border, no glyph. A camera here would read as "take another" (§26).
        AppExerciseThumb(isWeighted = true, onClick = {}) {
            Box(modifier = Modifier.fillMaxSize().background(AppUi.colors.surfaceTier4))
        }
    }
}
