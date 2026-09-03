// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [ContributesToScopeRule]: a `@BindingContainer` contributed to a wrong scope, or
 * to none at all, compiles green with zero diagnostic. See documentation/lint-rules.md.
 */
internal class ContributesToScopeRuleTest {

    private val rule = ContributesToScopeRule()

    @Test
    fun `BindingContainer ContributesTo the project AppScope passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.core.di

            import dev.zacsweers.metro.BindingContainer
            import dev.zacsweers.metro.ContributesTo
            import dev.zacsweers.metro.Provides
            import io.github.stslex.workeeper.core.core.di.AppScope

            @BindingContainer
            @ContributesTo(AppScope::class)
            object NetworkBindings {
                @Provides
                fun provideThing(): String = "x"
            }
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "A @BindingContainer @ContributesTo the project AppScope must pass — it aggregates into the app graph.",
        )
    }

    @Test
    fun `BindingContainer with no ContributesTo is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.core.di

            import dev.zacsweers.metro.BindingContainer
            import dev.zacsweers.metro.Provides

            @BindingContainer
            object NetworkBindings {
                @Provides
                fun provideThing(): String = "x"
            }
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @BindingContainer with no @ContributesTo must be flagged — it never aggregates (silent orphan).",
        )
    }

    @Test
    fun `BindingContainer ContributesTo a wrong (feature) scope is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.core.di

            import dev.zacsweers.metro.BindingContainer
            import dev.zacsweers.metro.ContributesTo
            import dev.zacsweers.metro.Provides
            import io.github.stslex.workeeper.feature.archive.di.ArchiveScope

            @BindingContainer
            @ContributesTo(ArchiveScope::class)
            object NetworkBindings {
                @Provides
                fun provideThing(): String = "x"
            }
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @BindingContainer @ContributesTo a non-AppScope (ArchiveScope) must be flagged — it will not " +
                "aggregate into the app graph.",
        )
    }

    @Test
    fun `BindingContainer ContributesTo Metro's built-in AppScope is flagged`() {
        // Same simple name, different class: Metro's built-in AppScope is not the project token.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.core.di

            import dev.zacsweers.metro.AppScope
            import dev.zacsweers.metro.BindingContainer
            import dev.zacsweers.metro.ContributesTo
            import dev.zacsweers.metro.Provides

            @BindingContainer
            @ContributesTo(AppScope::class)
            object NetworkBindings {
                @Provides
                fun provideThing(): String = "x"
            }
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @BindingContainer using Metro's built-in AppScope (wrong class, same simple name) must be flagged.",
        )
    }

    @Test
    fun `class without BindingContainer is ignored`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.core.di

            import dev.zacsweers.metro.ContributesTo
            import io.github.stslex.workeeper.feature.archive.di.ArchiveScope

            @ContributesTo(ArchiveScope::class)
            interface SomeGraph
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "A declaration with no @BindingContainer must be ignored — this rule only guards binding containers.",
        )
    }
}
