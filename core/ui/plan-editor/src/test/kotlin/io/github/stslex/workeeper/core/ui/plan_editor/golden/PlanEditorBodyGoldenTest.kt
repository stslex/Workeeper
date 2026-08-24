// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.golden

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The shared plan-editor body's only visual gate — neither host can photograph it (v3-editors.md
 * ED2). Rows on a card, the `.setbar` foot, the `.tchip` letter, values in `textPrimary`.
 */
internal class PlanEditorBodyGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun weightedDraft(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Body(isWeighted = true) }
    }

    /** A weightless exercise drops the weight column and keeps everything else. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun weightlessDraft(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Body(isWeighted = false) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun emptyDraft(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            PlanEditorBody(
                modifier = Modifier.padding(SUBJECT_INSET),
                draft = persistentListOf(),
                isWeighted = true,
                onAction = {},
                scrollable = false,
            )
        }
    }
}

@Composable
private fun Body(isWeighted: Boolean) {
    PlanEditorBody(
        modifier = Modifier.padding(SUBJECT_INSET),
        draft = listOf(
            PlanSetUiModel(weight = 40.0, reps = 12, type = SetTypeUiModel.WARMUP),
            PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
            PlanSetUiModel(weight = 50.0, reps = 6, type = SetTypeUiModel.FAILURE),
        ).toImmutableList(),
        isWeighted = isWeighted,
        onAction = {},
        // GUARD: hosts own the scroll; a capped inner scroller in a SHRINK canvas shoots the cap.
        scrollable = false,
    )
}

/** The screen edge the two hosts put around it, so the card is not flush to the canvas. */
private val SUBJECT_INSET = 16.dp
