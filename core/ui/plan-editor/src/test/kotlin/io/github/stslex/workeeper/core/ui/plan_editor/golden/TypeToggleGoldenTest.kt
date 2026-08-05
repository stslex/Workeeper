// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.TypeToggle
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The type toggle after ED5 — the one instrument that can say the accent is gone.
 *
 * The ruling is entirely colour and geometry, so a picture is the whole gate: track
 * `surfaceTier1`, the selected half lifted onto `surfaceTier2` + `slabtop`, labels `textPrimary`
 * on the selection and `textSecondary` beside it. What it replaces — an `accent` outline, an
 * `accentTintedBackground` fill and `accentTintedForeground` text on a 48dp outlined box — is
 * three colours and one height away from that, and every one of the four differences is a static
 * frame.
 *
 * **Two selections, not one.** A single image cannot distinguish "the selected half is lifted"
 * from "the left half is lifted": both are true of the weighted picture. Moving the selection is
 * what makes the lift a function of the argument rather than of position, and it is the same
 * reason `SegmentedControlGoldenTest` photographs a middle segment.
 *
 * Rendered in **Russian**, where «С весом» / «Без веса» are what the drawing writes and what a
 * user reads; the `en` frame cannot fail on a string it never shows.
 */
internal class TypeToggleGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun typeWeighted(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            Toggle(selected = ExerciseTypeUiModel.WEIGHTED)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun typeWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            Toggle(selected = ExerciseTypeUiModel.WEIGHTLESS)
        }
    }
}

@Composable
private fun Toggle(selected: ExerciseTypeUiModel) {
    TypeToggle(
        modifier = Modifier.padding(SUBJECT_INSET),
        selected = selected,
        onSelect = {},
    )
}

/** The screen edge the host puts around it, so the track is not flush to the canvas. */
private val SUBJECT_INSET = 16.dp
