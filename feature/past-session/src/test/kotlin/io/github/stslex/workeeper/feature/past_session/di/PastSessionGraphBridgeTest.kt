// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 2 checkpoint — in-situ proof on past-session's REAL [PastSessionGraph]. ASSISTED
 * non-collider with a SINGLE qualified dispatcher (@IODispatcher). Proves: (a) a lone non-Default
 * qualified dispatcher rides the includeJavax bridge and resolves under its qualifier; (b) the
 * assisted-bulk flip is mechanical (graph exposes the assisted Factory, never the Store).
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class PastSessionGraphBridgeTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private fun buildGraph(): PastSessionGraph = createGraphFactory<PastSessionGraph.Factory>()
        .create(
            sessionRepository = sessionRepository,
            setRepository = setRepository,
            personalRecordRepository = personalRecordRepository,
            resourceWrapper = resourceWrapper,
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            ioDispatcher = ioDispatcher,
        )

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        val factory = buildGraph().storeFactory

        assertNotNull(
            factory,
            "The assisted PastSessionStoreImpl.Factory must be resolvable — proving the @IO-qualified " +
                "graph wired all 9 bridged singletons",
        )
    }
}
