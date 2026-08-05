// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.golden

import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.TrainingDetailScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The training READ screen (`v3-editors.md` §3.3) — S5's frame, the screen ED9 rules and
 * nothing ever drew.
 *
 * The rulings that land on a static frame of this screen and have no other instrument:
 *
 *  - **Two forms on one screen (ED9)**: the exercises are CARDS — the collapsed form S4
 *    shipped in the editor, minus the drag handle and `✕`, plus the `.chev` — while the
 *    history below them is a full-bleed RULED LIST. [detailWithPlans] holds both at once,
 *    which is the frame the ruling is about.
 *  - **`Изменить` is on the dock, not in the `⋮` menu (ED10)** — the ghost 128dp beside the
 *    primary session button. Every frame shows it.
 *  - **The tags are one mono `.meta` line**, not chips (§3.3's `meta` row).
 *
 * [detailNoPlanExercise] pins the collapsed card's no-plan italic on READ — the same string
 * the editor's card renders, because the two heads are one composable.
 * [detailEmptyHistory] pins the ИСТОРИЯ head without a count over the «Сессий ещё не было»
 * line — the empty half S8 will extend, drawn here because the section renders in every state.
 *
 * Russian, deliberately — the section heads, the dock labels and the no-plan italic are all
 * strings the shipped app renders in Russian (`TrainingEditGoldenTest`'s own reasoning).
 *
 * Out of model, per the harness KDoc: the `⋮` menu sheet and every confirm this screen opens
 * render in their own windows and stay on manual verification.
 */
internal class TrainingDetailGoldenTest {

    /** ED9's frame whole: meta line · cards with plans · ruled history rows · dock. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun detailWithPlans(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingDetailScreen(state = detailState(), consume = {})
        }
    }

    /** One card carries no plan: the italic `.plan-line` fallback on the read card. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun detailNoPlanExercise(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingDetailScreen(
                state = detailState().copy(
                    exercises = detailState().exercises
                        .map { it.copy(planSets = null, planSummary = "") }
                        .toImmutableList(),
                ),
                consume = {},
            )
        }
    }

    /** No sessions yet: head without a trailing count, the empty line instead of rules. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun detailEmptyHistory(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingDetailScreen(
                state = detailState().copy(
                    pastSessions = persistentListOf(),
                    historyCount = 0,
                ),
                consume = {},
            )
        }
    }

    private fun detailState(): State = State
        .create(uuid = "training-uuid")
        .copy(
            isLoading = false,
            name = "Верх (с подтягиваниями)",
            tags = listOf(
                AppTagItem(uuid = "t1", name = "верх"),
                AppTagItem(uuid = "t2", name = "спина"),
            ).toImmutableList(),
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
                    planSets = listOf(
                        PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK),
                    ).toImmutableList(),
                    planSummary = "12",
                ),
            ).toImmutableList(),
            pastSessions = listOf(
                HistorySessionItem(sessionUuid = "s1", dateLabel = "27 июля 2026 г."),
                HistorySessionItem(sessionUuid = "s2", dateLabel = "22 июля 2026 г."),
            ).toImmutableList(),
            historyCount = 2,
        )
}
