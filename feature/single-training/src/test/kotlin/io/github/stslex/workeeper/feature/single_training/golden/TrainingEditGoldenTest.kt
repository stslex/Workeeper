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
 * The training editor's frame — this module's first golden.
 *
 * The rulings that land on a static frame of this screen and have no other instrument:
 *
 *  - **Each exercise is a card, COLLAPSED by default (ED14)** — ordinal, type glyph, name,
 *    `.plan-line` summary, drag handle, `✕`, and **no plan rows** until a card is opened.
 *    [editCollapsed] holds both collapsed states at once: one card with a summary, one with
 *    the italic «плана пока нет».
 *  - **The expanded card carries the plan body and NO TYPE TOGGLE** ([editExpanded]) — the
 *    rows, the `.setbar` foot and the lifted `.card.open` surface, with `onTypeChange = null`
 *    keeping the toggle out: type belongs to the exercise, not to a training-scoped editor.
 *  - **`.addex` is the add action, and it is not in the section header** (§26; extraction §7.6).
 *  - **One drag handle, not two arrows** (§26, "Reorder is long-press drag").
 *  - **Save is enabled with an empty name** (§26, "Save is never disabled") — and on THIS screen
 *    a save predicate would hide a second branch too, the empty-exercise-list snackbar.
 *
 * [editEmpty] is not a decoration: it is the only frame in which `.addex` stands alone, which is
 * the state a user starting a training is actually in, and it is the frame that shows Save enabled
 * with a blank name and an empty list at once — both of a save predicate's conjuncts false.
 *
 * Russian, deliberately. The strings this screen carries — the `.addex` label, the section
 * count, the row's «Изменить план» — are ones the shipped app renders in Russian, and the
 * harness's default `en` frame cannot fail on a Russian-only defect. The set-type marks are the
 * standing witness for why that matters (`SetTypeMarkGoldenTest`).
 *
 * Out of model, per the harness KDoc: the exercise-picker sheet and every dialog this screen opens
 * render in their own windows and stay on manual verification.
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
