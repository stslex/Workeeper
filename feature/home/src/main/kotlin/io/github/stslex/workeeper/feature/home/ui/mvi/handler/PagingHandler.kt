package io.github.stslex.workeeper.feature.home.ui.mvi.handler

import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.exercise.data.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.store.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.home.di.HOME_SCOPE_NAME
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseChartMap
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.home.ui.model.toUi
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [PagingHandler::class])
@Scope(name = HOME_SCOPE_NAME)
internal class PagingHandler(
    private val repository: ExerciseRepository,
    private val commonStore: CommonDataStore,
    private val mapper: ExerciseChartMap,
    private val dispatcher: AppDispatcher,
    @Named(HOME_SCOPE_NAME) store: HomeHandlerStore,
) : Handler<HomeStore.Action.Paging>, HomeHandlerStore by store {

    private val queryState = MutableStateFlow("")

    val processor: PagingUiState<PagingData<ExerciseUiModel>> = PagingUiState {
        queryState.flatMapLatest { query ->
            repository.getExercises(query).map { pagingData ->
                pagingData.map { it.toUi() }
            }
        }
            .flowOn(dispatcher.io)
    }

    override fun invoke(action: HomeStore.Action.Paging) {
        when (action) {
            HomeStore.Action.Paging.Init -> processInit()
        }
    }

    private fun processInit() {
        subscribeToDates()
        subscribeToHomeQuery()
        subscribeToCharts()
    }

    private fun subscribeToDates() {
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

    private fun subscribeToCharts() {
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
                            charts = mapper(items).toImmutableList()
                        )
                    )
                }
            }
    }

    private fun subscribeToHomeQuery() {
        state.map { it.allState.query }.launch { queryState.value = it }
    }
}
