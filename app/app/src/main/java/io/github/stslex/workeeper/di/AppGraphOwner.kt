// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

/**
 * Minimal coupling boundary between the Android [android.app.Application] and the Metro [AppGraph]
 * (KMP C.1 app-collapse Phase 1 — leaf E-proof). Exposes ONLY the app graph — nothing else.
 *
 * Why this exists (a mechanic fix, not scaffolding): the adopt-back delegating `@Provides` must NOT
 * cast the Application to the concrete `BaseApplication`. The Hilt instrumented-test harness swaps in
 * `dagger.hilt.android.testing.HiltTestApplication`, which does NOT extend `BaseApplication`; a
 * `context as BaseApplication` cast would `ClassCastException` in every `@HiltAndroidTest` that
 * transitively resolves a migrated binding. Reading the graph through THIS interface lets:
 *  - prod `BaseApplication` implement it (holds the real graph), and
 *  - instrumented tests `@TestInstallIn`-replace the graph provider (a test-built [AppGraph]),
 * with the delegating providers depending only on `AppGraph` (Hilt-injected), never on a concrete
 * Application type. Generalizes to every one of the ~96 bindings the bulk migration moves.
 */
internal interface AppGraphOwner {
    val appGraph: AppGraph
}
