// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.TelemetryDedupeKey
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 7.16 D3: a real [BaseStore] hands its `SCREEN_<name>` logger to the launch scope, so a
 * failing launch reaches Crashlytics once, under the Store's tag.
 *
 * Every telemetry sink is stubbed; only the recorded exceptions are captured.
 */
internal class StoreLaunchFailureTelemetryTest {

    private val records = mutableListOf<String>()
    private lateinit var dispatcher: TestDispatcher
    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        mockkObject(FirebaseAnalyticsHolder, FirebaseCrashlyticsHolder)
        every { FirebaseAnalyticsHolder.log(any()) } just Runs
        every { FirebaseCrashlyticsHolder.log(any<String>()) } just Runs
        every { FirebaseCrashlyticsHolder.log(any<String>(), any<TelemetryDedupeKey>()) } just Runs
        every { FirebaseCrashlyticsHolder.recordException(any(), any()) } answers {
            val throwable = firstArg<Throwable>()
            records += "${secondArg<String>()}|${throwable::class.simpleName}|${throwable.message}"
        }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<String>()) } just Runs
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Int>()) } just Runs
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Long>()) } just Runs
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Boolean>()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(FirebaseAnalyticsHolder, FirebaseCrashlyticsHolder)
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun aFailingStoreLaunchIsRecordedOnceUnderTheStoreTag() = runTest {
        val lifetime = AppScopeLifetime()
        val store = LaunchProbe(StoreDispatchers(dispatcher, dispatcher), lifetime)
        store.init(LaunchProbeLifecycleOwner())

        store.launch<Unit>(action = { throw IllegalStateException("probe") })
        advanceUntilIdle()

        assertEquals(listOf("SCREEN_LaunchProbe|IllegalStateException|probe"), records)

        store.dispose()
        lifetime.cancelAndJoin()
    }
}

private class LaunchProbeLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

private data object LaunchProbeState : Store.State

private data object LaunchProbeAction : Store.Action

private data object LaunchProbeEvent : Store.Event

/** The smallest real [BaseStore]: its name gives the `SCREEN_LaunchProbe` logger tag. */
private class LaunchProbe(
    storeDispatchers: StoreDispatchers,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<LaunchProbeState, LaunchProbeAction, LaunchProbeEvent>(
    name = "LaunchProbe",
    initialState = LaunchProbeState,
    storeEmitter = BaseHandlerStore(),
    handlerCreator = HandlerCreator<LaunchProbeAction> { Handler<LaunchProbeAction> { } },
    storeDispatchers = storeDispatchers,
    analyticsHolder = AnalyticsHolder(),
    loggerHolder = LoggerHolder(),
    appScopeLifetime = appScopeLifetime,
)
