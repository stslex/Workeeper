// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.PlanSetCard
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The set card's read-only host — the exercise read screen's plan. Paired with
 * `PlanEditorBodyGoldenTest` as the instrument for D-OPEN-6 (`v3-editors.md` ED2).
 */
internal class PlanSetCardReadOnlyGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyWeighted(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Card(isWeighted = true) }
    }

    /** A weightless exercise drops the weight column and keeps everything else. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Card(isWeighted = false) }
    }

    /** A 5-glyph weight keeps the full 26sp where the slot fits it (set-field-column-headers). */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyFiveGlyphWeight(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            PlanSetCard(
                modifier = Modifier.padding(SUBJECT_INSET),
                plan = persistentListOf(
                    PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
                ),
                isWeighted = true,
            )
        }
    }

    /** An exercise with no default plan — the read screen draws the section anyway. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            PlanSetCard(
                modifier = Modifier.padding(SUBJECT_INSET),
                plan = persistentListOf(),
                isWeighted = true,
            )
        }
    }
}

@Composable
private fun Card(isWeighted: Boolean) {
    PlanSetCard(
        modifier = Modifier.padding(SUBJECT_INSET),
        // The same four rows `PlanEditorBodyGoldenTest` photographs, so the pair is comparable.
        plan = listOf(
            PlanSetUiModel(weight = 40.0, reps = 12, type = SetTypeUiModel.WARMUP),
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 6, type = SetTypeUiModel.FAILURE),
        ).toImmutableList(),
        isWeighted = isWeighted,
    )
}

/** The screen edge the host puts around it, so the card is not flush to the canvas. */
private val SUBJECT_INSET = 16.dp
