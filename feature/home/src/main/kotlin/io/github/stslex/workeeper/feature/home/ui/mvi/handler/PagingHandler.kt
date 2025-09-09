package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import android.annotation.SuppressLint
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.data.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.store.store.CommonStore
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.model.toUi
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import io.github.stslex.workeeper.feature.home.ui.mvi.store.SingleChartUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Factory
@Scope(HomeScope::class)
@Scoped
class PagingHandler(
    private val repository: ExerciseRepository,
    private val commonStore: CommonStore,
    private val appDispatcher: AppDispatcher
) : Handler<HomeStore.Action.Paging, HomeHandlerStore> {

    private val queryState = MutableStateFlow("")

    val processor: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        queryState.flatMapLatest { query ->
            repository.getExercises(query).map { pagingData ->
                pagingData.map { it.toUi() }
            }
        }
            .flowOn(appDispatcher.io)
    }

    override fun HomeHandlerStore.invoke(action: HomeStore.Action.Paging) {
        when (action) {
            HomeStore.Action.Paging.Init -> processInit()
        }
    }

    private fun HomeHandlerStore.processInit() {
        subscribeToDates()
        subscribeToHomeQuery()
        subscribeToCharts()
    }

    private fun HomeHandlerStore.subscribeToDates() {
        commonStore.homeSelectedStartDate
            .filterNotNull()
            .launch { timestamp ->
                updateStateImmediate {
                    it.copyCharts(
                        startDate = DateProperty.new(timestamp)
                    )
                }
            }
        commonStore.homeSelectedEndDate
            .filterNotNull()
            .launch { timestamp ->
                updateStateImmediate {
                    it.copyCharts(
                        endDate = DateProperty.new(timestamp)
                    )
                }
            }
    }

    private fun HomeHandlerStore.subscribeToCharts() {
        state
            .map {
                Triple(
                    it.chartsState.startDate,
                    it.chartsState.endDate,
                    it.chartsState.name
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { triple ->
                val (startDate, endDate, name) = triple
                repository.getExercises(
                    name = name,
                    startDate = startDate.timestamp,
                    endDate = endDate.timestamp
                )
            }
            .launch { items ->
                logger.d("Charts items: ${items.size}")
                updateState {
                    it.copy(
                        chartsState = it.chartsState.copy(
                            charts = items.calculateSizes()
                        )
                    )
                }
            }
    }

    private fun HomeHandlerStore.subscribeToHomeQuery() {
        state.map { it.allState.query }.launch { queryState.value = it }
    }
}

internal fun List<ExerciseDataModel>.calculateSizes(
    itemsCount: Int? = null
): ImmutableList<SingleChartUiModel> {
    val mapOfItems = this
        .groupBy { it.name }
        .mapValues { it.value.distinctBy { data -> data.timestamp } }

    val itemsCount = itemsCount ?: mapOfItems.values.maxOfOrNull { it.size } ?: 7
    return mapOfItems.map { item ->
        val (name, items) = item
        val minX = items.minOf { it.timestamp }
        val maxX = items.maxOf { it.timestamp }
        val firstTimeStamp = items.firstOrNull()?.timestamp ?: 0L

        val step = (maxX - minX) / itemsCount

        val properties = mutableListOf<Double>()

        if (items.size < itemsCount) {
            repeat(itemsCount) { i ->
                val startMinX = minX + i * step

                val property = if (firstTimeStamp < startMinX) {
                    0.0
                } else {
                    items
                        .firstOrNull { data ->
                            data.timestamp in startMinX..(minX + (i + 1) * step)
                        }
                        ?.let { it.weight * (it.reps + it.sets) } ?: 0.0
                }

                properties.add(roundPropertyValue(property))
            }
        } else {
            properties.addAll(
                items.map {
                    roundPropertyValue(it.weight * (it.reps + it.sets))
                }
            )
        }

        SingleChartUiModel(
            name = name,
            properties = properties
        )
    }
        .toImmutableList()
}

@SuppressLint("DefaultLocale")
private fun roundPropertyValue(value: Double): Double = if (value >= 1000) {
    (value / 1000.0).let { v -> String.format("%.1f", v).toDouble() }
} else {
    value
}