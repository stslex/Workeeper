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
 * The training read screen (`v3-editors.md` §3.3) — the ED9/ED10 rulings pinned on a static
 * frame. Russian, since the heads and dock labels ship in Russian; sheets are out of model.
 */
internal class TrainingDetailGoldenTest {

    /** ED9's frame whole: meta · cards with plans · description · ruled history rows · dock. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun detailWithPlans(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme, locale = LOCALE_RU) {
            TrainingDetailScreen(
                state = detailState().copy(
                    description = "Фокус на жиме: четыре недели линейной прибавки.",
                ),
                consume = {},
            )
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

    /** S8: zero sessions — no ИСТОРИЯ section at all; the exercise cards end the frame. */
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
