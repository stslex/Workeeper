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
                        // loadChart owns the false transition when it is about to fire.
                        isLoading = result.isLoading,
                    )
                }
                result.selected?.also { selected ->
                    loadChart(selected)
                }
            },
            // GUARD: `HandlerStore.launch` defaults `onError` to `{}`, and a swallowed throw
            // leaves the screen on `Content.Loading` forever — resolve to LOAD_FAILED instead.
            onError = { resolveToLoadFailure() },
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

    /** Re-fetch and bucket the chart for [exercise]; [ClickHandler] calls it on every change. */
    fun loadChart(exercise: ExercisePickerItemUiModel) {
        val current = state.value
        val metric = current.metric.toDomain()
        val type = exercise.type.toDomain()
        // Two staleness guards: cancel the previous load, and stamp the response with its request.
        val request = ChartRequest(
            exerciseUuid = exercise.uuid,
            preset = current.preset,
            metric = current.metric,
        )
        loadJob?.cancel()
        loadJob = launchDefault(
            onSuccess = { result ->
                if (state.value.requestOf() != request) return@launchDefault
                // Rule 1: mapping happens here; the lambda only copies State.
                val prior = state.value
                val newPoints = result.toUiPoints()
                // §4.8: below two points there is no line, so sub-threshold is an empty state.
                val subThreshold = newPoints.size < State.MIN_CHART_POINTS
                // Session identity, not day: duplicate-day points must not transfer the scrub.
                val sameSessions = prior.points.map(ChartPointUiModel::sessionUuid) ==
                    newPoints.map(ChartPointUiModel::sessionUuid)
                val activeIndex = if (subThreshold) {
                    null
                } else {
                    prior.activeIndex
                        ?.takeIf { index -> sameSessions && index in newPoints.indices }
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
            // Same guard as processInit; the staleness check is repeated because
            // `AppCoroutineScopeImpl`'s `runCatching` catches CancellationException too.
            onError = {
                if (state.value.requestOf() != request) return@launchDefault
                resolveToLoadFailure()
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

    /** Resolves the screen to LOAD_FAILED — the one empty reason whose recovery is a retry. */
    private suspend fun resolveToLoadFailure() {
        updateStateImmediate { current ->
            current.copy(
                emptyReason = EmptyReason.LOAD_FAILED,
                isLoading = false,
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
