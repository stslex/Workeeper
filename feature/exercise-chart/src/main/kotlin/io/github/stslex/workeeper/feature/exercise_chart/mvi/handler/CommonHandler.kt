// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.RecentExerciseDataModel
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseChartInteractor,
    store: ExerciseChartHandlerStore,
) : Handler<Action.Common>, ExerciseChartHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    private fun processInit() {
        val initialUuid = state.value.initialUuid
        launch(
            onSuccess = { result ->
                val selected = result.recents.firstOrNull { it.uuid == result.targetUuid }
                val emptyReason = when {
                    result.recents.isEmpty() -> EmptyReason.NO_FINISHED_SESSIONS
                    selected == null && initialUuid != null -> EmptyReason.EXERCISE_NOT_FOUND
                    else -> null
                }
                updateStateImmediate { current ->
                    current.copy(
                        recentExercises = result.recents.toImmutableList(),
                        selectedExercise = selected,
                        emptyReason = emptyReason,
                        // Stop the spinner only when we're not about to fire loadChart —
                        // otherwise loadChart owns the false transition.
                        isLoading = selected != null,
                    )
                }
                if (selected != null) {
                    loadChart(selected)
                }
            },
        ) {
            val recents = interactor.getRecentlyTrainedExercises().map { it.toUi() }
            val resolvedUuid = initialUuid ?: interactor.getLastTrainedExerciseUuid()
            InitResult(recents = recents, targetUuid = resolvedUuid)
        }
    }

    /**
     * Re-fetch and bucket the chart for [exercise] using the current preset / metric.
     * Toggles `isLoading` true → false around the fetch and clears any prior tooltip /
     * `emptyReason`. On empty result, sets `EmptyReason.NO_DATA_FOR_EXERCISE`.
     *
     * Exposed for [ClickHandler] to call on picker / preset / metric changes.
     */
    fun loadChart(exercise: ExercisePickerItemUiModel) {
        val current = state.value
        launch(
            onSuccess = { result ->
                updateStateImmediate {
                    it.copy(
                        points = result.points,
                        footerStats = result.footer,
                        windowStartDay = result.windowStartDay,
                        windowEndDay = result.windowEndDay,
                        emptyReason = if (result.points.isEmpty()) {
                            EmptyReason.NO_DATA_FOR_EXERCISE
                        } else {
                            null
                        },
                        isLoading = false,
                    )
                }
            },
        ) {
            interactor.loadChartData(
                exerciseUuid = exercise.uuid,
                preset = current.preset,
                metric = current.metric,
                type = exercise.type,
                now = System.currentTimeMillis(),
            )
        }
    }

    private fun RecentExerciseDataModel.toUi(): ExercisePickerItemUiModel =
        ExercisePickerItemUiModel(
            uuid = uuid,
            name = name,
            type = when (type) {
                ExerciseTypeDataModel.WEIGHTED -> ExerciseTypeUiModel.WEIGHTED
                ExerciseTypeDataModel.WEIGHTLESS -> ExerciseTypeUiModel.WEIGHTLESS
            },
        )

    @Suppress("unused")
    private fun State.placeholder(): State = this

    private data class InitResult(
        val recents: List<ExercisePickerItemUiModel>,
        val targetUuid: String?,
    )
}
