// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.TrainingEditScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The training editor's frame — ED14's collapsed cards, the expanded card with no type toggle,
 * `.addex`, and Save enabled on an empty form. Russian; sheets and dialogs are out of model.
 */
internal class TrainingEditGoldenTest {

    /** ED14: both collapsed states — a `.plan-line` summary, and the no-plan italic. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editCollapsed(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingEditScreen(state = editState(), consume = {})
        }
    }

    /** The first card open: rows + `.setbar` on the lifted surface, and no type toggle. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editExpanded(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingEditScreen(
                state = editState().copy(expandedExerciseUuids = persistentSetOf("e1")),
                consume = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingEditScreen(
                state = editState().copy(name = "", exercises = persistentListOf()),
                consume = {},
            )
        }
    }

    private fun editState(): State = State
        .create(uuid = "training-uuid")
        .copy(
            mode = State.Mode.Edit(isCreate = false),
            isLoading = false,
            name = "Верх (с подтягиваниями)",
            exercises = listOf(
                TrainingExerciseItem(
                    exerciseUuid = "e1",
                    exerciseName = "Жим лёжа",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = listOf(
                        PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                        PlanSetUiModel(weight = 60.0, reps = 8, type = SetTypeUiModel.WORK),
                    ).toImmutableList(),
                    planSummary = "60×10 · 60×8",
                ),
                TrainingExerciseItem(
                    exerciseUuid = "e2",
                    exerciseName = "Подтягивания",
                    exerciseType = ExerciseTypeUiModel.WEIGHTLESS,
                    tags = persistentListOf(),
                    position = 1,
                    planSets = null,
                    planSummary = "",
                ),
            ).toImmutableList(),
        )
}
