// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.mvi.handler.PastSessionComponent
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Event
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.State
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl

internal typealias PastSessionStoreProcessor = StoreProcessor<State, Action, Event>

internal object PastSessionFeature :
    Feature<PastSessionStoreProcessor, Screen.PastSession, PastSessionComponent>() {

    @Composable
    override fun processor(
        screen: Screen.PastSession,
    ): PastSessionStoreProcessor =
        createProcessor<PastSessionStoreImpl, PastSessionStoreImpl.Factory>(screen)
}
