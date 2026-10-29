// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.golden

import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.ui.LiveWorkoutScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The whole frame (extraction §1.1) on one full-device canvas: this locks how the regions
 * compose, not the regions themselves. Out-of-window surfaces stay Hidden (§10.4).
 */
internal class SessionScreenGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun sessionScreen(theme: GoldenTheme, testInfo: TestInfo) {
        golden(testInfo, theme) {
            LiveWorkoutScreen(
                state = screenState(),
                consume = {},
            )
        }
    }
}

@Suppress("LongMethod")
private fun screenState(): State {
    val activeSets = (0 until 3).map { position ->
        LiveSetUiModel(
            position = position,
            weight = if (position == 2) 102.5 else 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = position == 0,
            isPersonalRecord = position == 0,
        )
    }.toImmutableList()
    val plan = persistentListOf(
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
    )
    fun exercise(
        uuid: String,
        name: String,
        position: Int,
        status: ExerciseStatusUiModel,
        sets: kotlinx.collections.immutable.ImmutableList<LiveSetUiModel>,
        isPlanAttached: Boolean = true,
    ) = LiveExerciseUiModel(
        performedExerciseUuid = uuid,
        exerciseUuid = "ex-$uuid",
        exerciseName = name,
        exerciseType = ExerciseTypeUiModel.WEIGHTED,
        position = position,
        status = status,
        statusLabel = if (status == ExerciseStatusUiModel.SKIPPED) {
            "пропущено"
        } else {
            "100×5 · 100×5 · 102.5×5"
        },
        planSets = plan,
        performedSets = sets.filter { it.isDone }.toImmutableList(),
        visibleSets = sets,
        isPlanAttached = isPlanAttached,
    )

    val doneSets = (0 until 3).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        )
    }.toImmutableList()

    return State.create(sessionUuid = "s-1", trainingUuid = "t-1").copy(
        trainingName = "верх (с подтягиваниями)",
        trainingNameLabel = "верх (с подтягиваниями)",
        trainingNameDraft = "верх (с подтягиваниями)",
        elapsedDurationLabel = "12:04",
        headerMetaLabel = "1 из 3 упражнений · 4 из 9 подходов",
        doneCount = 1,
        totalCount = 3,
        setsLogged = 4,
        progress = 0.33f,
        exercises = persistentListOf(
            exercise("pe-1", "жим лёжа", 0, ExerciseStatusUiModel.DONE, doneSets),
            exercise("pe-2", "тяга в наклоне", 1, ExerciseStatusUiModel.CURRENT, activeSets),
            exercise(
                "pe-3",
                "подтягивания",
                2,
                ExerciseStatusUiModel.PENDING,
                activeSets.map { it.copy(isDone = false, isPersonalRecord = false) }.toImmutableList(),
                isPlanAttached = false,
            ),
        ),
        setDrafts = persistentMapOf(),
        expandedExerciseUuids = persistentSetOf("pe-2"),
        isLoading = false,
        dialogState = DialogState.Hidden,
        bottomSheetState = BottomSheetState.Hidden,
    )
}
