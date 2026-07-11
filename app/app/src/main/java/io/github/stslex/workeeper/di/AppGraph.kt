// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder

/**
 * The Metro app-scope dependency graph (KMP C.1 app-collapse Phase 1 — leaf E-proof).
 *
 * Stood up ALONGSIDE `@HiltAndroidApp` (a second dual-path, now at the app-scope tier — the
 * mirror of the feature-tier dual-path shipped 13 times). Held by `BaseApplication` for the whole
 * process. Factory-shaped ([Factory]) per the locked C decision: the app `Context` enters as a
 * `@Provides` bound instance via `create(...)`, so nothing reads Hilt's `@ApplicationContext`
 * through this graph and the graph interface stays small.
 *
 * OWNS exactly one app-scoped binding in this leaf spike: [AnalyticsHolder], constructed and
 * retained by the graph (`@Provides @SingleIn(AppScope)`). `AnalyticsHolder`'s `@Inject`/`@Singleton`
 * were stripped so Hilt no longer auto-binds it (single-owner). Hilt-side readers — the 13
 * `*HiltEntryPoint.analyticsHolder()` accessors — resolve it through a delegating Hilt `@Provides`
 * ([AppGraphAdoptBackModule]) that returns THIS graph's instance, never a parallel one. That
 * delegating read is the adopt-back seam this phase proves identity-preserving.
 */
@DependencyGraph(scope = AppScope::class)
internal interface AppGraph {

    /** Root accessor: the single app-scoped [AnalyticsHolder] the adopt-back `@Provides` delegates to. */
    val analyticsHolder: AnalyticsHolder

    /**
     * App-Scope Collapse Step 3 (SB1). Metro-owned [NumUiUtils] — CONTRIBUTED by
     * `@ContributesBinding(AppScope::class)` on `NumUiUtilsImpl` in its own module (`core:ui:kit`),
     * which `@DependencyGraph(AppScope::class)` auto-aggregates. No `@Provides` here: the impl is
     * `internal` to `core:ui:kit` and app/app cannot reference it, so ownership lives at the impl via
     * contribution (the visibility-respecting Metro mechanic). CLEAN migration: no app-scope Hilt
     * consumer, so no adopt-back `@Provides`; this accessor exposes the binding for identity tests.
     */
    val numUiUtils: NumUiUtils

    /**
     * Metro CONSTRUCTS and retains the leaf. `@SingleIn(AppScope)` binds it to this graph's
     * lifetime — i.e. the process — the exact lifetime Hilt's `@Singleton` gave. This is the first
     * app-scoped binding Metro *owns* (features only ever ADOPTED Hilt-owned singletons in).
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalyticsHolder(): AnalyticsHolder = AnalyticsHolder()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            // PLAIN Context bound instance (locked C shape). Unused by the leaf, but fixes the graph
            // shape so the bulk migration adds AppDatabase/etc. as siblings without reshaping create().
            @Provides applicationContext: Context,
        ): AppGraph
    }
}
