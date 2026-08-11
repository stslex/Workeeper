// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [NoActualForExpectSuppressionRule].
 *
 * The positive-control tests are load-bearing: they PROVE the detector actually fires on a
 * known-bad input, so a green detekt run over the real repo means "clean", not "the rule
 * never ran". (Probe-2 lesson: a green result is worthless until the detector is shown to
 * fire on a negative control.)
 *
 * The `does not flag ... as plain string content` test pins the AST-based nature of the
 * rule — it is why THIS very test file (which embeds `@Suppress("NO_ACTUAL_FOR_EXPECT")`
 * inside triple-quoted fixtures) is not itself flagged when detekt scans the repo.
 */
internal class NoActualForExpectSuppressionRuleTest {

    private val rule = NoActualForExpectSuppressionRule()

    // ---------------------------- positive controls (must fire) ----------------------------

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

    // ---------------------------- negative controls (must NOT fire) ----------------------------

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
        // The string appears as a value, NOT inside a @Suppress annotation — must not fire.
        // This is exactly why this test file's own fixtures are safe when detekt scans the repo.
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
