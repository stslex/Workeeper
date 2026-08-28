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
 * The type toggle under ED5 — a picture is the whole gate: track `surfaceTier1`, selected half on
 * `surfaceTier2` + `slabtop`, no accent. Both selections, so the lift is not read as position.
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
