// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.performance.LocalScreenRenderRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.ScreenRenderRecorder
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The production Store path executed by Kotlin/Native: `rememberMetroStoreProcessor` →
 * `rememberStoreProcessor` → `viewModel` retention → `init` / `dispose`, driven by the CMP test
 * runner's headless Skiko `ComposeScene` under explicit common `LifecycleOwner` and
 * `ViewModelStoreOwner` test owners.
 *
 * It does NOT claim `ComposeUIViewController`, `UIWindow` or Metal rendering — those belong to a
 * future `iosApp` stage. See kmp-phase-7-3-mvi.md §10.2.
 */
class StoreProcessorSceneIosTest {

    private var wasLogging: Boolean = true

    @BeforeTest
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
    }

    @AfterTest
    fun tearDown() {
        Log.isLogging = wasLogging
    }

    @Test
    fun productionProcessorRetainsOneStoreAndDrivesTheRenderSeam() = runComposeUiTest {
        val lifetime = AppScopeLifetime()
        val lifecycleOwner = SceneLifecycleOwner()
        val viewModelStoreOwner = SceneViewModelStoreOwner()
        val recorder = RecordingScreenRenderRecorder()
        val created = mutableListOf<SceneStore>()
        val recomposeTrigger = mutableStateOf(0)
        val mounted = mutableStateOf(true)

        setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides lifecycleOwner,
                LocalViewModelStoreOwner provides viewModelStoreOwner,
                LocalScreenRenderRecorder provides recorder,
            ) {
                if (mounted.value) {
                    SceneHost(
                        trigger = recomposeTrigger,
                        lifetime = lifetime,
                        onCreate = { store -> created += store },
                    )
                }
            }
        }
        waitForIdle()

        // (1) exactly one real BaseStore, built by the factory with the explicit lifetime.
        assertEquals(1, created.size, "the factory must construct exactly one Store")
        val store = created.single()
        assertEquals(1, recorder.starts, "the processor must start the render trace once")
        assertEquals(0, recorder.stops, "and must not stop it while the Store is composed")

        // (2) recomposition returns the retained instance through the production viewModel path;
        // a rescope would re-enter the factory.
        repeat(RECOMPOSITIONS) { recomposeTrigger.value += 1 }
        waitForIdle()
        assertEquals(
            1,
            created.size,
            "recomposition must reuse the ViewModelStore entry, not construct a second Store",
        )
        assertSame(store, created.single(), "and it must be the very same instance")
        assertEquals(1, recorder.starts, "a retained Store must not restart the render trace")

        // (5) the generation joins the Store's cleanup: a job with a slow finally must complete
        // before cancelAndJoin returns.
        val started = CompletableDeferred<Unit>()
        val finallyRan = CompletableDeferred<Unit>()
        store.launchDefault(
            onError = {},
            onSuccess = {},
            action = {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(TEARDOWN_WORK_MILLIS)
                        finallyRan.complete(Unit)
                    }
                }
            },
        )
        runBlocking {
            started.await()
            lifetime.cancelAndJoin()
        }
        assertTrue(
            finallyRan.isCompleted,
            "cancelAndJoin returned while a Store job's finally had not run — the Store is not " +
                "parented to the lifetime it was constructed with",
        )

        // (4) clearing the owner's ViewModelStore reaches onCleared and disposes exactly once.
        viewModelStoreOwner.viewModelStore.clear()
        assertEquals(
            1,
            store.disposeActionCount,
            "clearing the ViewModelStore must reach onCleared and dispose the Store",
        )

        // (3) leaving the composition stops the render trace exactly once, and the second
        // disposal is harmless.
        mounted.value = false
        waitForIdle()
        assertEquals(1, recorder.stops, "the processor must stop the render trace once")
        assertEquals(
            1,
            store.disposeActionCount,
            "the composition's own onDispose after a clear must repeat no dispose work",
        )
    }

    private companion object {

        const val RECOMPOSITIONS = 3
        const val TEARDOWN_WORK_MILLIS = 50L
    }
}

@Composable
private fun SceneHost(
    trigger: State<Int>,
    lifetime: AppScopeLifetime,
    onCreate: (SceneStore) -> Unit,
) {
    // Read the trigger here so bumping it recomposes the composable that owns the processor.
    @Suppress("UNUSED_EXPRESSION")
    trigger.value
    rememberMetroStoreProcessor<SceneStore> {
        SceneStore(lifetime).also(onCreate)
    }
}

private class RecordingScreenRenderRecorder : ScreenRenderRecorder {

    var starts: Int = 0
        private set
    var stops: Int = 0
        private set

    override fun start(screenName: String) {
        starts++
    }

    override fun stop(screenName: String) {
        stops++
    }
}

private class SceneLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle get() = registry
}

private class SceneViewModelStoreOwner : ViewModelStoreOwner {

    override val viewModelStore: ViewModelStore = ViewModelStore()
}

private data object SceneState : Store.State

private data object SceneAction : Store.Action

private data object SceneEvent : Store.Event

private class SceneStore(
    appScopeLifetime: AppScopeLifetime,
    private val handled: MutableList<SceneAction> = mutableListOf(),
) : BaseStore<SceneState, SceneAction, SceneEvent>(
    name = "SceneStore",
    initialState = SceneState,
    storeEmitter = BaseHandlerStore(),
    handlerCreator = HandlerCreator<SceneAction> {
        Handler<SceneAction> { action -> handled += action }
    },
    storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Default,
        mainImmediateDispatcher = Dispatchers.Main.immediate,
    ),
    disposeActions = listOf(SceneAction),
    analyticsHolder = AnalyticsHolder(),
    loggerHolder = LoggerHolder(),
    appScopeLifetime = appScopeLifetime,
) {

    /** Disposal is observable only through its dispose action; `dispose()` itself is final. */
    val disposeActionCount: Int get() = handled.size
}
