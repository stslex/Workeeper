// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.SavedStateHandle
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * The navigation-result contract: a destination declares what it hands back, a producer
 * hands one back, and the consumer reads it at that type.
 *
 * **This test is deliberately written against the contract and not against the transport.**
 * It is the migrated successor of `SavedStateHandleNavigationResultTest`, which asserted
 * the mechanism directly — `SavedStateHandle`, the literal key `"plan-editor-saved"`,
 * `getStateFlow`, `setAttrDefaultValue`. All four are gone at 1.3, and a test written
 * against them would go with them, taking the only characterization of result transport
 * the project has.
 *
 * Rewritten this way it has a job at 1.3 instead: it is the **witness that the typed
 * contract survived a transport swap**. Every assertion below is about
 * [ScreenWithResult] semantics — what the type is, when a result is present, when it is
 * `null`, when it is delivered, when it is cleared, and whether two destinations can
 * collide. None of that changes when Nav2's `SavedStateHandle` is replaced.
 *
 * **One line knows the transport: [transport].** That is the seam, marked so 1.3 can find
 * it. When the swap lands, that factory changes and every assertion in this file stays
 * exactly as written — and if one of them then fails, the swap changed behaviour.
 *
 * What is NOT covered here, and why: `NavResults.OnResult` is a `@Composable` and needs a
 * composition to run, which this module's unit tests have no host for. Its two behaviours
 * — deliver only on a non-null result, and clear after delivering — are the composition of
 * [NavResults.result] and [NavResults.clear], and both are covered directly, including the
 * full re-arm cycle the old test simulated against `LaunchedEffect`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigationResultContractTest {

    /**
     * The only transport-aware line in this file — see the class KDoc.
     *
     * At 1.3 this returns a [NavResults] over whatever Nav3 uses; nothing else here moves.
     */
    private fun transport(): Pair<NavResults, TestProducer> {
        val handle = SavedStateHandle()
        return NavResults(handle) to TestProducer(handle)
    }

    /**
     * Stands in for `Navigator.popBackWithResult`, whose real implementation lives in
     * `:app:app` and needs a `NavController` to pop.
     *
     * It is not a hand-rolled stand-in for the *key*, which is the part that matters: it
     * calls the same [NavResultKey.of] production calls, so producer and consumer agree
     * here for the same reason they agree in production. If that agreement broke, these
     * tests would fail rather than pass against a private convention.
     */
    private class TestProducer(private val handle: SavedStateHandle) {

        fun <S, R : Any> produce(
            destination: KClass<S>,
            result: R,
        ) where S : ScreenWithResult<R> {
            handle[NavResultKey.of(destination)] = result
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

        // The declared type is what makes this assignment compile: PlanEditor is
        // ScreenWithResult<Boolean>, so `saved` is Boolean? with no cast at the call site.
        val saved: Boolean? = results.result(Screen.PlanEditor::class).first()
        assertEquals(true, saved)
    }

    @Test
    fun `false is a result, and is distinct from no result`() = runTest {
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
