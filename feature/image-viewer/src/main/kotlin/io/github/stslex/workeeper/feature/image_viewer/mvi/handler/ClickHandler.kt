// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import javax.inject.Inject

@ViewModelScoped
internal class ClickHandler @Inject constructor(
    store: ImageViewerHandlerStore,
) : Handler<Action.Click>, ImageViewerHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnDoubleTap -> processDoubleTap()
        }
    }

    private fun processBack() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.Back)
    }

    private fun processDoubleTap() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            // Toggle: any zoomed-in scale collapses to MIN_SCALE; otherwise jump to the
            // double-tap target. Pan resets only when collapsing — otherwise the target
            // stays centered (offset 0,0) so the user can pan from a known origin.
            if (current.scale > State.MIN_SCALE) {
                current.copy(
                    scale = State.MIN_SCALE,
                    offsetX = 0f,
                    offsetY = 0f,
                )
            } else {
                current.copy(
                    scale = State.DOUBLE_TAP_TARGET_SCALE,
                    offsetX = 0f,
                    offsetY = 0f,
                )
            }
        }
    }
}
