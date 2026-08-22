// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.feature.home.di.HomeGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Replaces the former feature-module `HomeGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * Proves the contributed extension aggregates into the REAL parent graph and inherits its app-scoped
 * bindings by IDENTITY, not copy:
 *  1. the extension resolves `HomeStoreImpl` (constructed via its INTERNAL ctor + internal handlers,
 *     entirely by :app-generated code), and
 *  2. the store's app-scoped deps are the SAME instances the parent graph holds (`===`).
 *
 * home keeps the plain construction assertion its siblings use — unlike [SettingsExtensionIdentityTest],
 * whose Store cannot be built off-device (see STANDING RULE 4 in the arc HANDOFF). Nothing home resolves
 * touches a platform singleton, which is part of why it was chosen as the build-time disambiguator.
 */
internal class HomeExtensionIdentityTest {

    // The real parent graph provides Dispatchers.Main.immediate (DispatchersBindingContainer); a plain
    // JVM test must install a Main dispatcher before the store constructs.
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph()
            .asContribution<HomeGraph.Factory>()
            .createHomeGraph()
            .homeStore

        assertNotNull(store, "The contributed extension must resolve HomeStoreImpl from the parent graph")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph
            .asContribution<HomeGraph.Factory>()
            .createHomeGraph()
            .homeStore

        // Identity, not just non-null: the extension inherits the parent's app-scoped singletons.
        assertSame(
            appGraph.analyticsHolder,
            store.analyticsHolder,
            "AnalyticsHolder in the extension-built store must be the parent graph's instance",
        )
        assertSame(
            appGraph.loggerHolder,
            store.loggerHolder,
            "LoggerHolder in the extension-built store must be the parent graph's instance",
        )
    }
}
