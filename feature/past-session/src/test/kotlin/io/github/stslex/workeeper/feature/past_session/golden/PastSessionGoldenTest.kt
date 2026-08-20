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
 * The past-session golden suite. The BASELINE commit (C0) recorded the pre-rebuild surface;
 * each rebuild commit re-records exactly its region, so the reviewer reads image diffs
 * commit by commit rather than a hex diff at the end.
 *
 * The fixture data deliberately mirrors `pass2d.html` §`s-past` (49/71/77×15 with the record
 * on set 3, «разведение ног», 56:08) so the final element-by-element pass can hold the golden
 * beside the mockup with no mental renaming. Data strings are fixture-side, so the Cyrillic
 * names render regardless of the harness's `en` resource locale.
 *
 * ## On §10.2's transient-pair rule
 *
 * This screen has no transient state, by decision: a finished session's records do not
 * change while on screen, so there is no false→true transition and nothing animates into
 * molten — animating one would reproduce the exact §10.2 defect for no behaviour. What the
 * rule still buys is the **difference assertion**, and three pairs carry it:
 * [setRowPlain]/[setRowPersonalRecord] (one flag apart), [header]/[headerWithoutTonnage]
 * (the §11.1 drop-out branch), and [cardWithSets]/[cardCollapsed] (the lift, §10.2's
 * unlifted/lifted pair). The two animated things — the card's expand size change and the
 * lift's colour tween — settle to their targets under Paparazzi's single frame; their rest
 * states are the covered pictures and the motion itself is on the device checklist (§10.4).
 *
 * Out of model, per the harness KDoc: `DeleteConfirmDialog` and `PrExplainerDialog` render in
 * their own windows and stay on manual verification.
 */
internal class PastSessionGoldenTest {

    // --- Whole frame -----------------------------------------------------------------------

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

    // --- Topbar ----------------------------------------------------------------------------

    /** `.topbar` (§2.2): back chevron · `h1.sm` title · ⋮ overflow — no delete glyph. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun topbar(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            TopBar(state = loadedState(), consume = {})
        }
    }

    // --- Sheets ----------------------------------------------------------------------------

    /**
     * The `⋮` menu's CONTENT on the sheet tier — the `ModalBottomSheet` window itself is out
     * of Paparazzi's one-window model and stays on the device checklist (§10.4).
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sheetSessionMenu(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier3 }) {
            PastSessionMenuSheetContent(consume = {})
        }
    }

    // --- Header ----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun header(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            InGutter { PastSessionHeader(detail = detail()) }
        }
    }

    /**
     * Pair partner of [header] (§10.2's difference assertion): a session that lifted nothing
     * drops the third term rather than printing "· 0 kg". Without this, the tonnage golden
     * could not tell a computed figure from an always-printed one.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun headerWithoutTonnage(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            InGutter {
                PastSessionHeader(detail = detail(totals = "5 exercises · 14 sets"))
            }
        }
    }

    // --- Exercise card ---------------------------------------------------------------------

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

    /**
     * Disclosure pair partner of [cardWithSets] (§10.2's unlifted/lifted pair): the resting
     * card — `.ord` · title · `.plan-line` summary · the static bare chevron, on
     * `surfaceTier1` with no lift.
     */
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
     * Ten sets — the index column must render a two-digit ordinal. Pins the fix for the
     * silent wrap a fixed 12dp column caused: `Text` breaks an over-wide token at a
     * grapheme boundary, so "10" stacked as 1-over-0 without changing row height.
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

    /**
     * The skipped card, collapsed — the session sibling treatment (§1.5 applied to §2.5's
     * two-state card): 0.5 alpha, struck-through title in `textTertiary`, the plan-line
     * replaced by the literal skipped line. The v2.4 warning chip is retired.
     */
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
     * Skipped **with** sets and a non-empty summary — the fixture that actually pins the
     * precedence. `cardSkippedEmpty` zeroes `setSummary` and `sets` together, so swapping
     * the skipped and summary branches of the plan-line `when` produced byte-identical
     * pixels there. The state is reachable: the live session preserves performed sets
     * across a skip toggle, and the mapper fills both fields regardless of `skipped`.
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

    // --- Set row ---------------------------------------------------------------------------

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

    /**
     * B11 coverage: the weightless row, whose write path carries the #178 stale-weight hazard.
     * The rebuild does not touch that arc, but this golden is the coverage that lets its future
     * fix read as a visible delta instead of shipping invisibly.
     */
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
    // First-entry rule of the amended §7 model: the first card open, the rest closed.
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

/**
 * Ten sets, so the index column has to render a two-digit ordinal. A fixed 12dp column
 * cannot fit "10" at `mono.meta`, and `Text` breaks an over-wide token at a grapheme
 * boundary rather than overflowing — which stacked the digits silently, because the 48dp
 * fields dominate the row height. This is the fixture that makes that visible.
 */
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
