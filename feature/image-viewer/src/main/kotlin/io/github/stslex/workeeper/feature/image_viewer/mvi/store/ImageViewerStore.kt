// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store

internal interface ImageViewerStore :
    Store<ImageViewerStore.State, ImageViewerStore.Action, ImageViewerStore.Event> {

    @Stable
    data class State(
        val model: String,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
    ) : Store.State {

        companion object {

            const val MIN_SCALE: Float = 1f
            const val MAX_SCALE: Float = 5f
            const val DOUBLE_TAP_TARGET_SCALE: Float = 2.5f

            fun create(model: String): State = State(
                model = model,
                scale = MIN_SCALE,
                offsetX = 0f,
                offsetY = 0f,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {

            data object OnBackClick : Click

            data object OnDoubleTap : Click
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
        }
    }

    @Stable
    sealed interface Event : Store.Event {

        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
