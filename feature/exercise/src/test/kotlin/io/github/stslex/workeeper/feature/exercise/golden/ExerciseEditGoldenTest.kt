// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseEditScreen
import io.github.stslex.workeeper.feature.exercise.ui.components.PlanInfoSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise editor on §3.2's frame — the screen S3 rebuilt, so these frames are the ruling's
 * own gate: the plan edited inline in BOTH modes (ED1), the section rhythm (ED3), no placeholder
 * echoing a label (ED4), the monochrome toggle (ED5), no thumb in the bar (ED6), the `(i)` on the
 * plan head (ED8), and an empty create draft (ED13).
 *
 * [createEmpty] is ED13's own photograph: no seeded sets, the card's empty hint, «− подход»
 * disabled at the drawn `opacity:.35`, and the bar title standing in DIM because the record has
 * no name yet. [editWeighted] holds ED1 for an EXISTING exercise: the plan drawn as rows on
 * this screen, not as a summary line with a button off to another one. [editWeightless] is
 * B11's half: the weight column gone, everything else in place.
 *
 * The two error frames are here because nothing else holds their claims: the field draws
 * `status.error` at the heavier weight with its reason underneath, and Save in the same frame
 * is in its **enabled** treatment (§7.3).
 *
 * Whole-screen frames render in **Russian**: the drawn strings — «Новое упражнение»,
 * «ПЛАН ПО УМОЛЧАНИЮ», «N из 10», «Отмена · Сохранить» — are what §3.2 writes, and an `en`
 * frame cannot fail on them.
 */
internal class ExerciseEditGoldenTest {

    /** ED13, ED4, and the dim fallback title in one frame. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun createEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            ExerciseEditScreen(
                state = State.create(uuid = null).copy(
                    isLoading = false,
                    availableTags = listOf(
                        TagUiModel(uuid = "t1", name = "грудь"),
                        TagUiModel(uuid = "t2", name = "трицепс"),
                    ).toImmutableList(),
                ),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editWeighted(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            ExerciseEditScreen(state = editState(name = "Жим лёжа"), consume = {})
        }
    }

    /** B11's half: a weightless exercise drops the weight column and keeps everything else. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            ExerciseEditScreen(
                state = editState(name = "Подтягивания").copy(
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    adhocPlan = listOf(
                        PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK),
                        PlanSetUiModel(weight = null, reps = 10, type = SetTypeUiModel.WORK),
                        PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.FAILURE),
                    ).toImmutableList(),
                ),
                consume = {},
            )
        }
    }

    /** Blank name — the branch that was unreachable until the button stopped being disabled. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editBlankNameError(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            ExerciseEditScreen(
                state = editState(name = "").copy(nameError = true),
                consume = {},
            )
        }
    }

    /** Duplicate name — reachable already, but it never had a picture of its own. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editDuplicateNameError(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            ExerciseEditScreen(
                state = editState(name = "Румынская тяга").copy(nameDuplicateError = true),
                consume = {},
            )
        }
    }

    /**
     * ED8's sheet CONTENT on the sheet tier — the `ModalBottomSheet` window itself is out of
     * Paparazzi's one-window model (§10.4). In Russian because the body copy is the ruling's
     * own verbatim text, and this is the only instrument that reads it at all.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetPlanInfo(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, locale = LOCALE_RU, surface = { AppUi.colors.surfaceTier3 }) {
            PlanInfoSheetContent(consume = {})
        }
    }

    private fun editState(name: String): State = State
        .create(uuid = "edit-uuid")
        .copy(
            mode = State.Mode.Edit(isCreate = false),
            isLoading = false,
            name = name,
            type = ExerciseTypeUiModel.WEIGHTED,
            description = "Локти чуть согнуты, без рывка.",
            tags = listOf(TagUiModel(uuid = "t1", name = "грудь")).toImmutableList(),
            availableTags = listOf(
                TagUiModel(uuid = "t1", name = "грудь"),
                TagUiModel(uuid = "t2", name = "трицепс"),
            ).toImmutableList(),
            adhocPlan = listOf(
                PlanSetUiModel(weight = 40.0, reps = 12, type = SetTypeUiModel.WARMUP),
                PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 50.0, reps = 6, type = SetTypeUiModel.FAILURE),
            ).toImmutableList(),
        )
}
