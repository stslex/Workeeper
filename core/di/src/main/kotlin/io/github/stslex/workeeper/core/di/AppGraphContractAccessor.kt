// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.di

import android.content.Context

/**
 * The SINGLE entry point by which a library consumer obtains the app-scope [AppGraphContract]
 * from any `Context`.
 *
 * Reads the held graph through the [AppGraphContractHolder] INTERFACE — never a concrete-`Application`
 * cast. This mirrors the sanctioned `AppGraphOwner` idiom (`AppGraphSourceModule`'s
 * `application is AppGraphOwner -> application.appGraph`): a `context as BaseApplication` cast
 * `ClassCastException`s under a swapped test Application, which is the entire reason the interface seam
 * exists. Doing the cast in exactly ONE place means no consumer casts directly.
 *
 * Test note: a test `Application` that does NOT implement [AppGraphContractHolder] fails this cast — the
 * same property `AppGraphOwner` has today (a test Application that does not implement the interface takes
 * the `else`/fallback branch).
 */
fun Context.appGraphContract(): AppGraphContract =
    (applicationContext as AppGraphContractHolder).appGraphContract
