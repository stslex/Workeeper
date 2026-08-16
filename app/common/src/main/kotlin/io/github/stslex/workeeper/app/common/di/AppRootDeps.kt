// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.common.di

import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.navigation.NavigatorEventBus

/**
 * `app:common`'s dep interface — the exact app-scope types the composition root reads.
 *
 * **Why this exists.** `@DependencyGraph(AppScope::class)` is `internal` to `:app:app`, and
 * `:app:app` depends on `app:common`. That puts this module BELOW the graph, where `AppGraph` and
 * `AppGraphOwner` are not visible by construction. The composition root therefore cannot reach the
 * graph directly at all — it names a contract it owns, and `:app:app` satisfies it.
 *
 * **Why a narrow contract and not a graph extension.** A `@ContributesGraphExtension` models a scope
 * with its own lifetime, which is what the per-screen feature extensions are. The composition root is
 * not a scope: it needs two singletons that already live in `AppScope`. Declaring an extension to
 * hand over two existing bindings would invent a lifetime that models nothing, and would make this
 * module depend on Metro's aggregation machinery — the opposite of what phase 7 needs, since an iOS
 * composition root wires its own way.
 *
 * **Precedent.** This is the shape [RecoveryDeps][io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps]
 * and `BackupWorkerDeps` already use, and it is deliberate: the repo arrived at narrow per-consumer
 * dep interfaces by DELETING a god-object (`AppGraphContract` + its holder + its accessor + module
 * `core:di`). Reintroducing a wide contract here would undo that conclusion.
 *
 * Both types are owned by modules `app:common` depends on directly — `CommonDataStore` →
 * `core:data:dataStore`; [NavigatorEventBus] lives in this module — so no new edge and no cycle.
 */
interface AppRootDeps {

    /** Backs the theme-mode flow `AppRootViewModel` exposes to `AppTheme`. */
    val commonDataStore: CommonDataStore

    /**
     * The one `@SingleIn(AppScope)` navigator. Exposed as its CONCRETE type, matching the accessor
     * `AppGraph` already declared for `App.kt`: the composition root uses all three of its faces at
     * once — `Navigator` to dispatch, `NavigatorReceiver` to collect commands, `NavResultsSource` to
     * carry results — and naming three separate members for one instance would let a future graph
     * satisfy them with three objects, which the result transport cannot survive.
     */
    val navigatorEventBus: NavigatorEventBus
}

/**
 * Held-instance seam for [AppRootDeps]: the process `Application` exposes the app-scope graph typed
 * as [AppRootDeps]. `App()` reads it via `(context.applicationContext as AppRootDepsHolder)`, and
 * the cast is safe by construction because `BaseApplication : AppRootDepsHolder` is compile-visible
 * in `:app:app`.
 *
 * Same typed-point-acquisition shape as `RecoveryDepsHolder` / `BackupWorkerDepsHolder`, and for the
 * same reason: one reader needing one interface is better served by a concrete typed holder than by
 * the generic `appDeps<T>()` accessor and its unchecked cast.
 */
interface AppRootDepsHolder {

    fun appRootDeps(): AppRootDeps
}
