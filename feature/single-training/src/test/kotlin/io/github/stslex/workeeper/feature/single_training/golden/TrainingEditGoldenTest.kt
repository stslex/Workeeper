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
 * The training editor's frame — this module's first golden, and the editors stage is why.
 *
 * Three of this stage's rulings land on one static frame of this screen and had no instrument
 * between them:
 *
 *  - **`.addex`** replaces the `AppButton.Tertiary` + `Icons.Default.Add` pair that used to sit in
 *    the section header. Not a glyph swap — a different component in a different place, full width
 *    below the list rather than a small button above it (§26; extraction §7.6).
 *  - **One drag handle, not two arrows.** The row drew `Icons.Default.DragHandle` **twice**, once
 *    for up and once for down, which is two identical marks and therefore not a control (§26,
 *    "Reorder is long-press drag"). What a frame can see is that there is now one.
 *  - **Save is enabled with an empty name**, which is what makes the blank-name error reachable at
 *    all (§26, "Save is never disabled") — and on THIS screen the disabled button hid a second
 *    branch too, the empty-exercise-list snackbar.
 *
 * [editEmpty] is not a decoration: it is the only frame in which `.addex` stands alone, which is
 * the state a user starting a training is actually in, and it is the frame that shows Save enabled
 * with both of the old `canSave` conjuncts false at once.
 *
 * Russian, deliberately. Every string this stage moved on this screen — the `.addex` label, the
 * section count, the row's «Изменить план» — is one the shipped app renders in Russian, and the
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
