// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
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
 * The session's set and exercise states (§6.1, §6.5, §14), at rest.
 *
 * These render the real feature components rather than kit stand-ins, which is why the
 * goldens live in this module. The harness comes from `core:ui:kit`'s testFixtures, so device
 * config, `maxPercentDifference`, `useDeviceResolution` and the 392dp canvas are the same
 * values the kit goldens use — a copied harness would let those drift per module and weaken
 * the gate silently.
 *
 * **Explicitly not covered**, per §10.4: both wow moments. The set-closure morph, the row
 * flash and the molten unfurl are time-based, and Paparazzi renders one frame of a single
 * window. The `pr` case below is the *resting* record state, not the animation that reaches
 * it. Those are on the device checklist.
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
        // §6.1's sentinel rendered: reps = 0 shows an EMPTY field, which is precisely why a
        // deliberate zero cannot be told from an unfilled row and why discarding is safe.
        goldenSubject(testInfo, theme) { SetRow(set(isDone = false, reps = 0)) }
    }

    // --- Exercise states (§1.5): resting, active, fin, fin-reopened, skip, temp --------
    //
    // BASELINE CORRECTIONS against the step-5 goldens: the card is 16dp-radius, the ordinal
    // is the 7-state `.ordchip`, done is a checkmark-on-donefill + meta/500 title (not an
    // alpha fade), skip is opacity .5 + strikethrough, the sub is always the plan, the head
    // carries the pstrip micro-rail and the `.mini` cluster with the rotating chevron — and
    // the LIFT keys on *expanded* (`.card.active` == isOpen), not on CURRENT.
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
    fun exerciseFinished(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Card(exercise(ExerciseStatusUiModel.DONE, done = 3), expanded = false)
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun exerciseFinishedReopened(theme: GoldenTheme, testInfo: TestInfo) {
        // A completed card is manually expandable (§7) and lifts like any open card —
        // fin + active co-exist, fin winning the chip (stylesheet order, L87–88).
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
        // The `temp` state of §6.1: in the session, not in the plan (§6.2). NOT `is_adhoc` —
        // this exercise may be a long-standing library entry added as a one-off today.
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
        // has no `:not(.temp)` guard (the extraction's own state table has this wrong).
        goldenSubject(testInfo, theme) {
            Card(
                exercise(ExerciseStatusUiModel.DONE, done = 3, isPlanAttached = false),
                expanded = false,
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
    LiveExerciseCard(exercise = exercise, expanded = expanded, consume = {})
}

private fun set(
    isDone: Boolean,
    isRecord: Boolean = false,
    reps: Int = 5,
): LiveSetUiModel = LiveSetUiModel(
    position = 0,
    weight = 100.0,
    reps = reps,
    type = SetTypeUiModel.WORK,
    isDone = isDone,
    isPersonalRecord = isRecord,
)

private fun exercise(
    status: ExerciseStatusUiModel,
    done: Int,
    isPlanAttached: Boolean = true,
): LiveExerciseUiModel {
    val sets = (0 until 3).map { position ->
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
