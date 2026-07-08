// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import javax.inject.Singleton

/**
 * ADOPT-BACK seam (KMP C.1 app-collapse Phase 1 — leaf E-proof). The direction-mirror of the batch
 * bridge: the batch is Metro-reads-Hilt (features pull app-scoped `@Singleton`s via `*HiltEntryPoint`);
 * adopt-back is Hilt-reads-Metro — a still-Hilt-owned consumer resolves a now-Metro-owned binding
 * through a thin Hilt `@Provides` that DELEGATES to the app-graph accessor.
 *
 * The graph is obtained through the [AppGraphOwner] interface ([provideAppGraph]) — NEVER by casting
 * the Application to a concrete `BaseApplication`. That decoupling is a mechanic fix: the Hilt test
 * harness swaps in `HiltTestApplication` (no `BaseApplication`), so a concrete cast would crash every
 * `@HiltAndroidTest` transitively resolving a migrated binding. Tests `@TestInstallIn`-replace
 * [provideAppGraph] with a test-built graph.
 *
 * SINGLE-OWNER DISCIPLINE: [provideAnalyticsHolder] returns `appGraph.analyticsHolder` — the SAME
 * instance the Metro graph constructed and retains (`@SingleIn(AppScope)`). It NEVER constructs a
 * parallel Hilt-side `AnalyticsHolder`. `AnalyticsHolder`'s `@Inject`/`@Singleton` were stripped so
 * this is the ONLY Hilt binding for the type — no duplicate binding, no second owner (the
 * double-instance `===`-split class). Every one of the 13 `*HiltEntryPoint.analyticsHolder()`
 * accessors resolves through this provider.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppGraphAdoptBackModule {

    /**
     * Bridges the Metro [AppGraph] into Hilt as a single `@Singleton` instance, read through the
     * [AppGraphOwner] interface off the app context. In instrumented tests this provider is replaced
     * via `@TestInstallIn` with one that builds a test [AppGraph] — so no `BaseApplication` is needed.
     */
    @Provides
    @Singleton
    fun provideAppGraph(
        @ApplicationContext context: Context,
    ): AppGraph = (context.applicationContext as AppGraphOwner).appGraph

    /**
     * Adopt-back for the leaf: delegate to the graph's owned instance. `@Singleton` caches the
     * delegate in `SingletonComponent`, but since the target is a graph singleton the value === the
     * graph's either way.
     */
    @Provides
    @Singleton
    fun provideAnalyticsHolder(
        appGraph: AppGraph,
    ): AnalyticsHolder = appGraph.analyticsHolder
}
