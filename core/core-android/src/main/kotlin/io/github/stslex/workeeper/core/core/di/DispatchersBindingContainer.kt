// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * App-Scope Collapse Step 3 (Phase PF commit 1). The four app CoroutineDispatchers, moved out of Hilt's
 * `CoreModule` into a Metro provides-factory container — the FIRST production `@BindingContainer` (the
 * mechanic proven read-only in the PF spike + guarded by `ContributesToScopeRule`).
 *
 * `@BindingContainer @ContributesTo(AppScope)` makes these Metro-owned: the app `@DependencyGraph(AppScope)`
 * (`AppGraph`) auto-aggregates them cross-module, so `StoreDispatchers` (and every other Metro consumer)
 * resolves its qualified `CoroutineDispatcher` deps directly from the graph. This RETIRES the transient
 * `@DefaultDispatcher` / `@MainImmediateDispatcher` `create()`-param bridge (`AppGraph.Factory.create`,
 * `buildAppGraph`, `AppGraphSourceModule.provideAppGraph`, `BaseApplication.DispatcherBridgeEntryPoint`) —
 * keeping it would be a `[Metro/DuplicateBinding]`, and adopt-back-feeding it would be a construction cycle
 * (`provideAppGraph` → dispatcher → adopt-back → `appGraph` → `provideAppGraph`).
 *
 * PUBLIC (object + funcs): `@ContributesTo` on an `internal` container silently fails to aggregate
 * cross-Gradle-module (PF.0 gate, `nonPublicContributionSeverity` default NONE) — the same visibility rule
 * as `@ContributesBinding`. The javax `@Qualifier`s survive into the graph via `includeJavax`.
 *
 * Still-Hilt consumers (feature `*HiltEntryPoint` bridges + pure-Hilt `@Inject` interactors / handlers /
 * `core:data` repositories) read these from Hilt's `SingletonComponent`, so they resolve through the three
 * qualified adopt-back `@Provides` in `AppGraphAdoptBackModule` (`@Default` / `@MainImmediate` / `@IO`).
 * `@Main` is migrated for completeness but has ZERO consumers — no adopt-back shim.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object DispatchersBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @SingleIn(AppScope::class)
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @SingleIn(AppScope::class)
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @SingleIn(AppScope::class)
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
}
