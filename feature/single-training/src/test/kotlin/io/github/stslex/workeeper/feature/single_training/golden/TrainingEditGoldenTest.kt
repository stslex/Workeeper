// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.TrainingEditScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The training editor's frame — this module's first golden.
 *
 * Three rulings land on one static frame of this screen and have no other instrument
 * between them:
 *
 *  - **`.addex` is the add action, and it is not in the section header.** A full-width dashed
 *    block below the list, not a small tertiary button above it — a different component in a
 *    different place, so a glyph swap in the header would not satisfy it (§26; extraction §7.6).
 *  - **One drag handle, not two arrows.** Two identical marks meaning opposite directions are not
 *    a control (§26, "Reorder is long-press drag"); what a frame can see is the count.
 *  - **Save is enabled with an empty name**, which is what makes the blank-name error reachable at
 *    all (§26, "Save is never disabled") — and on THIS screen a save predicate would hide a second
 *    branch too, the empty-exercise-list snackbar.
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

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editWithExercises(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingEditScreen(state = editState(), consume = {})
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
                exercise(uuid = "e1", name = "Жим лёжа", position = 0, plan = "60×10 · 60×8"),
                exercise(uuid = "e2", name = "Подтягивания", position = 1, plan = ""),
            ).toImmutableList(),
        )

    private fun exercise(
        uuid: String,
        name: String,
        position: Int,
        plan: String,
    ): TrainingExerciseItem = TrainingExerciseItem(
        exerciseUuid = uuid,
        exerciseName = name,
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        tags = persistentListOf(),
        position = position,
        planSets = null,
        planSummary = plan,
    )
}
