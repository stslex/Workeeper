// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 4 — in-situ proof on app-dialogs:impl's REAL [AppDialogGraph]. This is the AppFeature
 * (root-mounted, Activity-scoped) PLAIN shape: the graph exposes the Store directly; construction
 * wires the 5 bridged `@Singleton`s. The `@ApplicationContext` Context is NOT in this graph — it
 * lives on the Hilt-constructed `@Singleton` [AppDialogRepository] (bridged bare here) — so there is
 * no Context param and no dispatcher param.
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class AppDialogGraphBridgeTest {

    private val appDialogRepository = mockk<AppDialogRepository>(relaxed = true)
    private val appDialogObserver = mockk<AppDialogObserverImpl>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()

    private fun buildGraph(): AppDialogGraph = createGraphFactory<AppDialogGraph.Factory>()
        .create(
            appDialogRepository = appDialogRepository,
            appDialogObserver = appDialogObserver,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
        )

    @Test
    fun `plain graph constructs the store from the bridged singletons`() {
        assertNotNull(
            buildGraph().appDialogStore,
            "Metro must construct the plain AppDialogStoreImpl from the 5 bridged singletons",
        )
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().appDialogStore
        assertSame(analyticsHolder, store.analyticsHolder, "AnalyticsHolder must be === the provided instance")
        assertSame(loggerHolder, store.loggerHolder, "LoggerHolder must be === the provided instance")
    }
}
