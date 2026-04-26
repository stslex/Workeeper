// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Archive
import io.github.stslex.workeeper.feature.settings.mvi.handler.ArchiveComponent
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStoreImpl

internal typealias ArchiveStoreProcessor = StoreProcessor<State, Action, Event>

internal object ArchiveFeature : Feature<ArchiveStoreProcessor, Archive, ArchiveComponent>() {

    @Composable
    override fun processor(
        screen: Archive,
    ): ArchiveStoreProcessor = createProcessor<ArchiveStoreImpl, ArchiveStoreImpl.Factory>(screen)
}
