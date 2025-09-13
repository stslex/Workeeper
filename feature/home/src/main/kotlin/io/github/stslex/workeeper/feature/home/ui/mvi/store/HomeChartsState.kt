package io.github.stslex.workeeper.feature.home.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.exercise.model.DateProperty
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class HomeChartsState(
    val name: String,
    val charts: ImmutableList<SingleChartUiModel>,
    val startDate: DateProperty,
    val endDate: DateProperty,
    val calendarState: CalendarState
) {

    companion object {

        val INITIAL: HomeChartsState = HomeChartsState(
            name = "",
            charts = persistentListOf(),
            startDate = DateProperty.new(System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)), // 7 days default
            endDate = DateProperty.new(System.currentTimeMillis()),
            calendarState = CalendarState.Closed
        )
    }
}

@Stable
data class SingleChartUiModel(
    val name: String,
    val properties: List<Double>,
)

sealed interface CalendarState {

    data object Closed : CalendarState

    sealed interface Opened : CalendarState {

        data object StartDate : Opened

        data object EndDate : Opened
    }

    val isOpened: Boolean
        get() = this is Opened
}
