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
    ) : Store.State {

        /**
         * The picture's two verbs, which arrived here with §26's "The image moves into the pushed
         * top bar". Sealed and Store-homed rather than a `Boolean` + `remember`, per Rule 4 of
         * compose-state-discipline: the screen has one modal today and a second one added later
         * must be unrepresentable alongside it, not merely absent.
         */
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

            fun create(model: String): State = State(
                model = model,
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
             * Pop carrying what the user asked for. The viewer performs neither verb — the
             * editor owns the permission plumbing, the temp-URI dance and the uncommitted
             * `PendingImage` — so this is a REQUEST and not a result.
             */
            data class BackWithRequest(val request: ExerciseImageRequest) : Navigation
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
