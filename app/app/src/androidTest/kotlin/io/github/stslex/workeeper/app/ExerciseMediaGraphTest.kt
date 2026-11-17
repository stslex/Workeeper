// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.TestSingleScreenHost
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.exercise.di.ExerciseGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl
import io.github.stslex.workeeper.harness.MetroTestRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
internal class ExerciseMediaGraphTest {
    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun productionGraphRoutesMediaEventsAndResults() = with(composeRule) {
        val events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
        val actions = mutableListOf<Action>()
        val store = mockk<ExerciseStoreImpl>(relaxed = true) {
            every { name } returns "ExerciseMediaGraph"
            every { state } returns MutableStateFlow(State.create(null))
            every { event } returns events
            every { consume(any()) } answers { actions += firstArg<Action>() }
        }
        val graph = mockk<ExerciseGraph> { every { exerciseStore } returns store }
        val registry = RecordingRegistry()
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val owner = MediaOwner()
        val context = RecordingContext(ApplicationProvider.getApplicationContext())
        val visible = mutableStateOf(true)
        setContent {
            if (visible.value) {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides owner,
                    LocalLifecycleOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides owner,
                    LocalActivityResultRegistryOwner provides registryOwner,
                    LocalContext provides context,
                ) {
                    AppTheme {
                        TestSingleScreenHost(start = Screen.Exercise(null)) {
                            exerciseGraph(ExerciseGraph.Factory { graph })
                        }
                    }
                }
            }
        }
        try {
            waitForIdle()
            assertEquals("The real graph must collect Store events", 1, events.subscriptionCount.value)
            fun launchAndAnswer(event: Event, answer: Any?): Launch {
                val before = registry.launches.size
                runOnIdle { assertTrue(events.tryEmit(event)) }
                waitForIdle()
                assertEquals("Graph must launch $event", before + 1, registry.launches.size)
                val launch = registry.launches.last()
                runOnIdle { assertTrue(registry.dispatchResult(launch.requestCode, answer)) }
                waitForIdle()
                return launch
            }
            val target = ImageRef("content://exercise/capture")
            val camera = launchAndAnswer(Event.NavigateLaunchCamera(target), true)
            assertTrue(camera.contract is ActivityResultContracts.TakePicture)
            assertEquals(Uri.parse(target.value), camera.input)
            assertEquals(Action.Common.ImagePicked(target), actions.last())
            launchAndAnswer(Event.NavigateLaunchCamera(target), false)
            assertEquals(Action.Common.ImagePickCancelled, actions.last())

            val picked = Uri.parse("content://exercise/picked")
            val gallery = launchAndAnswer(Event.NavigateLaunchGallery, picked)
            assertTrue(gallery.contract is ActivityResultContracts.PickVisualMedia)
            assertEquals(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
                (gallery.input as PickVisualMediaRequest).mediaType,
            )
            assertEquals(Action.Common.ImagePicked(ImageRef(picked.toString())), actions.last())
            launchAndAnswer(Event.NavigateLaunchGallery, null)
            assertEquals(Action.Common.ImagePickCancelled, actions.last())

            val permission = launchAndAnswer(Event.NavigateRequestCameraPermission, true)
            assertTrue(permission.contract is ActivityResultContracts.RequestPermission)
            assertEquals(Manifest.permission.CAMERA, permission.input)
            assertEquals(Action.Click.RequestCameraCapture, actions.last())
            launchAndAnswer(Event.NavigateRequestCameraPermission, false)
            assertEquals(Action.Click.OnCameraPermissionDenied, actions.last())

            runOnIdle { assertTrue(events.tryEmit(Event.NavigateOpenAppSettings)) }
            waitForIdle()
            assertEquals("Graph must open application settings", 1, context.started.size)
            val intent = context.started.single()
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
            assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals(6, registry.launches.size)
            assertEquals(6, actions.size)
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.viewModelStore.clear() }
        }
    }

    private class MediaOwner : ViewModelStoreOwner, OnBackPressedDispatcherOwner {
        override val viewModelStore = ViewModelStore()
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
    }

    private data class Launch(val requestCode: Int, val contract: ActivityResultContract<*, *>, val input: Any?)

    private class RecordingRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Launch>()
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches += Launch(requestCode, contract, input)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            started += intent
        }
    }
}
