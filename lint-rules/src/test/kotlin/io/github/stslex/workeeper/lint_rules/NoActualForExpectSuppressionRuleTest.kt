// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [NoActualForExpectSuppressionRule]. GUARD: the rule matches only `@Suppress`
 * arguments in the AST, which is why this file's own triple-quoted fixtures are not flagged.
 */
internal class NoActualForExpectSuppressionRuleTest {

    private val rule = NoActualForExpectSuppressionRule()

    @Test
    fun `flags @Suppress NO_ACTUAL_FOR_EXPECT on a declaration`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            @Suppress("NO_ACTUAL_FOR_EXPECT")
            expect class ExampleConstructor
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Expected the banned suppression to be flagged, got: $findings")
        assertTrue(findings.single().message.contains("NO_ACTUAL_FOR_EXPECT"))
    }

    @Test
    fun `flags file-level @file colon Suppress NO_ACTUAL_FOR_EXPECT`() {
        val findings = rule.lint(
            """
            @file:Suppress("NO_ACTUAL_FOR_EXPECT")

            package io.github.stslex.workeeper.feature.example

            expect class ExampleConstructor
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "File-level suppression must be flagged, got: $findings")
    }

    @Test
    fun `flags NO_ACTUAL_FOR_EXPECT when mixed with other suppressions`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            @Suppress("UNUSED", "NO_ACTUAL_FOR_EXPECT", "RedundantVisibilityModifier")
            expect class ExampleConstructor
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Mixed-argument suppression must be flagged, got: $findings")
    }

    @Test
    fun `flags NO_ACTUAL_FOR_EXPECT in the names array argument form`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            @Suppress(names = ["NO_ACTUAL_FOR_EXPECT"])
            expect class ExampleConstructor
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "Array-form suppression must be flagged, got: $findings")
    }

    @Test
    fun `does not flag an unrelated suppression`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            @Suppress("UNUSED", "unused")
            class Example
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Unrelated suppressions must not be flagged, got: $findings")
    }

    @Test
    fun `does not flag a declaration with no suppression`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            expect class ExampleConstructor
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "No suppression means no finding, got: $findings")
    }

    @Test
    fun `does not flag the diagnostic name as plain string content`() {
        // The string is a value, not a `@Suppress` argument — the rule must not fire.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            val doc = "banning @Suppress(\"NO_ACTUAL_FOR_EXPECT\") prevents false greens"
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Plain string content must not be flagged, got: $findings")
    }

    @Test
    fun `does not flag the diagnostic name inside a non-Suppress annotation`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example

            @Deprecated("NO_ACTUAL_FOR_EXPECT")
            class Example
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "Only @Suppress is targeted, got: $findings")
    }
}
