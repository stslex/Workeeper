// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.compose.runtime.Stable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.ui.mvi.Store

internal interface HomeStore : Store<HomeStore.State, HomeStore.Action, HomeStore.Event> {

    @Stable
    data class State(
        val activeSession: ActiveSessionInfo?,
        val nowMillis: Long,
        val isLoading: Boolean,
    ) : Store.State {

        @Stable
        data class ActiveSessionInfo(
            val sessionUuid: String,
            val trainingUuid: String,
            val trainingName: String,
            val startedAt: Long,
            val doneCount: Int,
            val totalCount: Int,
            val elapsedDurationLabel: String,
        ) {
            fun elapsedMillis(now: Long): Long = (now - startedAt).coerceAtLeast(0L)
        }

        companion object {
            val INITIAL = State(
                activeSession = null,
                nowMillis = 0L,
                isLoading = true,
            )
        }
    }

    @Stable
    sealed interface Action : Store.Action {

        sealed interface Click : Action {
            data object OnActiveSessionClick : Click
            data object OnSettingsClick : Click
        }

        sealed interface Navigation : Action {
            data class OpenLiveWorkout(val sessionUuid: String) : Navigation
            data object OpenSettings : Navigation
        }

        sealed interface Common : Action {
            data object Init : Common
            data object TimerTick : Common
        }
    }

    @Stable
    sealed interface Event : Store.Event {
        data class HapticClick(val type: HapticFeedbackType) : Event
    }
}
