// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordCard
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseDetailScreen
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise-detail golden suite. The BASELINE commit (C0) records the pre-rebuild
 * surface — Scaffold + M3 `DetailTopbar`, `AppCard` sections, card-shaped history rows —
 * so each Part-3 rebuild commit reads as an image diff against the skin it replaces.
 *
 * Fixture data deliberately mirrors `pass2d.html` §`s-ex` («Отведение гантелей через
 * стороны», type «С весом» + tag «верх», plan 4 × 7×12, record 9×12, history
 * 22 июля / 12 июля (PR) / 23 июня) so the final element-by-element pass can hold the
 * golden beside the mockup with no mental renaming. Data strings are fixture-side, so the
 * Cyrillic names render regardless of the harness's `en` resource locale.
 *
 * Out of model, per the harness KDoc: the topbar `DropdownMenu`, every dialog
 * (`AppBlockedArchiveDialog`, `AppConfirmDialog`, `ActiveSessionConflictDialog`,
 * `PrExplainerDialog`) and the snackbars render in their own windows and stay on manual
 * verification (§10.4).
 */
internal class ExerciseDetailGoldenTest {

    // --- Whole frame -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenLoaded(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(state = loadedState(), consume = {})
        }
    }

    /**
     * The empty branch of every optional section at once: no description, no plan, no
     * record, no history — the "no sessions yet" line under the Recent label.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(state = baseState(), consume = {})
        }
    }

    /** B11 coverage: the weightless variant — reps-only plan rows, reps-only record label. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = baseState().copy(
                    name = "подтягивания",
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    tags = persistentListOf(TagUiModel(uuid = "t-up", name = "верх")),
                    adhocPlan = List(3) {
                        PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK)
                    }.toImmutableList(),
                    personalRecord = PersonalRecordUiModel(
                        sessionUuid = "s-pr",
                        displayLabel = "15 повт",
                        relativeDateLabel = "12 июля",
                    ),
                ),
                consume = {},
            )
        }
    }

    // --- Record block ----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recordCard(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PersonalRecordCard(
                displayLabel = "9 × 12",
                relativeDateLabel = "12 июля",
                onClick = {},
                onBadgeClick = {},
            )
        }
    }

    // --- History row -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun historyRow(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ExerciseHistoryRow(item = historyEntry(), onClick = {})
        }
    }
}

private fun baseState(): State = State
    .create(uuid = "ex-1")
    .copy(
        mode = State.Mode.Read,
        name = "Отведение гантелей через стороны",
        isLoading = false,
    )

private fun loadedState(): State = baseState().copy(
    tags = persistentListOf(TagUiModel(uuid = "t-up", name = "верх")),
    description = "Разводи гантели в стороны до уровня плеч, локти чуть согнуты.",
    adhocPlan = List(4) {
        PlanSetUiModel(weight = 7.0, reps = 12, type = SetTypeUiModel.WORK)
    }.toImmutableList(),
    personalRecord = PersonalRecordUiModel(
        sessionUuid = "s-2",
        displayLabel = "9 × 12",
        relativeDateLabel = "12 июля",
    ),
    recentHistory = persistentListOf(
        historyEntry(),
        HistoryUiModel(
            sessionUuid = "s-2",
            setsSummaryLabel = "5 × 12 · 6 × 12 · 9 × 12 · 7 × 12",
            metaLabel = "12 июля 2026 г. · верх (с подтягиваниями)",
        ),
        HistoryUiModel(
            sessionUuid = "s-3",
            setsSummaryLabel = "5 × 12 · 5 × 12 · 5 × 12 · 5 × 12",
            metaLabel = "23 июня 2026 г. · верх (с подтягиваниями)",
        ),
    ),
)

private fun historyEntry(): HistoryUiModel = HistoryUiModel(
    sessionUuid = "s-1",
    setsSummaryLabel = "7 × 12 · 7 × 12 · 7 × 12 · 7 × 12",
    metaLabel = "22 июля 2026 г. · верх (с подтягиваниями)",
)
