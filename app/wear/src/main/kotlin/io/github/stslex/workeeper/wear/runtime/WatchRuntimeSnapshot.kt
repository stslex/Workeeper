package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.state.WatchReducerState
import java.util.Locale

internal data class WatchRuntimeSnapshot(
    val workout: WatchReducerState = WatchReducerState(),
    val ongoing: OngoingStatus = OngoingStatus.Inactive,
    val noSession: Boolean = false,
    val recoveryRequired: Boolean = false,
    val readOnly: Boolean = false,
    val locale: Locale = Locale.getDefault(),
) {
    override fun toString(): String = "WatchRuntimeSnapshot"
}
