// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
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
 * Replaces the former feature-module `ArchiveGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * Proves the contributed extension aggregates into the REAL parent graph and inherits its app-scoped
 * bindings by IDENTITY, not copy:
 *  1. the extension resolves `ArchiveStoreImpl` (constructed via its INTERNAL ctor + internal handlers,
 *     entirely by :app-generated code), and
 *  2. the store's app-scoped deps are the SAME instances the parent graph holds (`===`).
 * Mirrors [AllTrainingsExtensionIdentityTest] and [AppGraphIdentityTest]'s 3-root create() seam.
 *
 * The last two tests are deliberately the OTHER shape. No other `*ExtensionIdentityTest` has an
 * `assertSame` with both operands read from ONE extension — they compare an extension against the
 * parent, or two sibling extensions against each other, and both of those pin INHERITANCE across the
 * graph boundary. Neither can see the invariant that `@SingleIn(ArchiveScope::class)` replaced
 * `@ViewModelScoped` for, because that one is intra-extension. archive carries it for the batch —
 * see `the two handler-store keys resolve to ONE instance`.
 */
internal class ArchiveExtensionIdentityTest {

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

    /**
     * The intra-extension sharing invariant, asserted directly on the two binding keys production uses.
     *
     * `ArchiveStoreImpl` takes `storeEmitter: ArchiveHandlerStoreImpl` — the CONCRETE key. Every archive
     * handler takes `store: ArchiveHandlerStore` — the INTERFACE key, reached through
     * `@Binds ArchiveHandlerStoreImpl.bindHandlerStore`. Both keys resolve the same `@Inject` class, so
     * they are one object ONLY because of `@SingleIn(ArchiveScope::class)` on `ArchiveHandlerStoreImpl`.
     *
     * FAILURE MODE this pins: delete (or mistype the KClass on) that annotation and everything still
     * compiles — an unscoped `@Inject` class is a legal Metro binding, and `nonPublicContributionSeverity`
     * gates `AppScope` contributions only, so an internal feature scope gets no compiler check. Metro then
     * builds emitter #1 for the handlers and #2 for the Store; only #2 receives `setStore(this)` from
     * `BaseStore.init {}`, and the first `Action.Paging.Init` hits `requireNotNull(_store)` on #1 —
     * the screen crashes on open. Both `assertSame` operands are read from ONE extension, which is what
     * makes the assertion sensitive to it; the parent-vs-extension shape used by the other tests here is
     * not.
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
     * The same invariant observed end-to-end rather than by key identity: the emitter the Store bound
     * itself into is the emitter the handlers delegate through.
     *
     * `BaseStore.init {}` calls `storeEmitter.setStore(this)`, and `BaseHandlerStore` forwards `state`
     * to that stored consumer. So after resolving `archiveStore`, reading `state` off the INTERFACE key
     * must return the Store's own `StateFlow`. Without the `@SingleIn`, that read is on a second,
     * never-bound instance and `requireNotNull(_store)` throws — the production crash, reproduced.
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
