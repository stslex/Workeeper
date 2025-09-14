package io.github.stslex.workeeper.feature.charts.ui.mvi.model

internal sealed interface CalendarState {

    data object Closed : CalendarState

    sealed interface Opened : CalendarState {

        data object StartDate : Opened

        data object EndDate : Opened
    }

    val isOpened: Boolean
        get() = this is Opened
}