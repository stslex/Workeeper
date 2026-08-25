// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Settings
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl

internal typealias SettingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/settings resolves its Store through the Metro graph-extension path; `Context` reaches
 * only the `AppDepsHolder` seam, never the graph. See documentation/graph-extension-arc/HANDOFF.md.
 */
internal object SettingsFeature : Feature<SettingsStoreProcessor, Settings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): SettingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SettingsStoreImpl> {
            context.appDeps<SettingsGraph.Factory>()
                .createSettingsGraph()
                .settingsStore
        } as SettingsStoreProcessor
    }
}
