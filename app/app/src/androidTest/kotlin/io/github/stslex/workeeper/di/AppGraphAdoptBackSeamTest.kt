// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

/**
 * KMP C.1 app-collapse Phase 1 (leaf E-proof) — the CROSS-SIDE instrumented half. This is the gate.
 *
 * Proves the ADOPT-BACK seam preserves singleton identity on-device with the REAL Hilt graph live.
 * `AnalyticsHolder` is Metro-OWNED (its `@Inject`/`@Singleton` were stripped); Hilt resolves it only
 * through `AppGraphAdoptBackModule.provideAnalyticsHolder`, which delegates to the Hilt-provided
 * [AppGraph] (`provideAppGraph`).
 *
 * The Hilt test harness swaps in `HiltTestApplication` (no `BaseApplication`), so [TestAppGraphModule]
 * `@TestInstallIn`-REPLACES `provideAppGraph` with a test-built graph — the legitimate test-infra
 * substitute for the prod `BaseApplication.appGraph`. Both asserted access paths then read THAT graph:
 *  - **Path M (Metro-direct):** `testAppGraph.analyticsHolder` — the owner.
 *  - **Path H (Hilt-via-adopt-back):** resolved from Hilt's `SingletonComponent` through
 *    [TestAnalyticsEntryPoint] → `provideAnalyticsHolder` → `provideAppGraph` (the replaced one) →
 *    `appGraph.analyticsHolder`. This is the IDENTICAL Hilt-side resolution the 13 production
 *    `*HiltEntryPoint.analyticsHolder()` accessors perform. Those are `internal` to their feature
 *    modules, so this test declares an equivalent one for the same binding.
 *
 * Negative-control note: the earlier `context as BaseApplication` form threw `ClassCastException`
 * here (HiltTestApplication ≠ BaseApplication) — proving this test genuinely exercises the app swap.
 * If M !== H, the adopt-back constructed a parallel instance (single-owner violation). M === H is E.
 */
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppGraphAdoptBackSeamTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun hiltResolved(): AnalyticsHolder =
        EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                TestAnalyticsEntryPoint::class.java,
            )
            .analyticsHolder()

    private fun metroDirect(): AnalyticsHolder = TestAppGraphModule.testAppGraph.analyticsHolder

    @Test
    fun metroDirectAndHiltAdoptBackResolveTheSameInstance() {
        val metro = metroDirect()
        val hilt = hiltResolved()

        assertNotNull(metro)
        assertNotNull(hilt)
        // THE GATE: the Hilt-side resolution (through the delegating @Provides) returns the SAME
        // object the Metro graph owns. Identity survives the app-tier adopt-back seam.
        assertSame(
            "Hilt adopt-back @Provides must return the Metro-owned AnalyticsHolder (===), not a copy",
            metro,
            hilt,
        )
    }

    @Test
    fun hiltSideResolutionIsStableAcrossReads() {
        // The delegating @Provides is @Singleton; every Hilt read (all 13 bridges) is the same object.
        assertSame(
            "Repeated Hilt resolutions must be identical — a single owner behind the seam",
            hiltResolved(),
            hiltResolved(),
        )
    }

    @Test
    fun metroSideResolutionIsStableAcrossReads() {
        // @SingleIn(AppScope): the Metro owner retains one instance for the process.
        assertSame(
            "Repeated Metro-direct reads must be identical — @SingleIn(AppScope) retention",
            metroDirect(),
            metroDirect(),
        )
    }

    /**
     * Equivalent to a production `*HiltEntryPoint.analyticsHolder()`: reads `AnalyticsHolder` from
     * Hilt's `SingletonComponent`, now served exclusively by the adopt-back delegating `@Provides`.
     * Declared here because the real feature EntryPoints are module-`internal`. (An `@EntryPoint`
     * MAY be nested in a `@HiltAndroidTest` class; only `@TestInstallIn` modules may not.)
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestAnalyticsEntryPoint {
        fun analyticsHolder(): AnalyticsHolder
    }
}

/**
 * `@TestInstallIn` REPLACES ONLY [AppGraphSourceModule] (Phase D2 decouple) — the single unit that
 * reaches the `BaseApplication`-held graph, absent under `HiltTestApplication`. It does NOT replace
 * [AppGraphAdoptBackModule]: the REAL adopt-back shims (`provideAnalyticsHolder`, etc.) stay live and
 * consume the test-built `AppGraph` this module provides, so the `===` proof exercises the PRODUCTION
 * delegation, not a hand-copied double (the honesty gap this phase fixes). TOP-LEVEL, not nested in the
 * `@HiltAndroidTest` class — Hilt forbids nesting `@TestInstallIn` modules in test classes.
 *
 * NOTE the prod [AppGraphSourceModule] now ALSO builds the real graph from `applicationContext` when the
 * Application is not an `AppGraphOwner` — so the flavor (`app:dev`/`app:store`) tests need no replacement
 * at all. This module remains for `app:app`'s own seam test to expose `testAppGraph` for the Metro-direct
 * assertion path.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppGraphSourceModule::class],
)
internal object TestAppGraphModule {

    // A single process-wide test graph, mirroring the prod `by lazy` app-owned singleton.
    val testAppGraph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>()
            .create(ApplicationProvider.getApplicationContext<Context>())
    }

    @Provides
    @Singleton
    fun provideAppGraph(): AppGraph = testAppGraph
}
