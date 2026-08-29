// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.ui.navigation.Screen.ExerciseImageRequest

interface ImageViewerStore :
    Store<ImageViewerStore.State, ImageViewerStore.Action, ImageViewerStore.Event> {

    @Stable
    data class State(
        val model: String,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val sheetState: SheetState,
        /**
         * Whether the CALLER can honour a replace/remove request; the `⋮` is drawn only when true.
         * Stated by the caller on the route. See documentation/feature-specs/v3-redesign-spec.md.
         */
        val editable: Boolean,
    ) : Store.State {

        /** The picture's two verbs, Store-homed per Rule 4 of compose-state-discipline. */
        @Stable
        sealed interface SheetState {

            @Stable
            data object Hidden : SheetState

            /** «Заменить» · «Удалить» — the sheet the trailing `⋮` opens. */
            @Stable
            data object Menu : SheetState
        }

        companion object {

            const val MIN_SCALE: Float = 1f
            const val MAX_SCALE: Float = 5f
            const val DOUBLE_TAP_TARGET_SCALE: Float = 2.5f

            fun create(model: String, editable: Boolean): State = State(
                model = model,
                editable = editable,
                scale = MIN_SCALE,
                offsetX = 0f,
                offsetY = 0f,
                sheetState = SheetState.Hidden,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {

            data object OnBackClick : Click

            data object OnDoubleTap : Click

            /** Trailing `⋮` — opens the two-verb sheet. */
            data object OnMenuClick : Click

            data object OnSheetDismiss : Click

            data object OnReplaceClick : Click

            data object OnRemoveClick : Click
        }

        sealed interface Common : Action {

            data object Init : Common

            data class TransformChange(
                val scale: Float,
                val offsetX: Float,
                val offsetY: Float,
            ) : Common
        }

        sealed interface Navigation : Action {

            data object Back : Navigation

            /**
             * Pop carrying what the user asked for: a REQUEST, not a result — the editor owns the
             * permission plumbing and the uncommitted `PendingImage`.
             */
            data class BackWithRequest(val request: ExerciseImageRequest) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
