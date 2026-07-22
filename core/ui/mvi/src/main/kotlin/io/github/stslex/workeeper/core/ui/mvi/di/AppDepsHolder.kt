// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import android.content.Context

/**
 * Held-instance seam for the **god-object split** (variant A, mechanism A): the process
 * [android.app.Application] exposes the app-scope graph as an opaque [Any], from which a reader
 * acquires ONE narrow dependency interface via [appDeps].
 *
 * The return type is deliberately [Any], NOT the concrete app graph: `core:ui:mvi` must not know the
 * `:app`-owned graph type (that would invert the module direction / create a cycle). The graph is
 * re-narrowed to the caller's requested interface by the single `as T` cast in [appDeps].
 *
 * This is the ACQUISITION layer (how a reader physically obtains its interface). It is orthogonal to the
 * INJECTION layer (the typed `create(...)` bound-instance handoff into each feature graph) — `appDeps<T>()`
 * FEEDS `create(...)`, it does not replace it. See
 * `documentation/appgraphcontract-split/spec.md` §"Acquisition mechanism".
 *
 * The public holder seam readers acquire the graph through: `BaseApplication` implements it and reads
 * the graph through the INTERFACE (never a concrete-`Application` cast — that `ClassCastException`s
 * under a swapped test `Application`).
 */
interface AppDepsHolder {
    fun appDeps(): Any
}

/**
 * The single entry point by which a feature-side reader obtains its narrow dep interface [T] (e.g.
 * [StoreCoreDeps], [io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps], a per-feature `XDeps`)
 * from any [Context].
 *
 * Reads the held graph through the [AppDepsHolder] INTERFACE, then re-narrows it with the single
 * `as T` cast. That cast is the ONE untyped point of mechanism A, and it is **safe by construction**:
 * the app graph implements every narrow interface, so the cast can only fail if [T] is an interface the
 * graph does not implement — a compile-time-visible mistake at the migration site, never a runtime key
 * miss. The dependency/injection path downstream of this call stays fully typed.
 *
 * Covers the 14 feature-side readers (13 features + `RecoveryActivity`), all of which depend on
 * `core:ui:mvi`. `MetroWorkerFactory` (`core:data:backup:worker`) intentionally does NOT reach this — it
 * must not depend on `core:ui:mvi` (data→ui inversion) and gets its own point acquisition when migrated.
 *
 * Test note: a test `Application` that does not implement [AppDepsHolder] fails this cast — the same
 * property the interface seam has by design.
 */
inline fun <reified T : Any> Context.appDeps(): T =
    (applicationContext as AppDepsHolder).appDeps() as T
