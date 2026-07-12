// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * App-Scope Collapse Step 3 (Phase PF commit 0) coverage for [ContributesToScopeRule], the false-green
 * guard on Metro `@BindingContainer` provides-factory contributions (the twin of
 * [ContributesBindingScopeRuleTest]).
 *
 * Verified empirically on Metro 1.1.1 (PF.0 gate): a `@BindingContainer @ContributesTo` to a wrong scope
 * — a feature scope, Metro's built-in AppScope, or a missing/absent `@ContributesTo` — compiles GREEN with
 * zero diagnostic (silent-absence false-green). These tests pin the guard on those known-NEGATIVE anchors
 * and the known-POSITIVE (project AppScope → passes), plus the no-op case (a non-container class is ignored).
 */
internal class ContributesToScopeRuleTest {

    private val rule = ContributesToScopeRule()

    // ---- known-POSITIVE anchor: correct project AppScope passes ----

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

    // ---- known-NEGATIVE anchors: each compiles green today, the rule MUST flag ----

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
        // The negative anchor a simple-name-only check would MISS: simple name is AppScope, but it's
        // dev.zacsweers.metro.AppScope, a different class from the project token the AppGraph is scoped to.
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

    // ---- no-op: unrelated declarations are ignored ----

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
