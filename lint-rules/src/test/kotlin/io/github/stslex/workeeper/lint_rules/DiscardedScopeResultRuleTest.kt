// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DiscardedScopeResultRuleTest {

    private val rule = DiscardedScopeResultRule()

    @Test
    fun `flags discarded plus inside apply guarded by an if - the detail-menu bug shape`() {
        val findings = rule.lint(
            """
            fun build(cond: Boolean): List<Int> = listOf(1, 2).apply {
                if (cond) {
                    plus(3)
                }
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding, got: $findings")
    }

    @Test
    fun `flags discarded map statement inside apply`() {
        val findings = rule.lint(
            """
            fun build(): List<Int> = listOf(1, 2).apply {
                map { it + 1 }
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected one finding, got: $findings")
    }

    @Test
    fun `does not flag mutating calls inside apply`() {
        val findings = rule.lint(
            """
            fun build(): MutableList<Int> = mutableListOf(1).apply {
                add(2)
                removeAt(0)
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), "Unexpected findings: $findings")
    }

    @Test
    fun `does not flag transform whose result is used by run`() {
        val findings = rule.lint(
            """
            fun build(): List<Int> = listOf(1, 2).run {
                plus(3)
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), "Unexpected findings: $findings")
    }

    @Test
    fun `does not flag assignment of a transform result inside apply`() {
        val findings = rule.lint(
            """
            class Holder(var items: List<Int>) {
                fun refresh() = apply {
                    items = items.map { it + 1 }
                }
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), "Unexpected findings: $findings")
    }
}
