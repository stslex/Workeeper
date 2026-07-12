// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.ui.kit.utils.NumUiUtils
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import kotlinx.coroutines.CoroutineDispatcher

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
     * App-Scope Collapse Step 3 (SB1, core:ui:mvi slice). Metro-owned [LoggerHolder] — a concrete
     * self-bound class (no interface), so it carries `@SingleIn(AppScope)` + `@Inject` (NOT
     * `@ContributesBinding`, which binds to a supertype); THIS accessor pulls it into the graph as a
     * retained singleton. The 13 `*HiltEntryPoint.loggerHolder()` readers + the `BaseStore` ctor param
     * resolve it via the single adopt-back `@Provides` ([AppGraphAdoptBackModule]) delegating here.
     */
    val loggerHolder: LoggerHolder

    /**
     * App-Scope Collapse Step 3 (SB1, core:ui:mvi slice). Metro-owned [StoreDispatchers]. Its two
     * `CoroutineDispatcher` ctor deps are the collider set, still Hilt-owned at this layer, so they are
     * bridged into [Factory.create] as qualified bound instances (`@DefaultDispatcher` /
     * `@MainImmediateDispatcher` survive via `includeJavax`) until core:core-android is migrated.
     */
    val storeDispatchers: StoreDispatchers

    /**
     * App-Scope Collapse Step 3 (SB1). Metro-owned [ActivityHolder] + [ActivityHolderProducer] — the same
     * `ActivityHolderImpl` (one `@SingleIn(AppScope)` retained instance) contributes BOTH via repeatable
     * `@ContributesBinding`. `ActivityHolder` is read by the still-Hilt `ResourceManagerImpl` (L1) and
     * `ActivityHolderProducer` by `MainActivity`, both via the adopt-back `@Provides`.
     */
    val activityHolder: ActivityHolder
    val activityHolderProducer: ActivityHolderProducer

    /**
     * App-Scope Collapse Step 3 (SB1, backup/scheduling slice). Metro-owned [BackupPreferencesRepository]
     * — CONTRIBUTED by `@ContributesBinding(AppScope)` on the (now public) `BackupPreferencesRepositoryImpl`
     * in its own module; `@DependencyGraph` auto-aggregates it. Its `Context` ctor dep resolves from the
     * `create(applicationContext)` bound instance. This accessor exposes the binding for the adopt-back
     * `@Provides` + identity tests; `SettingsHiltEntryPoint` + `BackupWorkerHiltEntryPoint` delegate here.
     */
    val backupPreferencesRepository: BackupPreferencesRepository

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
            // App-Scope Collapse Step 3 (SB1, mvi slice): the 2 collider dispatchers StoreDispatchers
            // needs are still Hilt-owned (CoreModule, core:core-android) at this layer, so they are
            // bridged in as QUALIFIED bound instances (includeJavax carries the qualifiers). Transient —
            // dropped from create() when core:core-android migrates the dispatchers to the graph.
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            @Provides @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
        ): AppGraph
    }
}
