package io.github.stslex.workeeper.feature.charts.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.coroutine.asyncMap
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.domain.interactor.ChartsInteractor
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartParamsMapper
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartResultsMapper
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
                .mapLatest { params ->
                    updateStateImmediate { it.copy(chartState = ChartsState.Loading) }
                    delay(DEFAULT_QUERY_DELAY) // To prevent too many requests when user change params fast.
                    logger.d { "New chart params: $params" }
                    interactor.getChartsData(params)
                },
        ) { items ->
            logger.d { "Charts items: $items" }

            val mappedItems = items
                .asyncMap(chartResultsMapper::invoke)
                .toImmutableList()

            logger.d { "mapped chart items: $mappedItems" }

            val selectedChartIndex = state.value.chartState.content?.selectedChartIndex ?: 0
            updateStateImmediate { state ->
                state.copy(
                    chartState = if (mappedItems.isEmpty()) {
                        ChartsState.Empty
                    } else {
                        ChartsState.Content(
                            charts = mappedItems,
                            chartsTitles = mappedItems.map { it.name }.toImmutableList(),
                            selectedChartIndex = selectedChartIndex,
                        )
                    },
                )
            }
            sendEvent(
                ChartsStore.Event.ScrollChartHeader(
                    chartIndex = selectedChartIndex,
                    animated = false,
                    force = true,
                ),
            )
        }
    }

    companion object {

        private const val DEFAULT_QUERY_DELAY = 600L
    }
}
