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
 * The set card's **read-only host** — the exercise read screen's plan (`v3-editors.md` ED2).
 *
 * Its partner is `PlanEditorBodyGoldenTest`, and the pair is the instrument for D-OPEN-6: read and
 * edit are ruled **identical**, so the two suites photograph the same fixture on the same canvas
 * at the same width, and the only differences an eye should find between them are the ones the
 * read-only host is defined by — **no `.setbar` foot**, and a different empty line, because the
 * editor's hint names a control this host does not have. Same card, same tier, same rows, same
 * `.tchip`: a chip removal or a tier change would show here as a diff against a picture that is
 * meant to match.
 *
 * Rendered in **Russian**, like its partner: the empty line and the units are the strings a
 * Russian user reads, and an `en` frame cannot fail on them.
 */
internal class PlanSetCardReadOnlyGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyWeighted(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Card(isWeighted = true) }
    }

    /** B11's half: a weightless exercise drops the weight column and keeps everything else. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun readOnlyWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Card(isWeighted = false) }
    }

    /**
     * An exercise with no default plan. The read screen draws the section anyway — the head is
     * where the type is stated — so this branch is on screen, not a theoretical state.
     */
    /**
     * R3's pin (set-field-column-headers.md §8 fixture 4): a 5-glyph weight, "102.5". The
     * measured stepdown lets a value this long keep the full 26sp wherever its slot fits it
     * — which PlanSetCard's 101.8dp weight box does — where the old glyph-count heuristic
     * force-stepped it to 19sp. That behaviour change must not ship ungated; this is the
     * gate. New snapshot, first recording.
     */
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
        // The same four rows `PlanEditorBodyGoldenTest` photographs, so the two images are
        // comparable by eye and D-OPEN-6's "identical" is checkable rather than asserted.
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
