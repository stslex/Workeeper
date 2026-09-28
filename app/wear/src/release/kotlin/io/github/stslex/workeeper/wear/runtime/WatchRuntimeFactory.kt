// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.content.Context
import io.github.stslex.workeeper.wear.state.WatchProcessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** The release source remains read-only until the privacy/transport integration gate is approved. */
internal object WatchRuntimeFactory {
    private val runtime = ReadOnlyWatchRuntime()

    fun get(context: Context): WatchRuntime = runtime.also {
        it.setLocale(context.resources.configuration.locales[0])
    }

    fun handleDebugScenario(context: Context, scenario: String): Boolean = false
}

private class ReadOnlyWatchRuntime : WatchRuntime {
    private var locale = Locale.getDefault()
    private val mutableSnapshot = MutableStateFlow(
        WatchRuntimeSnapshot(workout = WatchProcessState.currentState(), readOnly = true),
    )
    override val snapshot: StateFlow<WatchRuntimeSnapshot> = mutableSnapshot.asStateFlow()

    @Synchronized
    override fun onWake(): WatchRuntimeSnapshot {
        WatchProcessState.expireAuthority()
        return WatchRuntimeSnapshot(
            workout = WatchProcessState.currentState(),
            readOnly = true,
            locale = locale,
        ).also { mutableSnapshot.value = it }
    }

    override fun onAction(action: ControllerAction): WatchActionResult = WatchActionResult.Rejected

    @Synchronized
    override fun setLocale(locale: Locale) {
        this.locale = locale
        onWake()
    }
}
