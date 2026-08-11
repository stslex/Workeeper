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
     * Every optional section absent at once (S8): no tags, no record, no plan, no
     * description, no history — the read frame reduces to the top bar and the dock,
     * because a section with nothing in it does not render.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            ExerciseDetailScreen(state = baseState(), consume = {})
        }
    }

    /**
     * S8(a): `personalRecord == null` — the `.prhero` block is ABSENT and the screen opens
     * on the tags line and then the plan section. No placeholder, no empty hero: a record
     * that does not exist has nothing to display. The record session's history row loses
     * its PR tag with it (the tag keys on the record's session uuid).
     */
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

    /**
     * S8(b): zero sessions — the ИСТОРИЯ section is ABSENT, head and all; the description
     * is the frame's last block. Difference partner of [screenLoaded], which carries the
     * ruled list this frame drops. The record is held constant on purpose — a differential
     * fixture, not a reachable state: a real record derives from logged sessions, and
     * [screenEmpty] holds the every-section-absent frame.
     */
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

    /**
     * S8(d): no sets at all — the ПЛАН ПО УМОЛЧАНИЮ section is ABSENT, and the type
     * declaration on its head (ED12) leaves with it. The editor's empty card (head plus
     * setbar with a disabled «− подход») belongs to the editor, where the setbar is a
     * target; read has no target.
     */
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

    /**
     * The `ОПИСАНИЕ` section's condition is a **disjunction**, and this is the arm the other
     * goldens do not hold: a picture and no description. [screenLoaded] carries the
     * description-with-no-picture arm and [screenEmpty] the neither case, so the three together
     * are what separates `||` from `&&` — with `&&` this frame would lose its section and that
     * one difference is the whole ruling.
     */
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

    /**
     * B11 coverage and S8(c): the weightless read — plan rows carry ONE value box, reps
     * only, exactly as the editor and the session draw a weightless set; the section head's
     * trailing label declares the weightless type (БЕЗ ВЕСА in the shipped locale; this
     * harness renders its EN resource); the record label is reps-only.
     */
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

    // --- Topbar ----------------------------------------------------------------------------

    /** `.topbar` (§1.2 on §3.1's frame): back chevron · `h1.sm` exercise name · `⋮`. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun topbar(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            TopBar(state = baseState(), consume = {})
        }
    }

    // --- Sheets ----------------------------------------------------------------------------

    /**
     * The `⋮` menu's CONTENT on the sheet tier — the `ModalBottomSheet` window itself is out
     * of Paparazzi's one-window model and stays on the device checklist (§10.4).
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetDetailMenu(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            ExerciseDetailMenuSheetContent(canPermanentlyDelete = false, consume = {})
        }
    }

    /**
     * Difference partner of [sheetDetailMenu]: the destructive permanent-delete row appears
     * exactly when deletion is allowed. Carries the regression the retired
     * `exerciseDetailActions` unit test pinned (the discarded `apply { plus(...) }` result).
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetDetailMenuDeletable(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            ExerciseDetailMenuSheetContent(canPermanentlyDelete = true, consume = {})
        }
    }

    // --- Record block ----------------------------------------------------------------------

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

    /**
     * S8(e), B-E4: the meta line clamps to ONE line with an ellipsis instead of growing the
     * hero — the fixture is the drawn `date · training` form with a training name long
     * enough that the line must truncate at this width.
     */
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

    // --- History row -----------------------------------------------------------------------

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
