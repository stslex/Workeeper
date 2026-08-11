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
 * The shared plan-editor body — the set list §26 "Sets: add and remove move to the card's foot"
 * rules, and its only visual gate.
 *
 * **Why it lives here and not in a host.** Both remaining editors compose this body and neither can
 * photograph it: the exercise editor's whole-screen frame scrolls it off the bottom of a single
 * Paparazzi viewport, and the full-screen route's module has no goldens at all. §24.2 item 6 names
 * this component as the one no mockup section draws; that is a reason to gate it more carefully,
 * not less.
 *
 * Four claims, every one of them a one-frame static fact and no other instrument holding any of
 * them:
 *
 *  - the rows sit on a **card** (`surfaceTier1`, `Radius.medium`), so the foot's top rule has
 *    something to be the foot of;
 *  - the foot is the drawn **`.setbar`** — two mono uppercase halves split by a hairline — and the
 *    per-row `✕` is **gone**;
 *  - the `.tchip` carries a **letter** for warmup and failure and the dot for work;
 *  - the values are in the **normal colour**. `textPrimary`, not `textTertiary`, which draws a
 *    number the user has typed as "not yet entered". That one is a colour
 *    swap between two roles that are genuinely different values, so a picture can see it — unlike
 *    the `textDim`/`textTertiary` alias §27 records as ungatable by any golden.
 *
 * [emptyDraft] is the control and the second half of the foot's contract: with no rows, «− подход»
 * is disabled at the drawn `opacity:.35` and the hint takes the card. Photographed in **Russian**,
 * because the empty hint and both foot labels are the strings a Russian user actually sees and the
 * default `en` frame cannot fail on them.
 */
internal class PlanEditorBodyGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun weightedDraft(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Body(isWeighted = true) }
    }

    /** B11's half: a weightless exercise drops the weight column and keeps everything else. */
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
        // The host owns the scroll on both surfaces this body ships on; a capped inner scroller
        // inside a SHRINK canvas would photograph the cap rather than the list.
        scrollable = false,
    )
}

/** The screen edge the two hosts put around it, so the card is not flush to the canvas. */
private val SUBJECT_INSET = 16.dp
