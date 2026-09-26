package io.github.stslex.workeeper.wear.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.wear.di.WearScope
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.runtime.runWearRuntimeUiEvent
import kotlinx.coroutines.flow.Flow

internal interface WearInteractor {
    val snapshots: Flow<WatchRuntimeSnapshot>
    fun current(): WatchRuntimeSnapshot
    fun perform(action: ControllerAction)
}

@SingleIn(WearScope::class)
internal class WearInteractorImpl @Inject constructor(
    private val runtime: WatchRuntime,
) : WearInteractor {
    override val snapshots: Flow<WatchRuntimeSnapshot> = runtime.snapshot
    override fun current(): WatchRuntimeSnapshot = runtime.snapshot.value
    override fun perform(action: ControllerAction) {
        runWearRuntimeUiEvent { runtime.onAction(action) }
    }
}
