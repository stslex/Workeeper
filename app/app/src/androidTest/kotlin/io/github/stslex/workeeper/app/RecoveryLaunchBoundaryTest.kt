// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.NavigatorReceiver
import io.github.stslex.workeeper.core.ui.test.NoopNavResultsSource
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.feature.recovery.RecoveryScenario
import io.github.stslex.workeeper.harness.MetroTestGraphHolder
import io.github.stslex.workeeper.harness.MetroTestRule
import io.github.stslex.workeeper.harness.NavPaths
import io.github.stslex.workeeper.navigation.NavigatorExt.NavigationEventBusSetup
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
internal class RecoveryLaunchBoundaryTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mountedAppOpensStampedStartupRecovery() {
        val paths = NavPaths(composeRule)
        paths.awaitTag("HomeGraph")
        paths.openSettings()
        paths.awaitTag("SettingsGraph")
        val caller = composeRule.activity
        val generationId = requireNotNull(MetroTestGraphHolder.currentGenerationId)
        val navigator = MetroTestGraphHolder.graph.navigatorEventBus
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(RecoveryActivity::class.java.name, null, false)
        var recovery: RecoveryActivity? = null

        try {
            composeRule.runOnIdle { navigator.openRecovery() }
            composeRule.waitForIdle()
            recovery = monitor.waitForActivityWithTimeout(NavPaths.ARRIVAL_TIMEOUT_MS)
                as? RecoveryActivity
            assertNotNull("the mounted generation must launch recovery", recovery)
            val launched = requireNotNull(recovery)
            instrumentation.waitForIdleSync()

            val expected = RecoveryScenario.intent(caller, RecoveryScenario.StartupMigration)
            val actual = launched.intent
            assertEquals(expected.component, actual.component)
            assertEquals(expected.flags, actual.flags)
            val expectedExtras = requireNotNull(expected.extras)
            val actualExtras = requireNotNull(actual.extras)
            assertEquals(
                "both scenario and Continue must be explicitly stamped",
                expectedExtras.keySet(),
                actualExtras.keySet(),
            )
            expectedExtras.keySet().forEach { key ->
                assertEquals(expectedExtras.get(key), actualExtras.get(key))
            }
            assertEquals(RecoveryScenario.StartupMigration, RecoveryScenario.fromIntent(actual))
            assertFalse(RecoveryScenario.allowsContinue(actual))
            assertEquals("one command must launch one Activity", 1, monitor.hits)
            assertFalse(caller.isFinishing)
            assertFalse(caller.isDestroyed)

            instrumentation.runOnMainSync { launched.finish() }
            recovery = null
            paths.awaitTag("SettingsGraph")
            assertEquals(generationId, MetroTestGraphHolder.currentGenerationId)
            assertEquals(1, MetroTestGraphHolder.outstandingAdmissions(generationId))
            composeRule.runOnIdle { navigator.popBack() }
            paths.awaitTag("HomeGraph")
            assertEquals("return and pop must not launch recovery again", 1, monitor.hits)
            assertFalse(caller.isFinishing)
            assertFalse(caller.isDestroyed)
        } finally {
            recovery?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun recomposedBridgeUsesCurrentRecoveryCallback() {
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        val commandFlow = MutableSharedFlow<NavCommand>(extraBufferCapacity = 1)
        val collections = AtomicInteger()
        val receiver = object : NavigatorReceiver {
            override val commands = commandFlow.onSubscription { collections.incrementAndGet() }
        }
        // OpenRecovery never reads the stack; the holder supplies only the collector's identity.
        val holder = NavigatorHolder(mockk())
        val oldCalls = AtomicInteger()
        val currentCalls = AtomicInteger()
        val callback = mutableStateOf<() -> Unit>({ oldCalls.incrementAndGet() })

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                NavigationEventBusSetup(
                    navigatorHolder = holder,
                    navigator = receiver,
                    results = NoopNavResultsSource,
                    onOpenRecovery = callback.value,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, collections.get())
        assertEquals(1, commandFlow.subscriptionCount.value)

        composeRule.runOnIdle { callback.value = { currentCalls.incrementAndGet() } }
        composeRule.waitForIdle()
        assertEquals("changing the callback must preserve the collector", 1, collections.get())
        composeRule.runOnIdle { assertTrue(commandFlow.tryEmit(NavCommand.OpenRecovery)) }
        composeRule.waitUntil(NavPaths.ARRIVAL_TIMEOUT_MS) { oldCalls.get() + currentCalls.get() == 1 }

        assertEquals(0, oldCalls.get())
        assertEquals(1, currentCalls.get())
        assertEquals(1, collections.get())
        assertEquals(1, commandFlow.subscriptionCount.value)
    }
}
