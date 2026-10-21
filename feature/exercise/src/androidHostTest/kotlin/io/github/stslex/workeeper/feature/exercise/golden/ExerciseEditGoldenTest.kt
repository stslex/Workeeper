// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
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
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise editor on §3.2's frame — the ED rulings' own gate. Rendered in Russian because the
 * drawn strings are what the spec writes, and an `en` frame cannot fail on them.
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
                        AppTagItem(uuid = "t1", name = "грудь"),
                        AppTagItem(uuid = "t2", name = "трицепс"),
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

    /** ED8's sheet CONTENT only — the `ModalBottomSheet` window is outside Paparazzi (§10.4). */
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
            tags = listOf(AppTagItem(uuid = "t1", name = "грудь")).toImmutableList(),
            availableTags = listOf(
                AppTagItem(uuid = "t1", name = "грудь"),
                AppTagItem(uuid = "t2", name = "трицепс"),
            ).toImmutableList(),
            adhocPlan = listOf(
                PlanSetUiModel(weight = 40.0, reps = 12, type = SetTypeUiModel.WARMUP),
                PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 50.0, reps = 6, type = SetTypeUiModel.FAILURE),
            ).toImmutableList(),
        )
}
