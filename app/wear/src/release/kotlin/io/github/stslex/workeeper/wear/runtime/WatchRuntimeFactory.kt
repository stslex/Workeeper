// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.content.Context
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.state.WatchProcessState
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.ControllerAction
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
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
    private val mutableSurface = MutableStateFlow(WatchProcessState.currentSurface())
    override val surface: StateFlow<WearSurfaceModel> = mutableSurface.asStateFlow()
    override val ongoingStatus: StateFlow<OngoingStatus> =
        MutableStateFlow<OngoingStatus>(OngoingStatus.Inactive).asStateFlow()

    override fun currentSurface(): WearSurfaceModel = onWake()

    @Synchronized
    override fun onWake(): WearSurfaceModel {
        val source = WatchProcessState.currentSurface()
        return source.copy(
            kind = if (source.kind == WearSurfaceKind.ACTIVE) WearSurfaceKind.REFRESH_REQUIRED else source.kind,
            controlsEnabled = false,
            completeEnabled = false,
            retryEnabled = false,
            completionUnavailableReason = if (source.controlsVisible) {
                CompletionUnavailableReason.REFRESH_REQUIRED
            } else {
                null
            },
            hasUnsubmittedDraft = false,
            selectedLocale = locale,
        ).also { mutableSurface.value = it }
    }

    override fun onAction(action: ControllerAction): WatchActionResult = WatchActionResult.Rejected

    @Synchronized
    override fun setLocale(locale: Locale) {
        this.locale = locale
        onWake()
    }
}
