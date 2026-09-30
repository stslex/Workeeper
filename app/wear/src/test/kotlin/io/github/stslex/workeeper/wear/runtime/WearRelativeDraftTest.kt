package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ui.surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WearRelativeDraftTest {

    @Test
    fun successiveBatchesReadTheCurrentRepsDraft() {
        val env = activeRuntime()
        env.adjust(NumericField.REPS, 2)
        env.adjust(NumericField.REPS, 3)
        assertEquals(13, env.owner.surface.value.reps)
        env.adjust(NumericField.REPS, -6)
        assertEquals(7, env.owner.surface.value.reps)
    }

    @Test
    fun weightBatchesKeepEveryPositiveAndNegativeStep() {
        val env = activeRuntime()
        env.adjust(NumericField.WEIGHT, 2)
        assertEquals(10_500, env.owner.surface.value.weightHundredthsKg)
        env.adjust(NumericField.WEIGHT, -3)
        assertEquals(9_750, env.owner.surface.value.weightHundredthsKg)
    }

    @Test
    fun nullWeightAndZeroRemainDistinctSteps() {
        val env = activeRuntime()
        env.owner.onAction(ControllerAction.SetWeight(0))
        env.adjust(NumericField.WEIGHT, -2)
        assertNull(env.owner.surface.value.weightHundredthsKg)
        env.adjust(NumericField.WEIGHT, 2)
        assertEquals(250, env.owner.surface.value.weightHundredthsKg)
    }

    @Test
    fun crossingTheWeightBoundaryKeepsTheValidPrefixWithoutInventingAClampedStep() {
        val env = activeRuntime()
        env.owner.onAction(ControllerAction.SetWeight(99_500))
        env.adjust(NumericField.WEIGHT, 3)
        assertEquals(99_750, env.owner.surface.value.weightHundredthsKg)
        env.owner.onAction(ControllerAction.SetWeight(100))
        env.adjust(NumericField.WEIGHT, -2)
        assertEquals(100, env.owner.surface.value.weightHundredthsKg)
    }

    @Test
    fun extremeRepsBatchesStopAtTheExistingBoundsWithoutOverflow() {
        val env = activeRuntime()
        env.adjust(NumericField.REPS, Int.MAX_VALUE)
        assertEquals(999, env.owner.surface.value.reps)
        env.adjust(NumericField.REPS, Int.MIN_VALUE)
        assertEquals(0, env.owner.surface.value.reps)
    }

    @Test
    fun expiredAuthorityRejectsTheEntireBatch() {
        val env = activeRuntime()
        env.now = 121_000
        assertEquals(WatchActionResult.Rejected, env.adjust(NumericField.REPS, 2))
        assertEquals(8, env.owner.surface.value.reps)
    }

    private fun activeRuntime() = RuntimeTestEnvironment().also { it.accept() }

    private fun RuntimeTestEnvironment.adjust(field: NumericField, steps: Int) =
        owner.onAction(ControllerAction.AdjustDraft(field, steps))
}
