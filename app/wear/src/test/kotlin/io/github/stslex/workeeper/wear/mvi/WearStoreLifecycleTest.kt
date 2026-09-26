package io.github.stslex.workeeper.wear.mvi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.di.WearApplicationGraph
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearStoreImpl
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.ui.WearControllerRoute
import io.github.stslex.workeeper.wear.ui.WearGateHost
import io.github.stslex.workeeper.wear.ui.WearScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "en-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearStoreLifecycleTest {
    @Test
    fun metroRetainsTheEditorAndProcessDraftButClosesExpiredEditingOnWake() {
        val env = RuntimeTestEnvironment().also { it.accept() }
        val lifetime = AppScopeLifetime()
        val applicationGraph = createGraphFactory<WearApplicationGraph.Factory>().create(lifetime)
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        try {
            runComposeUiTest {
                var contentGeneration by mutableStateOf(0)
                var platform by mutableStateOf(WearPlatformState())
                var creations = 0
                lateinit var store: WearStoreImpl
                setContent {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        key(contentGeneration) {
                            WearGateHost(WearScreen.XL_ROUND) {
                                WearControllerRoute(
                                    factory = {
                                        creations += 1
                                        applicationGraph.wearGraphFactory.createWearGraph(env.owner).store.also {
                                            store = it
                                        }
                                    },
                                    platform = platform,
                                    onEnableNotifications = {},
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                onNodeWithTag("reps_card").performScrollTo().performClick()
                waitForIdle()
                onNodeWithTag("editor_rotary").assertIsFocused()
                onRoot().performRotaryScrollInput { rotateToScrollVertically(96f) }
                waitForIdle()
                assertEquals(10, env.owner.snapshot.value.workout.draft?.reps)
                onNodeWithTag("editor_value").assertTextEquals("10")
                contentGeneration += 1
                waitForIdle()
                assertEquals(1, creations, "The same ViewModelStore must retain the project Store")
                assertEquals(NumericField.REPS, store.state.value.editor)
                onNodeWithTag("editor_value").assertTextEquals("10")
                onNodeWithTag("editor_rotary").assertIsFocused()
                platform = WearPlatformState(ambient = WearAmbientState(isAmbient = true))
                waitForIdle()
                env.now = 121_000L
                env.owner.onWake()
                platform = WearPlatformState()
                waitForIdle()
                assertFalse(store.state.value.presentation.model.controlsEnabled)
                assertNull(store.state.value.editor)
                assertEquals(10, env.owner.snapshot.value.workout.draft?.reps)
            }
        } finally {
            owner.viewModelStore.clear()
            lifetime.cancel()
        }
    }
}
