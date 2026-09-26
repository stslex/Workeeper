package io.github.stslex.workeeper.wear.mvi.store

import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.ui.WearOngoingNotice
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel

internal interface WearStore : Store<WearStore.State, WearStore.Action, WearStore.Event> {
    data class State(
        val presentation: WearPresentation = WearPresentation(),
        val platform: WearPlatformState = WearPlatformState(),
        val editor: NumericField? = null,
    ) : Store.State

    sealed interface Action : Store.Action {
        sealed interface Common : Action {
            data object Init : Common
            data object RuntimeChanged : Common
            data class PlatformChanged(val value: WearPlatformState) : Common {
                override fun toString(): String = "PlatformChanged"
            }
            data class PresentationChanged(val value: WearPresentation) : Common {
                override fun toString(): String = "PresentationChanged"
            }
        }
        sealed interface Click : Action {
            data class Edit(val field: NumericField) : Click
            data object Complete : Click
            data object Retry : Click
            data object EnableNotifications : Click
        }
        sealed interface Input : Action {
            data class Draft(val field: NumericField, val steps: Int) : Input {
                override fun toString(): String = "Draft"
            }
        }
        sealed interface Navigation : Action {
            data object CloseEditor : Navigation
        }
    }

    sealed interface Event : Store.Event {
        data object EnableNotificationsRequested : Event
    }
}

internal data class WearPlatformState(
    val ambient: WearAmbientState = WearAmbientState(),
    val notificationsEnabled: Boolean = true,
    val previewId: String? = null,
)

internal data class WearPresentation(
    val model: WearSurfaceModel = WearSurfaceModel(WearSurfaceKind.LOADING),
    val ambient: WearAmbientState = WearAmbientState(),
    val notice: WearOngoingNotice? = null,
    val preview: Boolean = false,
)
