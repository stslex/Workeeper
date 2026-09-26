package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal class RuntimePresentationValue<T>(
    private val source: StateFlow<WatchRuntimeSnapshot>,
    private val project: (WatchRuntimeSnapshot) -> T,
) : Flow<T> by source.map(project) {
    val value: T get() = project(source.value)
}

internal val WatchRuntime.surface: RuntimePresentationValue<WearSurfaceModel>
    get() = RuntimePresentationValue(snapshot, WearSurfaceMapper::map)

internal val WatchRuntime.ongoingStatus: RuntimePresentationValue<OngoingStatus>
    get() = RuntimePresentationValue(snapshot) { it.ongoing }

internal fun WatchRuntime.currentSurface(): WearSurfaceModel = WearSurfaceMapper.map(onWake())
