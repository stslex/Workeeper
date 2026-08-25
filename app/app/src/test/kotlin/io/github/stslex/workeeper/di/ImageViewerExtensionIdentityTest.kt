// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the image-viewer `@GraphExtension`, whose route arg enters as a bound
 * instance on the extension factory. See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class ImageViewerExtensionIdentityTest {

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

    private fun screen(path: String) = Screen.ExerciseImage(model = path)

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph()
            .asContribution<ImageViewerGraph.Factory>()
            .createImageViewerGraph(screen("/img/a.png"))
            .imageViewerStore

        assertNotNull(store, "The contributed extension must resolve ImageViewerStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph
            .asContribution<ImageViewerGraph.Factory>()
            .createImageViewerGraph(screen("/img/a.png"))
            .imageViewerStore

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
    fun `each extension carries its own route arg into the store state`() {
        val factory = buildAppGraph().asContribution<ImageViewerGraph.Factory>()

        val a = factory.createImageViewerGraph(screen("/img/a.png")).imageViewerStore
        val b = factory.createImageViewerGraph(screen("/img/b.png")).imageViewerStore

        assertNotSame(a, b, "each createImageViewerGraph(screen) must build a distinct Store")
        assertEquals("/img/a.png", a.state.value.model, "store A must carry its own route arg")
        assertEquals("/img/b.png", b.state.value.model, "store B must carry its own route arg")
    }
}
