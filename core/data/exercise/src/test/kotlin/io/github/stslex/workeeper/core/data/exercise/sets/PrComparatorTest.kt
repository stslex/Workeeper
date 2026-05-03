// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.sets

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PrComparatorTest {

    @Test
    fun `weighted heavier weight beats baseline`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = 105.0, reps = 5)

        assertTrue(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted same weight more reps beats baseline`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = 100.0, reps = 6)

        assertTrue(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted same weight equal reps does not beat`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = 100.0, reps = 5)

        assertFalse(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted same weight fewer reps does not beat`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = 100.0, reps = 4)

        assertFalse(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted lighter weight does not beat`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = 95.0, reps = 10)

        assertFalse(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted null candidate weight does not beat non-null baseline`() {
        val baseline = pr(weight = 100.0, reps = 5)
        val candidate = set(weight = null, reps = 10)

        assertFalse(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weighted null baseline beats by any non-zero candidate`() {
        val candidate = set(weight = 60.0, reps = 5)

        assertTrue(PrComparator.beats(candidate, null, ExerciseTypeDataModel.WEIGHTED))
    }

    @Test
    fun `weightless more reps beats`() {
        val baseline = pr(weight = null, reps = 12)
        val candidate = set(weight = null, reps = 15)

        assertTrue(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTLESS))
    }

    @Test
    fun `weightless equal reps does not beat`() {
        val baseline = pr(weight = null, reps = 12)
        val candidate = set(weight = null, reps = 12)

        assertFalse(PrComparator.beats(candidate, baseline, ExerciseTypeDataModel.WEIGHTLESS))
    }

    @Test
    fun `weightless null baseline beats with any positive reps`() {
        val candidate = set(weight = null, reps = 5)

        assertTrue(PrComparator.beats(candidate, null, ExerciseTypeDataModel.WEIGHTLESS))
    }

    @Test
    fun `weightless zero reps does not beat null baseline`() {
        val candidate = set(weight = null, reps = 0)

        assertFalse(PrComparator.beats(candidate, null, ExerciseTypeDataModel.WEIGHTLESS))
    }

    @Test
    fun `bestOf empty list returns null`() {
        val result = PrComparator.bestOf(emptyList(), ExerciseTypeDataModel.WEIGHTED)

        assertNull(result)
    }

    @Test
    fun `bestOf single weighted set returns it`() {
        val only = set(weight = 50.0, reps = 5)

        val result = PrComparator.bestOf(listOf(only), ExerciseTypeDataModel.WEIGHTED)

        assertEquals(only, result)
    }

    @Test
    fun `bestOf weighted picks heaviest then most reps`() {
        val sets = listOf(
            set(weight = 100.0, reps = 5),
            set(weight = 110.0, reps = 3),
            set(weight = 110.0, reps = 5),
            set(weight = 105.0, reps = 8),
        )

        val result = PrComparator.bestOf(sets, ExerciseTypeDataModel.WEIGHTED)

        assertEquals(110.0, result?.weight)
        assertEquals(5, result?.reps)
    }

    @Test
    fun `bestOf weightless picks max reps`() {
        val sets = listOf(
            set(weight = null, reps = 10),
            set(weight = null, reps = 12),
            set(weight = null, reps = 8),
        )

        val result = PrComparator.bestOf(sets, ExerciseTypeDataModel.WEIGHTLESS)

        assertEquals(12, result?.reps)
    }

    @Test
    fun `bestOf weighted skips weight-null sets`() {
        val sets = listOf(
            set(weight = null, reps = 20),
            set(weight = 50.0, reps = 5),
        )

        val result = PrComparator.bestOf(sets, ExerciseTypeDataModel.WEIGHTED)

        assertEquals(50.0, result?.weight)
    }

    private fun set(weight: Double?, reps: Int) = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = SetTypeDataModel.WORK,
    )

    private fun pr(weight: Double?, reps: Int) = PersonalRecordDataModel(
        sessionUuid = "session",
        performedExerciseUuid = "performed",
        setUuid = "set",
        weight = weight,
        reps = reps,
        type = SetsDataType.WORK,
        finishedAt = 1_000L,
    )
}
