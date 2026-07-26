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
 * feature/settings resolves its Store through the Metro **graph-extension** path.
 *
 * The app-scope graph (returned as `Any` by the `AppDepsHolder` seam) IS the parent graph, and once
 * `:app` is compiled it implements the contributed [SettingsGraph.Factory]. `appDeps<T>()` re-narrows it
 * with its `as T` cast — the same acquisition seam as before, now targeting the contributed factory
 * instead of the three `XxxDeps` interfaces. (`asContribution<T>()` is not usable here: it requires a
 * statically `@DependencyGraph`-typed receiver, which the `Any` seam is not.)
 *
 * This is where the arc's widest hand-threading collapses: the old body read three dep interfaces, plus
 * `appDialogPublisher` through a now-deleted `Application`-cast holder seam in `app-dialogs/api` and
 * the app `Context` from `LocalContext`, then passed all **18** across explicitly. Every one of them is
 * an app-scoped binding the extension now inherits, so `createSettingsGraph()` takes no arguments and
 * the composition-sourced/graph-sourced split disappears entirely — `Context` still comes from
 * `LocalContext`, but only to reach the `AppDepsHolder` seam, never as a graph argument.
 *
 * The extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it is built at
 * most once per retained [SettingsStoreImpl] (per `NavBackStackEntry` `ViewModelStore`) — binding the
 * extension and its `@SingleIn(SettingsScope)` nodes to exactly the Store's lifetime.
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
