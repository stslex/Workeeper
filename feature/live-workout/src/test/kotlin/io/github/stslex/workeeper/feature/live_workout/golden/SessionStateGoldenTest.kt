// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveExerciseCard
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The session's set and exercise states at rest, on `core:ui:kit`'s shared golden harness.
 */
internal class SessionStateGoldenTest {

    // --- Set states: plain, done, pr -------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setPlain(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { SetRow(set(isDone = false)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setDone(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { SetRow(set(isDone = true)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setPersonalRecord(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { SetRow(set(isDone = false, isRecord = true)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setUnfilled(theme: GoldenTheme, testInfo: TestInfo) {
        // reps = 0 renders an EMPTY field, so a deliberate zero cannot be told apart.
        goldenSubject(testInfo, theme) { SetRow(set(isDone = false, reps = 0)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setPersonalRecordDone(theme: GoldenTheme, testInfo: TestInfo) {
        // pr + done: molten wins the value and the mark; 102.5 is the 5-glyph worst case.
        goldenSubject(testInfo, theme) {
            SetRow(set(isDone = true, isRecord = true, weight = 102.5))
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setTwoDigitReps(theme: GoldenTheme, testInfo: TestInfo) {
        // Pins the weighted two-digit-reps cell — the tightest value the reps column carries.
        goldenSubject(testInfo, theme) { SetRow(set(isDone = false, reps = 12)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setBodyweight(theme: GoldenTheme, testInfo: TestInfo) {
        // One full-width field, the unit spelled out (`повторений` in RU; extraction §1.6).
        goldenSubject(testInfo, theme) {
            LiveSetRow(
                set = set(isDone = false).copy(weight = null, reps = 12),
                isWeighted = false,
                onWeightChange = {},
                onRepsChange = {},
                onTypeChange = {},
                onMarkDone = {},
                onUncheck = {},
                editable = true,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun setFlashPeak(theme: GoldenTheme, testInfo: TestInfo) {
        // The §10.2 pair with `setDone`: the flash frozen at its peak via `flashAlphaOverride`.
        goldenSubject(testInfo, theme) {
            LiveSetRow(
                set = set(isDone = true),
                isWeighted = true,
                onWeightChange = {},
                onRepsChange = {},
                onTypeChange = {},
                onMarkDone = {},
                onUncheck = {},
                editable = true,
                flashAlphaOverride = 1f,
            )
        }
    }

    // Exercise states (§1.5): resting, active, fin, fin-reopened, skip, temp.
    // `exercisePending`/`exerciseActive` are the lift's §10.2 pair (unlifted / lifted).

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exercisePending(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.PENDING, done = 0), expanded = false)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseActive(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.CURRENT, done = 1), expanded = true)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseSingleRow(theme: GoldenTheme, testInfo: TestInfo) {
        // The setbar's §10.2 pair with `exerciseActive`: at one row `− подход` is disabled.
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.CURRENT, done = 0, sets = 1), expanded = true)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseFinished(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.DONE, done = 3), expanded = false)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseFinishedReopened(theme: GoldenTheme, testInfo: TestInfo) {
        // A completed card opens, closes and lifts like any other; fin wins the chip.
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.DONE, done = 3), expanded = true)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseSkipped(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.SKIPPED, done = 0), expanded = false)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseOneOff(theme: GoldenTheme, testInfo: TestInfo) {
        // `temp`: in the session, not in the plan — NOT `is_adhoc`.
        goldenSubject(testInfo, theme) {
            Card(
                exercise(ExerciseStatusUiModel.CURRENT, done = 0, isPlanAttached = false),
                expanded = false,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseOneOffFinished(theme: GoldenTheme, testInfo: TestInfo) {
        // temp.fin: the checkmark replaces the number here too — `.card.fin .ordchip svg`
        // has no `:not(.temp)` guard.
        goldenSubject(testInfo, theme) {
            Card(
                exercise(ExerciseStatusUiModel.DONE, done = 3, isPlanAttached = false),
                expanded = false,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseTenSets(theme: GoldenTheme, testInfo: TestInfo) {
        // The D3 canary: at ten sets the index label "10" grows the shared resolved width.
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.CURRENT, done = 4, sets = 10), expanded = true)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseBodyweightRu(theme: GoldenTheme, testInfo: TestInfo) {
        // Pins the ПОВТОРЕНИЙ header — the widest label — over the bodyweight column.
        val weighted = exercise(ExerciseStatusUiModel.CURRENT, done = 0)
        goldenSubject(testInfo, theme, locale = LOCALE_RU) {
            Card(
                weighted.copy(
                    exerciseType = ExerciseTypeUiModel.WEIGHTLESS,
                    statusLabel = "12 · 12 · 12",
                    visibleSets = weighted.visibleSets
                        .map { it.copy(weight = null, reps = 12) }
                        .toImmutableList(),
                ),
                expanded = true,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun SetRow(set: LiveSetUiModel) {
    LiveSetRow(
        set = set,
        isWeighted = true,
        onWeightChange = {},
        onRepsChange = {},
        onTypeChange = {},
        onMarkDone = {},
        onUncheck = {},
        editable = true,
    )
}

@androidx.compose.runtime.Composable
private fun Card(exercise: LiveExerciseUiModel, expanded: Boolean) {
    LiveExerciseCard(exercise = exercise, ordinal = 1, expanded = expanded, consume = {})
}

private fun set(
    isDone: Boolean,
    isRecord: Boolean = false,
    reps: Int = 5,
    weight: Double = 100.0,
): LiveSetUiModel = LiveSetUiModel(
    position = 0,
    weight = weight,
    reps = reps,
    type = SetTypeUiModel.WORK,
    isDone = isDone,
    isPersonalRecord = isRecord,
)

private fun exercise(
    status: ExerciseStatusUiModel,
    done: Int,
    isPlanAttached: Boolean = true,
    sets: Int = 3,
): LiveExerciseUiModel {
    val sets = (0 until sets).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = position < done,
        )
    }
    return LiveExerciseUiModel(
        performedExerciseUuid = "pe-1",
        exerciseUuid = "ex-1",
        // Cyrillic on purpose — the title is a text slot and the primary locale is Russian.
        exerciseName = "жим лёжа",
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = 0,
        status = status,
        // `.sub` is always the plan (or the skipped marker) — extraction §1.5.
        statusLabel = when (status) {
            ExerciseStatusUiModel.SKIPPED -> "пропущено"
            else -> "100×5 · 100×5 · 102.5×5"
        },
        planSets = persistentListOf(
            PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        ),
        performedSets = sets.filter { it.isDone }.toImmutableList(),
        visibleSets = sets.toImmutableList(),
        isPlanAttached = isPlanAttached,
    )
}
