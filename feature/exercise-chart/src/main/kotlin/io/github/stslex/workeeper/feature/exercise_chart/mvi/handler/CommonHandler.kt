// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartScope
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ChartReadoutMapper
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toUi
import io.github.stslex.workeeper.feature.exercise_chart.mvi.mapper.ExerciseChartUiMapper.toUiPoints
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async

@SingleIn(ExerciseChartScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseChartInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: ExerciseChartHandlerStore,
) : Handler<Action.Common>, ExerciseChartHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    private fun processInit() {
        val initialUuid = state.value.initialUuid
        launchDefault(
            onSuccess = { result ->
                updateStateImmediate { current ->
                    current.copy(
                        recentExercises = result.recents,
                        selectedExercise = result.selected,
                        emptyReason = result.emptyReason,
                        // Stop the spinner only when we're not about to fire loadChart —
                        // otherwise loadChart owns the false transition.
                        isLoading = result.isLoading,
                    )
                }
                result.selected?.also { selected ->
                    loadChart(selected)
                }
            },
        ) {
            val recentsDeferred = async {
                interactor.getRecentlyTrainedExercises().map { it.toUi() }
            }
            val resolvedUuidDeferred = async {
                initialUuid ?: interactor.getLastTrainedExerciseUuid()
            }

            val recents = recentsDeferred.await()
            val resolvedUuid = resolvedUuidDeferred.await()

            val selected = recents.firstOrNull { it.uuid == resolvedUuid }
            val emptyReason = when {
                recents.isEmpty() -> EmptyReason.NO_FINISHED_SESSIONS
                selected == null && initialUuid != null -> EmptyReason.EXERCISE_NOT_FOUND
                else -> null
            }

            InitResult(
                selected = selected,
                recents = recents.toImmutableList(),
                emptyReason = emptyReason,
                isLoading = selected != null,
            )
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
        val metric = current.metric.toDomain()
        val type = exercise.type.toDomain()
        launchDefault(
            onSuccess = { result ->
                updateStateImmediate {
                    val newPoints = result.toUiPoints()
                    // The scrub position survives a reload only when the day buckets are the
                    // same — a metric switch replots identical days (the mockup keeps `active`
                    // across setMetric). A preset or exercise change produces new buckets and
                    // the readout resets to the most recent point.
                    val sameDays = it.points.map(ChartPointUiModel::day) ==
                        newPoints.map(ChartPointUiModel::day)
                    val activeIndex = it.activeIndex
                        ?.takeIf { index -> sameDays && index in newPoints.indices }
                        ?: (newPoints.size - 1).takeIf { index -> index >= 0 }
                    it.copy(
                        points = newPoints,
                        footerStats = result.footer?.toUi(type, resourceWrapper),
                        activeIndex = activeIndex,
                        readout = ChartReadoutMapper.toReadout(
                            points = newPoints,
                            activeIndex = activeIndex,
                            metric = current.metric,
                            type = exercise.type,
                            resourceWrapper = resourceWrapper,
                        ),
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
                preset = current.preset.toDomain(),
                metric = metric,
                type = type,
                now = System.currentTimeMillis(),
            )
        }
    }

    @Suppress("unused")
    private fun State.placeholder(): State = this

    private data class InitResult(
        val selected: ExercisePickerItemUiModel?,
        val recents: ImmutableList<ExercisePickerItemUiModel>,
        val emptyReason: EmptyReason?,
        val isLoading: Boolean,
    )
}
