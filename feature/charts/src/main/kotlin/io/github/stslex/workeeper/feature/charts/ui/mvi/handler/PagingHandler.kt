package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractor
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartParamsMapper
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartResultsMapper
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class PagingHandler @Inject constructor(
    private val interactor: ChartsInteractor,
    private val commonStore: CommonDataStore,
    private val chartParamsMapper: ChartParamsMapper,
    private val chartResultsMapper: ChartResultsMapper,
    store: ChartsHandlerStore,
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
            commonStore.homeSelectedStartDate.filterNotNull(),
        ) { timestamp ->
            updateStateImmediate { it.copy(startDate = PropertyHolder.DateProperty.new(timestamp)) }
        }
        scope.launch(
            commonStore.homeSelectedEndDate.filterNotNull(),
        ) { timestamp ->
            updateStateImmediate { it.copy(endDate = PropertyHolder.DateProperty.new(timestamp)) }
        }
    }

    private fun subscribeToCharts() {
        scope.launch(
            state
                .map(chartParamsMapper::invoke)
                .distinctUntilChanged()
                .map { params -> interactor.getChartsData(params) },
        ) { items ->
            logger.d { "Charts items: $items" }

            val mappedItems = items
                .asyncMap(chartResultsMapper::invoke)
                .toImmutableList()

            logger.d { "mapped chart items: $mappedItems" }

            updateStateImmediate {
                it.copy(charts = mappedItems)
            }
        }
    }
}
