// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.sets

import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlanUpdateRuleTest {

    private val plan100x5 = set(weight = 100.0, reps = 5)
    private val plan1025x5 = set(weight = 102.5, reps = 5)
    private val plan90x8 = set(weight = 90.0, reps = 8)

    @Test
    fun `replace when performed equals plan size`() {
        val existing = listOf(plan100x5, plan100x5, plan100x5)
        val performed = listOf(plan100x5, plan100x5, plan1025x5)

        val result = PlanUpdateRule.update(existing, performed)

        assertEquals(performed, result)
    }

    @Test
    fun `grow when performed exceeds plan size`() {
        val existing = listOf(plan100x5, plan100x5, plan100x5)
        val performed = listOf(plan100x5, plan100x5, plan1025x5, plan90x8)

        val result = PlanUpdateRule.update(existing, performed)

        assertEquals(performed, result)
    }

    @Test
    fun `partial keeps tail when performed shorter than plan`() {
        val existing = listOf(plan100x5, plan100x5, plan100x5)
        val performed = listOf(plan100x5, plan100x5)

        val result = PlanUpdateRule.update(existing, performed)

        assertEquals(listOf(plan100x5, plan100x5, plan100x5), result)
    }

    @Test
    fun `partial replaces head with performed values`() {
        val existing = listOf(plan100x5, plan100x5, plan100x5)
        val performed = listOf(plan1025x5)

        val result = PlanUpdateRule.update(existing, performed)

        assertEquals(listOf(plan1025x5, plan100x5, plan100x5), result)
    }

    @Test
    fun `skip leaves plan untouched when performed empty`() {
        val existing = listOf(plan100x5, plan100x5, plan100x5)

        val result = PlanUpdateRule.update(existing, emptyList())

        assertEquals(existing, result)
    }

    @Test
    fun `skip leaves null plan untouched when performed empty`() {
        val result = PlanUpdateRule.update(existingPlan = null, performed = emptyList())

        assertEquals(null, result)
    }

    @Test
    fun `null plan with performed produces performed`() {
        val performed = listOf(plan100x5, plan100x5)

        val result = PlanUpdateRule.update(existingPlan = null, performed = performed)

        assertEquals(performed, result)
    }

    @Test
    fun `empty plan with performed produces performed`() {
        val performed = listOf(plan100x5)

        val result = PlanUpdateRule.update(existingPlan = emptyList(), performed = performed)

        assertEquals(performed, result)
    }

    private fun set(weight: Double?, reps: Int) = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = SetTypeDataModel.WORK,
    )
}
