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
                val selected = result.recents
                    .firstOrNull { it.uuid == result.targetUuid }
                updateStateImmediate { current ->
                    current.copy(
                        recentExercises = result.recents.toImmutableList(),
                        selectedExercise = selected,
                        isLoading = selected != null,
                        isEmpty = selected == null,
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

    fun reload() {
        val selected = state.value.selectedExercise ?: return
        loadChart(selected)
    }

    private fun loadChart(exercise: ExercisePickerItemUiModel) {
        val current = state.value
        launch(
            onSuccess = { result ->
                updateStateImmediate {
                    it.copy(
                        points = result.points,
                        footerStats = result.footer,
                        isEmpty = result.points.isEmpty(),
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
