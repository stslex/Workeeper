// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the app-dialogs `@GraphExtension`: it inherits the parent [AppGraph]'s
 * app-scoped dialog singletons instead of building doubles.
 * See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class AppDialogExtensionIdentityTest {

    // GUARD: Store construction reads the parent graph's Main.immediate binding.
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

    private fun AppGraph.appDialogs(): AppDialogGraph =
        asContribution<AppDialogGraph.Factory>().createAppDialogGraph()

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().appDialogs().appDialogStore

        assertNotNull(store, "The contributed extension must resolve AppDialogStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.appDialogs().appDialogStore

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

    @Test
    fun `the app-scoped dialog singletons are inherited, retiring the internals holder seam`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.appDialogs()

        assertSame(
            appGraph.appDialogRepository,
            extension.appDialogRepository,
            "AppDialogRepository in the extension must be the PARENT's instance, not a double",
        )
        assertSame(
            appGraph.appDialogObserverImpl,
            extension.appDialogObserverImpl,
            "AppDialogObserverImpl in the extension must be the PARENT's instance, not a double",
        )
    }

    @Test
    fun `the no-arg creator builds a distinct extension and store per call`() {
        val appGraph = buildAppGraph()

        val first = appGraph.appDialogs()
        val second = appGraph.appDialogs()

        assertNotSame(first, second, "each createAppDialogGraph() must build a distinct extension")
        assertNotSame(
            first.appDialogStore,
            second.appDialogStore,
            "each extension must build its own Store — retention is the ViewModelStore's job, not the graph's",
        )
        assertSame(
            first.appDialogRepository,
            second.appDialogRepository,
            "the inherited app-scoped repository must be shared across extensions",
        )
    }
}
