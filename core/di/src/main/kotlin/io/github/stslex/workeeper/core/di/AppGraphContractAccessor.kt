// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.di

import android.content.Context

/**
 * The SINGLE Hilt-free entry point by which a library consumer obtains the app-scope [AppGraphContract]
 * from any `Context`. App-Scope Collapse Step 6 (P-CONTRACT).
 *
 * Reads the held graph through the [AppGraphContractHolder] INTERFACE — never a concrete-`Application`
 * cast. This mirrors the sanctioned `AppGraphOwner` idiom (`AppGraphSourceModule`'s
 * `application is AppGraphOwner -> application.appGraph`): a `context as BaseApplication` cast
 * `ClassCastException`s under a swapped test Application, which is the entire reason the interface seam
 * exists. Doing the cast in exactly ONE place means no consumer casts directly.
 *
 * Replaces the per-consumer
 * `EntryPointAccessors.fromApplication(context.applicationContext, *HiltEntryPoint::class.java)` reads
 * at the Step-6 cut. **P-CONTRACT is add-only: authored but not yet called** — every `EntryPointAccessors`
 * path stays live while Hilt is primary.
 *
 * Test note: a test `Application` that does NOT implement [AppGraphContractHolder] fails this cast — the
 * same property `AppGraphOwner` has today (`HiltTestApplication` takes the `else`/fallback branch). No
 * consumer calls this yet, so no test infra depends on it this commit.
 */
fun Context.appGraphContract(): AppGraphContract =
    (applicationContext as AppGraphContractHolder).appGraphContract
