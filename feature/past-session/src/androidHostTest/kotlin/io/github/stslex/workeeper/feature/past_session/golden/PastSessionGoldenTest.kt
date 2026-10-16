// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.past_session.mvi.store.DialogState
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.ui.PastSessionScreen
import io.github.stslex.workeeper.feature.past_session.ui.TopBar
import io.github.stslex.workeeper.feature.past_session.ui.components.PastExerciseCard
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionHeader
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionMenuSheetContent
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSetEditRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The past-session golden suite; fixtures mirror `pass2d.html` §`s-past` so a golden can be held
 * beside the mockup. Dialogs render in their own windows and stay on manual verification.
 */
internal class PastSessionGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenLoaded(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            PastSessionScreen(state = loadedState(), consume = {})
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenError(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            PastSessionScreen(
                state = State(
                    sessionUuid = "s-1",
                    phase = State.Phase.Error(ErrorType.SessionNotFound),
                    hasResolved = true,
                    expandedExerciseUuids = persistentSetOf(),
                    dialogState = DialogState.Hidden,
                    bottomSheetState = BottomSheetState.Hidden,
                ),
                consume = {},
            )
        }
    }

    /** `.topbar` (§2.2): back chevron · `h1.sm` title · ⋮ overflow — no delete glyph. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun topbar(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            TopBar(state = loadedState(), consume = {})
        }
    }

    /** The `⋮` menu's CONTENT; the sheet window is out of Paparazzi's one-window model. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetSessionMenu(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            PastSessionMenuSheetContent(consume = {})
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun header(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            InGutter { PastSessionHeader(detail = detail()) }
        }
    }

    /** Pair partner of [header]: a session that lifted nothing drops the third term. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun headerWithoutTonnage(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            InGutter {
                PastSessionHeader(detail = detail(totals = "5 exercises · 14 sets"))
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardWithSets(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise(),
                expanded = true,
                onHeaderClick = {},
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onPrTagClick = {},
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    /** Disclosure pair partner of [cardWithSets]: the resting card, no lift. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardCollapsed(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise(),
                expanded = false,
                onHeaderClick = {},
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onPrTagClick = {},
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    /**
     * Ten sets — the index column must render a two-digit ordinal. Pins the fix for the silent
     * grapheme-boundary wrap a fixed-width column caused.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardDoubleDigitIndex(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = tenSetExercise(),
                expanded = true,
                onHeaderClick = {},
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onPrTagClick = {},
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    /** The skipped card, collapsed: 0.5 alpha, struck-through title, the skipped line. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardSkippedEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise().copy(
                    skipped = true,
                    setSummary = "",
                    sets = persistentListOf(),
                ),
                expanded = false,
                onHeaderClick = {},
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onPrTagClick = {},
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    /**
     * Skipped WITH sets and a summary — the fixture that pins the plan-line branch order;
     * [cardSkippedEmpty] zeroes both fields, so a swap there is byte-identical.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardSkippedWithSets(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise().copy(skipped = true),
                expanded = false,
                onHeaderClick = {},
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onPrTagClick = {},
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setRowPlain(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            InCard {
                PastSetEditRow(
                    set = set(),
                    isWeighted = true,
                    onWeightChange = {},
                    onRepsChange = {},
                    onPrTagClick = {},
                )
            }
        }
    }

    /** Difference partner of [setRowPlain] — one flag apart, the record treatment. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setRowPersonalRecord(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            InCard {
                PastSetEditRow(
                    set = set(weight = "77", isPersonalRecord = true),
                    isWeighted = true,
                    onWeightChange = {},
                    onRepsChange = {},
                    onPrTagClick = {},
                )
            }
        }
    }

    /** The weightless row — coverage that lets a future write-path fix read as a delta. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setRowWeightless(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            InCard {
                PastSetEditRow(
                    set = set(weight = "", reps = "12"),
                    isWeighted = false,
                    onWeightChange = {},
                    onRepsChange = {},
                    onPrTagClick = {},
                )
            }
        }
    }

    /** Editing is real on this screen, so the invalid-input branch is part of its surface. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setRowInvalidReps(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            InCard {
                PastSetEditRow(
                    set = set(reps = "", repsError = true),
                    isWeighted = true,
                    onWeightChange = {},
                    onRepsChange = {},
                    onPrTagClick = {},
                )
            }
        }
    }
}

/** Rows live inside the card's padding; bare rows against the card tier would misstate insets. */
@Composable
private fun InCard(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(AppDimension.cardPadding)) {
        content()
    }
}

/** The header sits under the screen gutter (`padding: 0 var(--gutter)` — §2.1). */
@Composable
private fun InGutter(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = AppDimension.screenEdge)) {
        content()
    }
}

private fun loadedState(): State = State(
    sessionUuid = "s-1",
    phase = State.Phase.Loaded(detail = detail()),
    hasResolved = true,
    // First-entry rule (§7): the first card open, the rest closed.
    expandedExerciseUuids = persistentSetOf("pe-1"),
    dialogState = DialogState.Hidden,
    bottomSheetState = BottomSheetState.Hidden,
)

private fun detail(
    totals: String = "5 exercises · 14 sets · 4,820 kg",
): PastSessionUiModel = PastSessionUiModel(
    trainingName = "низ — 2",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "23 July 2026",
    durationLabel = "56:08",
    totalsLabel = totals,
    exercises = persistentListOf(
        weightedExercise(),
        weightlessExercise(),
    ),
)

private fun weightedExercise(): PastExerciseUiModel = PastExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseName = "разведение ног",
    position = 0,
    skipped = false,
    isWeighted = true,
    setSummary = "49×15 · 71×15 · 77×15 · 71×15",
    sets = listOf(
        set(uuid = "set-1", position = 0, weight = "49"),
        set(uuid = "set-2", position = 1, weight = "71"),
        set(uuid = "set-3", position = 2, weight = "77", isPersonalRecord = true),
        set(uuid = "set-4", position = 3, weight = "71"),
    ).toImmutableList(),
)

private fun weightlessExercise(): PastExerciseUiModel = PastExerciseUiModel(
    performedExerciseUuid = "pe-2",
    exerciseName = "подтягивания",
    position = 1,
    skipped = false,
    isWeighted = false,
    setSummary = "8 · 8",
    sets = listOf(
        set(uuid = "set-5", position = 0, performedExerciseUuid = "pe-2", weight = "", reps = "8"),
        set(uuid = "set-6", position = 1, performedExerciseUuid = "pe-2", weight = "", reps = "8"),
    ).toImmutableList(),
)

/** Ten sets, so the index column has to render a two-digit ordinal. */
private fun tenSetExercise(): PastExerciseUiModel = PastExerciseUiModel(
    performedExerciseUuid = "pe-3",
    exerciseName = "жим платформы",
    position = 2,
    skipped = false,
    isWeighted = true,
    setSummary = "60×12 · 60×12",
    sets = (0 until 10).map { index ->
        set(uuid = "set-many-$index", position = index, performedExerciseUuid = "pe-3", weight = "60", reps = "12")
    }.toImmutableList(),
)

private fun set(
    uuid: String = "set-1",
    position: Int = 0,
    performedExerciseUuid: String = "pe-1",
    weight: String = "49",
    reps: String = "15",
    repsError: Boolean = false,
    isPersonalRecord: Boolean = false,
): PastSetUiModel = PastSetUiModel(
    setUuid = uuid,
    performedExerciseUuid = performedExerciseUuid,
    position = position,
    type = SetTypeUiModel.WORK,
    weightInput = weight,
    repsInput = reps,
    weightError = false,
    repsError = repsError,
    isPersonalRecord = isPersonalRecord,
)
