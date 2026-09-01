// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sole watch-process owner observed by Tile and activity; process recreation starts read-only. */
internal object WatchProcessState {
    private val reducer = WatchWorkoutReducer()
    private val mutableSurface = MutableStateFlow(WearSurfaceMapper.map(reducer.state))

    val surface: StateFlow<WearSurfaceModel> = mutableSurface

    fun currentSurface(): WearSurfaceModel = mutableSurface.value
}
