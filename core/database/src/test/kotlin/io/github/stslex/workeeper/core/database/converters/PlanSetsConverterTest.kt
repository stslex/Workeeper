// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.converters

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PlanSetsConverterTest {

    @Test
    fun `null in null out`() {
        assertNull(PlanSetsConverter.toJson(null))
        assertNull(PlanSetsConverter.fromJson(null))
    }

    @Test
    fun `empty list round trip`() {
        val json = PlanSetsConverter.toJson(emptyList())

        val decoded = PlanSetsConverter.fromJson(json)

        assertEquals(emptyList<PlanSetDataModel>(), decoded)
    }

    @Test
    fun `weighted set round trip preserves all fields`() {
        val sets = listOf(
            PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            PlanSetDataModel(weight = 102.5, reps = 3, type = SetTypeDataModel.FAILURE),
        )

        val decoded = PlanSetsConverter.fromJson(PlanSetsConverter.toJson(sets))

        assertEquals(sets, decoded)
    }

    @Test
    fun `weightless set round trip preserves null weight`() {
        val sets = listOf(
            PlanSetDataModel(weight = null, reps = 12, type = SetTypeDataModel.WARMUP),
            PlanSetDataModel(weight = null, reps = 8, type = SetTypeDataModel.DROP),
        )

        val decoded = PlanSetsConverter.fromJson(PlanSetsConverter.toJson(sets))

        assertEquals(sets, decoded)
    }

    @Test
    fun `mixed weighted and weightless round trip`() {
        val sets = listOf(
            PlanSetDataModel(weight = 60.0, reps = 10, type = SetTypeDataModel.WARMUP),
            PlanSetDataModel(weight = null, reps = 5, type = SetTypeDataModel.WORK),
            PlanSetDataModel(weight = 80.0, reps = 6, type = SetTypeDataModel.WORK),
        )

        val decoded = PlanSetsConverter.fromJson(PlanSetsConverter.toJson(sets))

        assertEquals(sets, decoded)
    }
}
