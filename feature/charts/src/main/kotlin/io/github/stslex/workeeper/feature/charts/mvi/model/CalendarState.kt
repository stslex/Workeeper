package io.github.stslex.workeeper.feature.charts.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal sealed interface CalendarState {

    data object Closed : CalendarState

    sealed interface Opened : CalendarState {

        data object StartDate : Opened

        data object EndDate : Opened
    }
}
