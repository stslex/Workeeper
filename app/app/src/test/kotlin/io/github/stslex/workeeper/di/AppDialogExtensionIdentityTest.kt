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
 * Replaces the former feature-module `AppDialogGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * app-dialogs is **port 13 — the thirteenth and last feature graph of the arc**, and its narrowest at
 * 6 forced-public. It is the only one with no route arg (`AppFeature<P>`, screen-less), so there is no
 * per-extension arg claim to make; and the only one that both CONTRIBUTES app-scoped bindings and owns
 * a feature-scoped graph.
 *
 * The claim that matters here is specific to this port: [AppDialogRepository] and
 * [AppDialogObserverImpl] used to be handed in through the `AppDialogInternalsHolder` seam — an
 * `Application`-implements-holder trick, because no other module can name those impl-owned types. As a
 * contributed extension the graph inherits both straight from the parent. **Asserting they are the
 * PARENT's instances is what proves the seam is genuinely redundant and not merely unused.** A double
 * here would mean the app-root dialog state silently forking from the one the rest of the app writes.
 *
 * Every `assertSame` has one operand from the EXTENSION — an assertion whose operands both come from
 * the parent tests parent-side stability, not inheritance (adjacent-answer witness 13).
 */
internal class AppDialogExtensionIdentityTest {

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

    /**
     * The claim that retires the `AppDialogInternalsHolder` seam. Both singletons must be the parent's
     * own — if the extension built its own, the app-root dialog state would fork from the one every
     * other feature publishes to, and the seam could not be deleted.
     */
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

    /**
     * Screen-less counterpart of the other ports' per-arg claim. There is no route arg, so the
     * property to pin is the opposite one: the creator takes no parameters, and each call still builds
     * a DISTINCT extension whose Store is distinct — the extension is not itself a singleton.
     */
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
        // ...while the INHERITED app-scoped singleton stays one instance across both.
        assertSame(
            first.appDialogRepository,
            second.appDialogRepository,
            "the inherited app-scoped repository must be shared across extensions",
        )
    }
}
