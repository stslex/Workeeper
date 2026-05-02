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
     * Subscribes to a combined detail + PR-set-uuid flow. Detail is re-fetched in the
     * interactor on every PR re-emission so optimistic edits aren't clobbered by stale
     * captured detail. UI mapping happens in the collector body — keeping
     * `updateStateImmediate` reduced to pure state transformation per the State mutation
     * discipline rule.
     */
    private fun processInit() {
        val sessionUuid = state.value.sessionUuid
        updateState { it.copy(phase = State.Phase.Loading) }
        interactor.observeDetailWithPrs(sessionUuid).launch(
            onError = { _ ->
                updateStateImmediate {
                    it.copy(phase = State.Phase.Error(ErrorType.LoadFailed))
                }
            },
        ) { result ->
            val phase = result?.let {
                State.Phase.Loaded(detail = it.detail.toUi(resourceWrapper, it.prSetUuids))
            } ?: State.Phase.Error(ErrorType.SessionNotFound)
            updateStateImmediate { current -> current.copy(phase = phase) }
        }
    }
}
