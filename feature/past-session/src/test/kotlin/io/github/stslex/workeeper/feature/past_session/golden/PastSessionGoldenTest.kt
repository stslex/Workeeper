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
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.ui.PastSessionScreen
import io.github.stslex.workeeper.feature.past_session.ui.components.PastExerciseCard
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSessionHeader
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSetEditRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * BASELINE goldens — the past-session screen **as it stands before the Part 2 rebuild**.
 *
 * This feature had zero golden coverage (spec §24 names it as a gap), and recording the
 * current rendering *before* any rework edit is an entry condition of the rebuild PR: every
 * later commit re-records a region and the reviewer reads the image diff against these.
 * Without the baseline the rebuild's delta is unreadable and unreviewable.
 *
 * The fixture data deliberately mirrors `pass2d.html` §`s-past` (49/71/77×15 with the record
 * on set 3, «разведение ног», 56:08) so the final element-by-element pass can hold the golden
 * beside the mockup with no mental renaming. Data strings are fixture-side, so the Cyrillic
 * names render regardless of the harness's `en` resource locale.
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
                    deleteDialogVisible = false,
                ),
                consume = {},
            )
        }
    }

    // --- Header ----------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun header(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastSessionHeader(detail = detail())
        }
    }

    // --- Exercise card ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardWithSets(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise(),
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onTypeChange = { _, _ -> },
                onDragStarted = {},
                onSetReorder = { _, _, _ -> },
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun cardSkippedEmpty(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            PastExerciseCard(
                exercise = weightedExercise().copy(
                    skipped = true,
                    sets = persistentListOf(),
                ),
                onWeightChange = { _, _ -> },
                onRepsChange = { _, _ -> },
                onTypeChange = { _, _ -> },
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
                    onTypeChange = {},
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
                    onTypeChange = {},
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
                    onTypeChange = {},
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
                    onTypeChange = {},
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

private fun loadedState(): State = State(
    sessionUuid = "s-1",
    phase = State.Phase.Loaded(detail = detail()),
    deleteDialogVisible = false,
)

private fun detail(): PastSessionUiModel = PastSessionUiModel(
    trainingName = "низ — 2",
    isAdhoc = false,
    finishedAtAbsoluteLabel = "23 July 2026",
    durationLabel = "56:08",
    totalsLabel = "5 exercises · 14 sets",
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
    sets = listOf(
        set(uuid = "set-5", position = 0, weight = "", reps = "8"),
        set(uuid = "set-6", position = 1, weight = "", reps = "8"),
    ).toImmutableList(),
)

private fun set(
    uuid: String = "set-1",
    position: Int = 0,
    weight: String = "49",
    reps: String = "15",
    repsError: Boolean = false,
    isPersonalRecord: Boolean = false,
): PastSetUiModel = PastSetUiModel(
    setUuid = uuid,
    performedExerciseUuid = "pe-1",
    position = position,
    type = SetTypeUiModel.WORK,
    weightInput = weight,
    repsInput = reps,
    weightError = false,
    repsError = repsError,
    isPersonalRecord = isPersonalRecord,
)
