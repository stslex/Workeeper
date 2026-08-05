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
 * The type toggle under ED5 — the one instrument that can say there is no accent here.
 *
 * The ruling is entirely colour and geometry, so a picture is the whole gate: track
 * `surfaceTier1`, the selected half lifted onto `surfaceTier2` + `slabtop`, labels `textPrimary`
 * on the selection and `textTertiary` beside it. Four claims, each a single static frame, and no
 * accent among them — which is what the ruling is for.
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
