// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import javax.inject.Singleton

/**
 * ADOPT-BACK seam (KMP C.1 app-collapse Phase 1 — leaf E-proof). The direction-mirror of the batch
 * bridge: the batch is Metro-reads-Hilt (features pull app-scoped `@Singleton`s via `*HiltEntryPoint`);
 * adopt-back is Hilt-reads-Metro — a still-Hilt-owned consumer resolves a now-Metro-owned binding
 * through a thin Hilt `@Provides` that DELEGATES to the app-graph accessor.
 *
 * The `AppGraph` binding these shims consume is provided by [AppGraphSourceModule] — the SINGLE unit
 * that reaches the [BaseApplication]-held graph (or builds the real one under test). This module holds
 * ONLY the delegating shims and is NEVER `@TestInstallIn`-replaced, so tests exercise the REAL
 * delegation (Phase D2 decouple: previously the whole module was replaced by a hand-copied test double,
 * so the `===` proof exercised the copy, not the production shim).
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
