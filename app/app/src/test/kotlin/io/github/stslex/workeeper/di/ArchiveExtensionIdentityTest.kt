// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.feature.archive.di.ArchiveGraph
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
 * The contributed archive extension aggregates into the real parent graph and inherits its
 * app-scoped bindings by identity. Lives in `:app`: an extension is not standalone-creatable.
 */
internal class ArchiveExtensionIdentityTest {

    // The parent graph binds Dispatchers.Main.immediate, so a JVM test must install a Main first.
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

    private fun AppGraph.archive(): ArchiveGraph = asContribution<ArchiveGraph.Factory>()
        .createArchiveGraph()

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().archive().archiveStore

        assertNotNull(store, "The contributed extension must resolve ArchiveStoreImpl from the parent graph")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.archive().archiveStore

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

    /**
     * The concrete and interface handler-store keys are one object only because of
     * `@SingleIn(ArchiveScope::class)`, which no compiler check guards. See architecture.md.
     */
    @Test
    fun `the two handler-store keys resolve to ONE instance`() {
        val extension = buildAppGraph().archive()

        assertSame(
            extension.handlerStoreByConcreteKey,
            extension.handlerStoreByInterfaceKey,
            "The Store's storeEmitter (ArchiveHandlerStoreImpl) and the handlers' store " +
                "(ArchiveHandlerStore) must be one @SingleIn(ArchiveScope::class) instance",
        )
    }

    /**
     * The same invariant end-to-end: the emitter the Store called `setStore(this)` on is the
     * one the handlers delegate through.
     */
    @Test
    fun `the emitter the Store bound itself into is the one the handlers delegate through`() {
        val extension = buildAppGraph().archive()

        val store = extension.archiveStore

        assertSame(
            store.state,
            extension.handlerStoreByInterfaceKey.state,
            "The handlers' ArchiveHandlerStore must forward to the Store that called setStore(this)",
        )
    }
}
