// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.SavedStateHandle
import io.github.stslex.workeeper.core.ui.navigation.SaveHandlerAttr
import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies the navigation-result protocol that PlanEditor and its consumers
 * (`feature/exercise`, `feature/single-training`, `feature/live-workout`) use to
 * signal "plan was just saved, please reload":
 *
 *  1. Producer pops via
 *     `navigator.popBack(planEditorSavedAttr.toPairValue(true))`, which the
 *     App/UI bridge translates into
 *     `previousBackStackEntry.savedStateHandle["plan-editor-saved"] = true` before
 *     the actual `popBackStack()`.
 *  2. Consumer reads it through `stateHandle.getStateFlow(planEditorSavedAttr)`
 *     and observes the new value.
 *  3. Consumer resets via `stateHandle.setAttrDefaultValue(planEditorSavedAttr)`
 *     so re-entering the screen later does not retrigger the reload.
 *
 * The test simulates each step against a real `SavedStateHandle`. The
 * `SavedStateHandle.getStateFlow` family is the actual AndroidX API used by the
 * graph composables (`feature/exercise/.../ui/ExerciseGraph.kt` etc.), so this
 * exercises the genuine round-trip rather than a hand-rolled stand-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SavedStateHandleNavigationResultTest {

    private val attr: SaveHandlerAttr<Boolean> = Screen.PlanEditor.planEditorSavedAttr

    @Test
    fun `default value is observed before any producer write`() = runTest {
        val handle = SavedStateHandle()

        val initial = handle.getStateFlow(attr).first()

        assertEquals(false, initial, "planEditorSavedAttr default is false")
    }

    @Test
    fun `producer write surfaces as a new emission on getStateFlow`() = runTest {
        val handle = SavedStateHandle()
        val flow = handle.getStateFlow(attr)

        // Writing the producer-side pair (mirrors what NavigatorExt.popBack does in
        // production: previousBackStackEntry.savedStateHandle[key] = true).
        val (key, value) = attr.toPairValue(true)
        handle[key] = value

        assertEquals(true, flow.first())
    }

    @Test
    fun `setAttrDefaultValue resets the flow to the default and re-entry stays clean`() = runTest {
        val handle = SavedStateHandle()
        val flow = handle.getStateFlow(attr)

        handle["plan-editor-saved"] = true
        assertEquals(true, flow.first(), "consumer must observe the producer write")

        // Consumer-side reset after acting on the result.
        handle.setAttrDefaultValue(attr)

        assertEquals(false, flow.first(), "after reset the flow re-emits the default")

        // A subsequent simulated screen re-entry must NOT see a stale `true` and must
        // not retrigger the reload action. We verify by reading the current value
        // again — it is still the default.
        assertEquals(false, flow.first())
    }

    @Test
    fun `re-arming the producer write triggers exactly one reload signal per cycle`() = runTest {
        val handle = SavedStateHandle()

        // The reload is gated by `LaunchedEffect(attrValue) { if (attrValue == true) { ... } }`
        // in the consumer graph. We simulate that gate with a counter that advances
        // only when the value transitions to true. The reset-after-consumption
        // pattern guarantees each producer write is observed once.

        val reloadCount = MutableStateFlow(0)
        val flow = handle.getStateFlow(attr)

        suspend fun consumerTick() {
            val current = flow.first()
            if (current == true) {
                reloadCount.value = reloadCount.value + 1
                handle.setAttrDefaultValue(attr)
            }
        }

        // Cycle 1: producer signals, consumer reads + resets.
        handle["plan-editor-saved"] = true
        consumerTick()
        assertEquals(1, reloadCount.value)

        // Re-entry without a fresh producer write — consumer sees default, no reload.
        consumerTick()
        assertEquals(1, reloadCount.value)

        // Cycle 2: producer signals again (e.g. user opens PlanEditor and saves a
        // second time), consumer reads + resets again.
        handle["plan-editor-saved"] = true
        consumerTick()
        assertEquals(2, reloadCount.value)
    }

    @Test
    fun `multiple state-flow readers observe the same sequence of values`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val handle = SavedStateHandle()
        val flow = handle.getStateFlow(attr)

        val first = async(dispatcher) { flow.take(2).toList() }
        val second = async(dispatcher) { flow.take(2).toList() }
        testScheduler.advanceUntilIdle()

        handle["plan-editor-saved"] = true
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(false, true), first.await())
        assertEquals(listOf(false, true), second.await())
    }
}
