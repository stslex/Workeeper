// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Settings
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl

internal typealias SettingsStoreProcessor = StoreProcessor<State, Action, Event>

internal class SettingsFeature(
    private val factory: SettingsGraph.Factory,
) : Feature<SettingsStoreProcessor, Settings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): SettingsStoreProcessor {
        return rememberMetroStoreProcessor<SettingsStoreImpl> {
            factory
                .createSettingsGraph()
                .settingsStore
        } as SettingsStoreProcessor
    }
}
