// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The navigation-result contract: what a destination hands back, and when it reads as present,
 * cleared, or absent. Written against [ScreenWithResult] semantics, not against the transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigationResultContractTest {

    /** The only transport-aware line in this file; point it elsewhere and nothing else moves. */
    private fun transport(): Pair<NavResults, TestProducer> {
        val source = InMemoryNavResultsSource()
        return NavResults(source) to TestProducer(source)
    }

    /** The transport in miniature: the keyed nullable-StateFlow shape `NavigatorEventBus` has. */
    private class InMemoryNavResultsSource : NavResultsSource {

        private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

        override fun result(key: String): StateFlow<Any?> = flowFor(key)

        override fun setResult(key: String, result: Any) {
            flowFor(key).value = result
        }

        override fun clearResult(key: String) {
            flowFor(key).value = null
        }

        private fun flowFor(key: String): MutableStateFlow<Any?> =
            flows.getOrPut(key) { MutableStateFlow(null) }
    }

    /**
     * Stands in for `Navigator.popBackWithResult`, which needs a live back stack to pop. It calls
     * the production [NavResultKey.of], so producer and consumer agree as they do in production.
     */
    private class TestProducer(private val source: NavResultsSource) {

        fun <S, R : Any> produce(
            destination: KClass<S>,
            result: R,
        ) where S : ScreenWithResult<R> {
            source.setResult(NavResultKey.of(destination), result)
        }
    }

    @Test
    fun `a destination that has produced nothing reads as null`() = runTest {
        val (results, _) = transport()

        val value = results.result(Screen.PlanEditor::class).first()

        assertNull(value, "no result must read as null, not as a default standing in for one")
    }

    @Test
    fun `a produced result reads back at the type the destination declares`() = runTest {
        val (results, producer) = transport()

        producer.produce(Screen.PlanEditor::class, true)

        // PlanEditor is ScreenWithResult<Boolean>, so `saved` is Boolean? with no cast.
        val saved: Boolean? = results.result(Screen.PlanEditor::class).first()
        assertEquals(true, saved)
    }

    @Test
    fun `false is a result and is distinct from no result`() = runTest {
        val (results, producer) = transport()

        producer.produce(Screen.PlanEditor::class, false)

        assertEquals(
            false,
            results.result(Screen.PlanEditor::class).first(),
            "a produced false must not read as absence — null is what absence means",
        )
    }

    @Test
    fun `clear returns the destination to no-result so re-entry sees nothing`() = runTest {
        val (results, producer) = transport()
        val flow = results.result(Screen.PlanEditor::class)

        producer.produce(Screen.PlanEditor::class, true)
        assertEquals(true, flow.first(), "consumer must observe the produced result")

        results.clear(Screen.PlanEditor::class)

        assertNull(flow.first(), "after clear the destination reads as no-result")
        assertNull(flow.first(), "and a later re-entry still sees nothing")
    }

    @Test
    fun `each produced result is delivered exactly once per cycle`() = runTest {
        val (results, producer) = transport()
        val flow = results.result(Screen.PlanEditor::class)
        var reloads = 0

        // What OnResult does, spelled out: act only on a non-null result, then clear.
        suspend fun consumerTick() {
            val saved = flow.first() ?: return
            if (saved) reloads++
            results.clear(Screen.PlanEditor::class)
        }

        producer.produce(Screen.PlanEditor::class, true)
        consumerTick()
        assertEquals(1, reloads)

        // Re-entry with no fresh result — nothing to act on.
        consumerTick()
        assertEquals(1, reloads, "a consumed result must not re-fire on re-entry")

        // The user saves a second time.
        producer.produce(Screen.PlanEditor::class, true)
        consumerTick()
        assertEquals(2, reloads, "a re-armed result must be delivered again")
    }

    /**
     * GUARD: producer and consumer must pass the same KClass — a parent and its variant are
     * different result channels, and a mismatch fails silently.
     */
    @Test
    fun `a destination and its variant do not share a result channel`() {
        assertNotEquals(
            NavResultKey.of(Screen.PlanEditor::class),
            NavResultKey.of(Screen.PlanEditor.Existing::class),
            "producer and consumer must pass the same KClass; a variant is a different channel",
        )
    }

    @Test
    fun `every reader of a destination observes the same sequence`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val (results, producer) = transport()
        val flow = results.result<Screen.PlanEditor, Boolean>(Screen.PlanEditor::class)

        val first = async(dispatcher) { flow.take(2).toList() }
        val second = async(dispatcher) { flow.take(2).toList() }
        testScheduler.advanceUntilIdle()

        producer.produce(Screen.PlanEditor::class, true)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(null, true), first.await())
        assertEquals(listOf(null, true), second.await())
    }
}
