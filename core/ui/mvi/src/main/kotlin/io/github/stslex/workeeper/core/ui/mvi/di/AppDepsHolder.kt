// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import android.content.Context

/**
 * Held-instance seam: the process [android.app.Application] exposes the app-scope graph as an
 * opaque [Any], so `core:ui:mvi` never names the `:app`-owned graph type. Narrowed in [appDeps].
 */
interface AppDepsHolder {
    fun appDeps(): Any
}

/**
 * Obtains [T] — a contributed `XxxGraph.Factory` — from any [Context]. The single `as T` cast is
 * safe by construction: a graph missing [T] is a compile-time-visible mistake at the call site.
 */
inline fun <reified T : Any> Context.appDeps(): T =
    (applicationContext as AppDepsHolder).appDeps() as T
