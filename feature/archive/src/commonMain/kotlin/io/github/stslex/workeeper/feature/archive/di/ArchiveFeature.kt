// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Archive
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStore.State
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl

internal typealias ArchiveStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Resolves the Store through the Metro graph-extension path. The extension is created inside the
 * `rememberMetroStoreProcessor` lambda, so its scope is exactly the retained Store's lifetime.
 */
internal class ArchiveFeature(
    private val factory: ArchiveGraph.Factory,
) : Feature<ArchiveStoreProcessor, Archive>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): ArchiveStoreProcessor {
        return rememberMetroStoreProcessor<ArchiveStoreImpl> {
            factory
                .createArchiveGraph()
                .archiveStore
        } as ArchiveStoreProcessor
    }
}
