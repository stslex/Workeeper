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
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.EmptyReason
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

@SingleIn(ExerciseChartScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseChartInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: ExerciseChartHandlerStore,
) : Handler<Action.Common>, ExerciseChartHandlerStore by store {

    /** The in-flight chart load, if any. Held only so the next one can cancel it. */
    private var loadJob: Job? = null

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
        // The retarget sources — metric tabs, preset chips, picker — are one path with
        // three triggers, and a user can fire them faster than the DB answers. Two guards,
        // because neither alone is enough: the previous request is cancelled so it stops
        // competing, and the response carries the request that asked for it so a winner
        // that is already stale cannot be applied. Without them the last response to LAND
        // won outright, and since `metric` is never rewritten by a load, the chart could
        // settle showing Сессия's data under a highlighted Сет tab, permanently.
        val request = ChartRequest(
            exerciseUuid = exercise.uuid,
            preset = current.preset,
            metric = current.metric,
        )
        loadJob?.cancel()
        loadJob = launchDefault(
            onSuccess = { result ->
                if (state.value.requestOf() != request) return@launchDefault
                // Rule 1 (compose-state-discipline): everything below is mapped in the
                // collector body, off Main.immediate; the lambda only copies State. The
                // prior points/activeIndex are read here rather than from the lambda
                // argument — a scrub landing in between would be lost either way (it is a
                // second writer of activeIndex), and the guard above has just established
                // that this response is the live one.
                val prior = state.value
                val newPoints = result.toUiPoints()
                // §4.8: the copy states the threshold — the chart appears after TWO
                // recorded sessions. Below two points there is no line to draw (the
                // canvas is index-spaced), so sub-threshold is an empty state, not a
                // degenerate chart, and the readout/scrub state stays clear.
                val subThreshold = newPoints.size < State.MIN_CHART_POINTS
                // The scrub position survives a reload only when the day buckets are the
                // same — a metric switch replots identical days (the mockup keeps
                // `active` across setMetric). A preset or exercise change produces new
                // buckets and the readout resets to the most recent point.
                val sameDays = prior.points.map(ChartPointUiModel::day) ==
                    newPoints.map(ChartPointUiModel::day)
                val activeIndex = if (subThreshold) {
                    null
                } else {
                    prior.activeIndex
                        ?.takeIf { index -> sameDays && index in newPoints.indices }
                        ?: (newPoints.size - 1)
                }
                val footerStats = result.footer?.toUi(type, resourceWrapper)
                val readout = ChartReadoutMapper.toReadout(
                    points = newPoints,
                    activeIndex = activeIndex,
                    metric = current.metric,
                    type = exercise.type,
                    resourceWrapper = resourceWrapper,
                )
                updateStateImmediate {
                    it.copy(
                        points = newPoints,
                        footerStats = footerStats,
                        activeIndex = activeIndex,
                        readout = readout,
                        emptyReason = if (subThreshold) {
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

    /** The live selection, in the shape a pending [ChartRequest] can be compared against. */
    private fun State.requestOf(): ChartRequest? = selectedExercise?.let { selected ->
        ChartRequest(exerciseUuid = selected.uuid, preset = preset, metric = metric)
    }

    /** What a load was asked for. A response that no longer matches the live state is dropped. */
    private data class ChartRequest(
        val exerciseUuid: String,
        val preset: ChartPresetUiModel,
        val metric: ChartMetricUiModel,
    )

    private data class InitResult(
        val selected: ExercisePickerItemUiModel?,
        val recents: ImmutableList<ExercisePickerItemUiModel>,
        val emptyReason: EmptyReason?,
        val isLoading: Boolean,
    )
}
