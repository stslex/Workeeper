// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseEditScreen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise **editor**'s frame, and the two errors §26 "Save is never disabled" made reachable.
 *
 * **Why the errors are worth a photograph and the enabling is worth one too.** The handler tests
 * beside this file assert that `OnSaveClick` on a blank name sets `nameError` — they always did,
 * against a state no user could produce. What they cannot say is that the button which produces
 * that click is *tappable*, or that the flag reaches a red outline and a sentence. Both are
 * one-frame static facts, which is exactly the set a golden covers (§27), and neither had an
 * instrument before.
 *
 * So each error frame carries **two** claims at once: the field draws `status.error` at the heavier
 * weight with its reason underneath, and the Save button in the same frame is in its **enabled**
 * treatment — `accent` fill, not `surfaceTier4`. A regression to `enabled = name.isNotBlank()`
 * repaints that button and reddens these.
 *
 * `editClean` is the control. An error frame alone proves nothing about an outline weight or a
 * button fill, because there is nothing in the picture to compare either against.
 *
 * Out of model, per the harness KDoc: every dialog and sheet this screen can open renders in its
 * own window and stays on manual verification.
 */
internal class ExerciseEditGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editClean(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseEditScreen(state = editState(name = "Жим лёжа"), consume = {})
        }
    }

    /** Blank name — the branch that was unreachable until the button stopped being disabled. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editBlankNameError(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
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
        golden(testInfo, theme) {
            ExerciseEditScreen(
                state = editState(name = "Румынская тяга").copy(nameDuplicateError = true),
                consume = {},
            )
        }
    }

    /**
     * The create flow, which is the one place `PlanEditorBody` is composed INLINE — and therefore
     * the only frame in the app's whole golden set that can see the ruled set list (§26, "Sets:
     * add and remove move to the card's foot"; extraction §7.5).
     *
     * Four claims, all of them one-frame facts and no other instrument holding any of them:
     * the rows sit on a **card**; the foot is the drawn `.setbar` and no row carries a `✕`;
     * the `.tchip` carries a **letter** for a non-work type; and the values are in the **normal
     * colour** — `textPrimary`, not the `textTertiary` that drew a number the user typed as
     * "not yet entered".
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun editCreateWithInlinePlan(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseEditScreen(
                state = editState(name = "Жим лёжа").copy(
                    mode = State.Mode.Edit(isCreate = true),
                    adhocPlan = listOf(
                        PlanSetUiModel(weight = 40.0, reps = 12, type = SetTypeUiModel.WARMUP),
                        PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                        PlanSetUiModel(weight = 50.0, reps = 6, type = SetTypeUiModel.FAILURE),
                    ).toImmutableList(),
                ),
                consume = {},
            )
        }
    }

    private fun editState(name: String): State = State
        .create(uuid = "edit-uuid")
        .copy(
            mode = State.Mode.Edit(isCreate = false),
            isLoading = false,
            name = name,
            type = ExerciseTypeUiModel.WEIGHTED,
            description = "",
            tags = listOf(TagUiModel(uuid = "t1", name = "грудь")).toImmutableList(),
            availableTags = listOf(
                TagUiModel(uuid = "t1", name = "грудь"),
                TagUiModel(uuid = "t2", name = "трицепс"),
            ).toImmutableList(),
            adhocPlanSummaryLabel = "4 × 7×12",
        )
}
