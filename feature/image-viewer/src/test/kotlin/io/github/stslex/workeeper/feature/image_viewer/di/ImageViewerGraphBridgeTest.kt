// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 2 checkpoint — in-situ proof on image-viewer's REAL [ImageViewerGraph]. This is the
 * SIMPLEST assisted non-collider (4 bound instances, no dispatcher, no Context) — it proves the
 * assisted-bulk flip is mechanical on a minimal live feature: the graph exposes the assisted
 * [ImageViewerStoreImpl.Factory] (never the Store) and resolves it from the 4 bridged singletons.
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class ImageViewerGraphBridgeTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()

    private fun buildGraph(): ImageViewerGraph = createGraphFactory<ImageViewerGraph.Factory>()
        .create(
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
        )

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        val factory = buildGraph().storeFactory

        assertNotNull(
            factory,
            "The assisted ImageViewerStoreImpl.Factory must be resolvable from the graph",
        )
    }
}
