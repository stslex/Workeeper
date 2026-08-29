// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Screen.ExerciseImageRequest
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerScope
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State

@SingleIn(ImageViewerScope::class)
internal class ClickHandler @Inject constructor(
    store: ImageViewerHandlerStore,
) : Handler<Action.Click>, ImageViewerHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBack()
            Action.Click.OnDoubleTap -> processDoubleTap()
            Action.Click.OnMenuClick -> processMenuClick()
            Action.Click.OnSheetDismiss -> processSheetDismiss()
            Action.Click.OnReplaceClick -> processRequest(ExerciseImageRequest.REPLACE)
            Action.Click.OnRemoveClick -> processRequest(ExerciseImageRequest.REMOVE)
        }
    }

    /**
     * GUARD: keep this check even though `editable` already hides the `⋮` — a `Menu` sheet on a
     * non-editable route stages edits the caller cannot save.
     */
    private fun processMenuClick() {
        if (state.value.editable.not()) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(sheetState = State.SheetState.Menu) }
    }

    private fun processSheetDismiss() {
        updateState { it.copy(sheetState = State.SheetState.Hidden) }
    }

    /**
     * Both verbs pop with a REQUEST and perform nothing; the editor owns the image lifecycle. The
     * sheet closes in the same transition as the pop, never left for the dismiss to catch.
     */
    private fun processRequest(request: ExerciseImageRequest) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(sheetState = State.SheetState.Hidden) }
        consume(Action.Navigation.BackWithRequest(request))
    }

    private fun processBack() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.Back)
    }

    private fun processDoubleTap() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
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
