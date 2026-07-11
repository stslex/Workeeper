// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * The SINGLE source of the app-scope [AppGraph] into Hilt (App-Scope Collapse — Phase D2 decouple).
 * Split out of [AppGraphAdoptBackModule] so it is the ONLY place that reaches for the
 * [BaseApplication]-held graph, and the ONLY unit that behaves differently under test. The adopt-back
 * shims in [AppGraphAdoptBackModule] consume the `AppGraph` binding this module provides and are never
 * replaced — so tests exercise the REAL delegation, not a hand-copied double.
 *
 * WHY A FALLBACK, NOT A CAST: prod `BaseApplication` implements [AppGraphOwner] and holds the process
 * graph. But the Hilt instrumented-test harness swaps in `HiltTestApplication`, which does NOT — and it
 * is `internal`-invisible to the `app:dev` / `app:store` flavor test modules, so they cannot
 * `@TestInstallIn`-replace this module nor implement [AppGraphOwner] themselves. A `context as
 * AppGraphOwner` cast therefore `ClassCastException`s in every flavor `@HiltAndroidTest` that resolves a
 * migrated binding at startup (the defect that shipped latent with the leaf). Instead:
 *  - prod (`BaseApplication is AppGraphOwner`) → return the held graph; and
 *  - test (`HiltTestApplication`, not an owner) → build the REAL [AppGraph] from `applicationContext`.
 * The test branch builds the SAME real graph the prod app holds (`create(applicationContext)`), so there
 * is zero test-double drift and no per-flavor test wiring. Works for `app:app` + `app:dev` + `app:store`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppGraphSourceModule {

    @Provides
    @Singleton
    fun provideAppGraph(
        application: Application,
        // The db-cascade/collider bridge inputs the graph's create() needs. Hilt-owned at this layer, so
        // they are injected here and passed to the test-branch build (prod BaseApplication passes the same
        // set from its own EntryPoint). This provider is the single place that knows create()'s signature.
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        @MainImmediateDispatcher mainImmediateDispatcher: CoroutineDispatcher,
    ): AppGraph = when (application) {
        // Prod: BaseApplication (and its dev/store subclasses) hold the process-lifetime graph.
        is AppGraphOwner -> application.appGraph
        // Test: HiltTestApplication is not an AppGraphOwner. Build the real graph from the app context —
        // the same construction BaseApplication.appGraph performs — so the real adopt-back shims resolve.
        else -> createGraphFactory<AppGraph.Factory>().create(
            applicationContext = application.applicationContext,
            defaultDispatcher = defaultDispatcher,
            mainImmediateDispatcher = mainImmediateDispatcher,
        )
    }
}
