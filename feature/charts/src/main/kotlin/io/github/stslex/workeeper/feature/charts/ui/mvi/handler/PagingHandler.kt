package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.CHARTS_SCOPE_NAME
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ExerciseChartMap
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [PagingHandler::class])
@Scope(name = CHARTS_SCOPE_NAME)
internal class PagingHandler(
    private val repository: ExerciseRepository,
    private val commonStore: CommonDataStore,
    private val mapper: ExerciseChartMap,
    @Named(CHARTS_SCOPE_NAME) store: ChartsHandlerStore,
) : Handler<Action.Paging>, ChartsHandlerStore by store {

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> processInit()
        }
    }

    private fun processInit() {
        subscribeToDates()
        subscribeToCharts()
    }

    private fun subscribeToDates() {
        scope.launch(
            commonStore.homeSelectedStartDate.filterNotNull()
        ) { timestamp ->
            updateStateImmediate { it.copy(startDate = DateProperty.new(timestamp)) }
        }
        scope.launch(
            commonStore.homeSelectedEndDate.filterNotNull()
        ) { timestamp ->
            updateStateImmediate { it.copy(endDate = DateProperty.new(timestamp)) }
        }
    }

    private fun subscribeToCharts() {
        scope.launch(
            state
                .map {
                    Triple(it.startDate, it.endDate, it.name)
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
        ) { items ->
            logger.d("Charts items: ${items.size}")
            updateState { it.copy(charts = mapper(items).toImmutableList()) }
        }
    }
}
