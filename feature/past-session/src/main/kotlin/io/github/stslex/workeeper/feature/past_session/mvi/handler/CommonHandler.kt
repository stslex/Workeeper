// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.past_session.di.PastSessionHandlerStore
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor
import io.github.stslex.workeeper.feature.past_session.mvi.mapper.toUi
import io.github.stslex.workeeper.feature.past_session.mvi.model.ErrorType
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: PastSessionInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: PastSessionHandlerStore,
) : Handler<Action.Common>, PastSessionHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
        }
    }

    /**
     * Subscribes to a combined detail + PR-map flow. The detail load is one-shot at
     * subscription; the PR map is reactive — every Room invalidation re-emits a new map and
     * the mapper recomputes `isPersonalRecord` per set without re-fetching detail. Edits
     * apply optimistically through the input handler, so the snapshot stays consistent
     * between PR re-emissions.
     */
    private fun processInit() {
        val sessionUuid = state.value.sessionUuid
        updateState { it.copy(phase = State.Phase.Loading) }
        scope.launch(
            flow = interactor.observeDetailWithPrs(sessionUuid),
            onError = { _ ->
                updateStateImmediate {
                    it.copy(phase = State.Phase.Error(ErrorType.LoadFailed))
                }
            },
        ) { result ->
            updateStateImmediate { current ->
                if (result == null) {
                    current.copy(phase = State.Phase.Error(ErrorType.SessionNotFound))
                } else {
                    current.copy(
                        phase = State.Phase.Loaded(
                            detail = result.detail.toUi(resourceWrapper, result.prMap),
                        ),
                    )
                }
            }
        }
    }
}
