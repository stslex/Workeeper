// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.golden

import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordHero
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseDetailScreen
import io.github.stslex.workeeper.feature.exercise.ui.TopBar
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDetailMenuSheetContent
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseHistoryRow
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PersonalRecordUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exercise-detail golden suite. Fixture data mirrors `pass2d.html` §`s-ex`; the dialogs,
 * sheets and snackbars render in their own windows and stay on manual verification (§10.4).
 */
internal class ExerciseDetailGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenLoaded(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(state = loadedState(), consume = {})
        }
    }

    /** Every optional section absent at once (S8): the frame reduces to the top bar and dock. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(state = baseState(), consume = {})
        }
    }

    /** S8(a): `personalRecord == null` — the `.prhero` block is absent, with no placeholder. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenNoRecord(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = loadedState().copy(personalRecord = null),
                consume = {},
            )
        }
    }

    /** S8(b): zero sessions — the ИСТОРИЯ section is absent, head and all. Differential. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyHistory(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = loadedState().copy(
                    historyCount = 0,
                    recentHistory = persistentListOf(),
                ),
                consume = {},
            )
        }
    }

    /** S8(d): no sets — the ПЛАН ПО УМОЛЧАНИЮ section and its type declaration (ED12) both go. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyPlan(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = loadedState().copy(adhocPlan = null),
                consume = {},
            )
        }
    }

    /** The `ОПИСАНИЕ` condition is a disjunction; this is the picture-and-no-description arm. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenImageNoDescription(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = baseState().copy(
                    imagePath = "/exercise/preview.jpg",
                    imageLastModified = 1L,
                ),
                consume = {},
            )
        }
    }

    /** B11 and S8(c): the weightless read — one value box, reps only, weightless on the head. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(
                state = baseState().copy(
                    name = "подтягивания",
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    tags = persistentListOf(AppTagItem(uuid = "t-up", name = "верх")),
                    adhocPlan = List(3) {
                        PlanSetUiModel(weight = null, reps = 12, type = SetTypeUiModel.WORK)
                    }.toImmutableList(),
                    personalRecord = PersonalRecordUiModel(
                        sessionUuid = "s-pr",
                        weightLabel = null,
                        repsLabel = "15",
                        absoluteDateLabel = "12 июля 2026 г.",
                    ),
                ),
                consume = {},
            )
        }
    }

    /** `.topbar` (§1.2 on §3.1's frame): back chevron · `h1.sm` exercise name · `⋮`. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun topbar(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            TopBar(state = baseState(), consume = {})
        }
    }

    /** The `⋮` menu's CONTENT only — the sheet window is out of Paparazzi's model (§10.4). */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetDetailMenu(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            ExerciseDetailMenuSheetContent(canPermanentlyDelete = false, consume = {})
        }
    }

    /** Partner of [sheetDetailMenu]: the destructive row appears exactly when deletion is on. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetDetailMenuDeletable(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            ExerciseDetailMenuSheetContent(canPermanentlyDelete = true, consume = {})
        }
    }

    /** §3.3 fixture: mdot + Рекорд label, meta date line, 9×12 at dataValue — all molten. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recordHero(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PersonalRecordHero(
                weightLabel = "9",
                repsLabel = "12",
                metaLabel = "12 июля 2026 г.",
                onClick = {},
            )
        }
    }

    /** Difference partner of [recordHero]: the weightless value/unit split (B11 coverage). */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recordHeroWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PersonalRecordHero(
                weightLabel = null,
                repsLabel = "15",
                metaLabel = "12 июля 2026 г.",
            )
        }
    }

    /** S8(e), B-E4: the meta line clamps to ONE line with an ellipsis rather than grow the hero. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recordHeroMetaClamped(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PersonalRecordHero(
                weightLabel = "9",
                repsLabel = "12",
                metaLabel = "12 июля 2026 г. · верх (с подтягиваниями и добиваниями на блоке)",
                onClick = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun historyRow(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ExerciseHistoryRow(
                item = historyEntry(),
                isRecord = false,
                onClick = {},
                onPrTagClick = {},
            )
        }
    }

    /** Difference partner of [historyRow]: the record row swaps the chevron for the tag. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun historyRowRecord(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            ExerciseHistoryRow(
                item = HistoryUiModel(
                    sessionUuid = "s-2",
                    dateLabel = "12 июля",
                    setsSummaryLabel = "5×12 · 6×12 · 9×12 · 7×12",
                ),
                isRecord = true,
                onClick = {},
                onPrTagClick = {},
            )
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
    tags = persistentListOf(AppTagItem(uuid = "t-up", name = "верх")),
    description = "Разводи гантели в стороны до уровня плеч, локти чуть согнуты.",
    adhocPlan = List(4) {
        PlanSetUiModel(weight = 7.0, reps = 12, type = SetTypeUiModel.WORK)
    }.toImmutableList(),
    personalRecord = PersonalRecordUiModel(
        sessionUuid = "s-2",
        weightLabel = "9",
        repsLabel = "12",
        absoluteDateLabel = "12 июля 2026 г.",
    ),
    historyCount = 4,
    recentHistory = persistentListOf(
        historyEntry(),
        HistoryUiModel(
            sessionUuid = "s-2",
            dateLabel = "12 июля",
            setsSummaryLabel = "5×12 · 6×12 · 9×12 · 7×12",
        ),
        HistoryUiModel(
            sessionUuid = "s-3",
            dateLabel = "23 июня",
            setsSummaryLabel = "5×12 · 5×12 · 5×12 · 5×12",
        ),
    ),
)

private fun historyEntry(): HistoryUiModel = HistoryUiModel(
    sessionUuid = "s-1",
    dateLabel = "22 июля",
    setsSummaryLabel = "7×12 · 7×12 · 7×12 · 7×12",
)
