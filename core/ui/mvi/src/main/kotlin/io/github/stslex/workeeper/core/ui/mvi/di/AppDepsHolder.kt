// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import android.content.Context

/**
 * Held-instance seam: the process [android.app.Application] exposes the app-scope graph as an opaque
 * [Any], from which a feature-side reader acquires ONE interface the graph implements via [appDeps].
 *
 * The return type is deliberately [Any], NOT the concrete app graph: `core:ui:mvi` must not know the
 * `:app`-owned graph type (that would invert the module direction / create a cycle). The graph is
 * re-narrowed to the caller's requested interface by the single `as T` cast in [appDeps].
 *
 * This is the ACQUISITION layer (how a reader physically obtains its interface). Since the
 * graph-extension port, every reader asks for its own `XxxGraph.Factory` — the contributed extension
 * factory the app graph implements once `:app` is compiled — and the extension then INHERITS its
 * app-scoped bindings from the parent rather than being handed them.
 *
 * `BaseApplication` implements this and reads the graph through the INTERFACE (never a
 * concrete-`Application` cast — that `ClassCastException`s under a swapped test `Application`).
 */
interface AppDepsHolder {
    fun appDeps(): Any
}

/**
 * The single entry point by which a feature-side reader obtains [T] from any [Context]. Every current
 * call site passes its own contributed `XxxGraph.Factory` — 13 today, one per Store-hosting feature.
 *
 * Reads the held graph through the [AppDepsHolder] INTERFACE, then re-narrows it with the single
 * `as T` cast. That cast is the ONE untyped point of the seam, and it is **safe by construction**:
 * every `@ContributesTo(AppScope::class)` extension factory is merged into the app graph, so the cast
 * can only fail if [T] is an interface the graph does not implement — a compile-time-visible mistake at
 * the call site, never a runtime key miss. The injection path downstream stays fully typed.
 *
 * The two non-feature readers deliberately do NOT come through here: `RecoveryActivity` and
 * `MetroWorkerFactory` (`core:data:backup:worker`) each use their own typed holder
 * (`RecoveryDepsHolder` / `BackupWorkerDepsHolder`) so they gain no edge on `core:ui:mvi` — for the
 * worker that edge would be a data→ui inversion.
 *
 * Test note: a test `Application` that does not implement [AppDepsHolder] fails this cast — the same
 * property the interface seam has by design.
 */
inline fun <reified T : Any> Context.appDeps(): T =
    (applicationContext as AppDepsHolder).appDeps() as T
