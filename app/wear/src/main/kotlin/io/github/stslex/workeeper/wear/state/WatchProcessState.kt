// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import android.os.SystemClock
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sole watch-process owner observed by Tile and activity; process recreation starts read-only. */
internal object WatchProcessState {
    private val reducer = WatchWorkoutReducer()
    private val mutableState = MutableStateFlow(reducer.state)

    val state: StateFlow<WatchReducerState> = mutableState

    fun currentState(): WatchReducerState = mutableState.value

    @MainThread
    fun expireAuthority(nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
        reducer.expireAuthority(nowElapsedRealtimeMs)
        mutableState.value = reducer.state
    }
}
