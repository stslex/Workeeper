// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.stslex.workeeper

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.navigation.NavigatorExt.NavigationEventBusSetup
import io.github.stslex.workeeper.navigation.newTestBus
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppRootIosTest {
    @Test
    fun transitioningAndDeniedSkipGraphResolution() = runComposeUiTest {
        val owner = RootOwner()
        val generations = RootGenerations(owner)
        generations.appUiPhases.value = AppUiPhase.Transitioning
        setContent { App(generations, { error("Refused root read the graph") }, {}) }
        onNodeWithTag("AppTransitioning").assertIsDisplayed()
        runOnIdle {
            generations.allow = false
            generations.appUiPhases.value = AppUiPhase.Generation(1, owner)
        }
        waitForIdle()
        onNodeWithTag("HomeGraph").assertDoesNotExist()
        assertTrue(generations.admitted.isEmpty())
        assertTrue(generations.released.isEmpty())
        runOnIdle { owner.viewModelStore.clear() }
    }

    @Test
    fun admittedRootResolvesOnceAndReleasesItsToken() = runComposeUiTest {
        englishLocale()
        val fixture = AppRootNativeFixture()
        val owner = RootOwner()
        val generations = RootGenerations(owner)
        val visible = mutableStateOf(true)
        val revision = mutableStateOf(0)
        var resolves = 0
        setContent {
            val current = revision.value
            if (visible.value) {
                App(
                    generationsHolder = generations,
                    resolveDeps = {
                        resolves++
                        fixture
                    },
                    onOpenRecovery = { check(current >= 0) },
                )
            }
        }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            onNodeWithTag("WorkeeperBottomAppBar").assertIsDisplayed()
            listOf("Home", "Trainings", "Exercises").forEach {
                onNodeWithContentDescription(it).assertIsDisplayed()
            }
            runBlocking { fixture.dialogs.publish(AppDialog.UndoRestoreSuccess()) }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("Reverted", useUnmergedTree = true).fetchSemanticsNodes().size == 1
            }
            onNodeWithText("Reverted", useUnmergedTree = true).assertIsDisplayed()
            repeat(3) {
                runOnIdle { revision.value++ }
                waitForIdle()
            }
            assertEquals(1, resolves)
            assertEquals(1, generations.admitted.size)
            assertTrue(generations.released.isEmpty())
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            assertEquals(1, generations.released.size)
            assertSame(generations.admitted.single(), generations.released.single())
            runOnIdle { owner.viewModelStore.clear() }
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun abortedGenerationRestoresStackAndSuccessorStartsFresh() = runComposeUiTest {
        englishLocale()
        val fixture = AppRootNativeFixture()
        val firstOwner = RootOwner()
        val successorOwner = RootOwner()
        val generations = RootGenerations(firstOwner)
        val visible = mutableStateOf(true)
        val registry = SaveableStateRegistry(null) { true }
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (visible.value) App(generations, { fixture }, {})
            }
        }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            runOnIdle { fixture.navigatorEventBus.navTo(Screen.ExerciseImage("file:///root-test.png")) }
            waitForIdle()
            onNodeWithTag("ImageViewerGraph").assertIsDisplayed()
            runOnIdle { generations.appUiPhases.value = AppUiPhase.Transitioning }
            waitForIdle()
            onNodeWithTag("AppTransitioning").assertIsDisplayed()
            assertSame(generations.admitted.first(), generations.released.single())
            runOnIdle { generations.appUiPhases.value = AppUiPhase.Generation(1, firstOwner) }
            waitForIdle()
            onNodeWithTag("ImageViewerGraph").assertIsDisplayed()
            runOnIdle { generations.appUiPhases.value = AppUiPhase.Generation(2, successorOwner) }
            waitForIdle()
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            onNodeWithTag("ImageViewerGraph").assertDoesNotExist()
            runOnIdle {
                val slots = registry.performSave().values.flatten().filterIsInstance<Map<*, *>>()
                    .single { 2 in it }
                assertFalse(1 in slots, "The successor must discard the outgoing saved slot")
            }
            assertEquals(3, generations.admitted.size)
            assertEquals(2, generations.released.size)
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle {
                firstOwner.viewModelStore.clear()
                successorOwner.viewModelStore.clear()
            }
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun savedStackRoundTripsThroughTheProductionRoot() = runComposeUiTest {
        englishLocale()
        val fixture = AppRootNativeFixture()
        val owner = RootOwner()
        val generations = RootGenerations(owner)
        val visible = mutableStateOf(true)
        val registry = mutableStateOf(SaveableStateRegistry(null) { true })
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry.value) {
                if (visible.value) App(generations, { fixture }, {})
            }
        }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            runOnIdle { fixture.navigatorEventBus.navTo(Screen.ExerciseImage("file:///saved-stack.png")) }
            waitForIdle()
            onNodeWithTag("ImageViewerGraph").assertIsDisplayed()
            var saved: Map<String, List<Any?>> = emptyMap()
            runOnIdle {
                saved = registry.value.performSave()
                visible.value = false
            }
            waitForIdle()
            assertTrue(saved.isNotEmpty())
            runOnIdle {
                registry.value = SaveableStateRegistry(saved) { true }
                visible.value = true
            }
            waitForIdle()
            onNodeWithTag("ImageViewerGraph").assertIsDisplayed()
            runOnIdle { fixture.navigatorEventBus.popBack() }
            waitForIdle()
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            assertEquals(2, generations.admitted.size)
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.viewModelStore.clear() }
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun resultIsPublishedBeforeTheDestinationPops() = runComposeUiTest {
        val bus = newTestBus()
        val stack = NavBackStack<NavKey>(Screen.BottomBar.Home, Screen.ExerciseImage("result"))
        val holder = NavigatorHolder(stack)
        var publishedWhileMounted = false
        val results = object : NavResultsSource by bus {
            override fun setResult(key: String, result: Any) {
                assertEquals(2, stack.size, "Result must publish before pop")
                publishedWhileMounted = true
                bus.setResult(key, result)
            }
        }
        setContent { NavigationEventBusSetup(holder, bus, results, {}) }
        waitForIdle()
        runOnIdle { bus.popBackWithResult(Screen.ExerciseImage::class, "selected-image") }
        waitForIdle()
        assertTrue(publishedWhileMounted)
        assertEquals(1, stack.size)
        assertEquals("selected-image", bus.result(NavResultKey.of(Screen.ExerciseImage::class)).value)
    }

    @Test
    fun bridgeUsesLatestRecoveryCallback() = runComposeUiTest {
        val fixture = AppRootNativeFixture()
        val owner = RootOwner()
        val generations = RootGenerations(owner)
        val visible = mutableStateOf(true)
        var previous = 0
        var current = 0
        val callback = mutableStateOf<() -> Unit>({ previous++ })
        setContent { if (visible.value) App(generations, { fixture }, callback.value) }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            runOnIdle { callback.value = { current++ } }
            waitForIdle()
            runOnIdle { fixture.navigatorEventBus.openRecovery() }
            waitForIdle()
            assertEquals(0, previous)
            assertEquals(1, current)
            assertEquals(1, generations.admitted.size)
            assertTrue(generations.released.isEmpty())
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.viewModelStore.clear() }
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun abandonedCompositionReleasesItsExactToken() {
        val owner = RootOwner()
        val generations = RootGenerations(owner)
        assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent { App(generations, { error("Abort before applying composition") }, {}) }
                waitForIdle()
            }
        }
        assertEquals(1, generations.admitted.size)
        assertEquals(1, generations.released.size)
        assertSame(generations.admitted.single(), generations.released.single())
        owner.viewModelStore.clear()
    }

    @Test
    fun restartReachesHostWithoutACommandCollector() {
        val fixture = AppRootNativeFixture()
        try {
            assertEquals(0, fixture.navigatorEventBus.commands.replayCache.size)
            fixture.navigatorEventBus.restartApp()
            assertEquals(1, fixture.restarts)
            assertFalse(fixture.navigatorEventBus.commands.replayCache.isNotEmpty())
        } finally {
            runBlocking { fixture.close() }
        }
    }

    private fun englishLocale() {
        NSUserDefaults.standardUserDefaults.setObject(listOf("en"), forKey = "AppleLanguages")
        NSUserDefaults.standardUserDefaults.setObject("en_US", forKey = "AppleLocale")
    }
}
