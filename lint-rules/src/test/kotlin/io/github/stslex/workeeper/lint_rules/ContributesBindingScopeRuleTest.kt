// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * App-Scope Collapse Step 3 (Phase B commit 1) coverage for [ContributesBindingScopeRule], the
 * false-green guard on Metro `@ContributesBinding` scope.
 *
 * `@ContributesBinding(scope = KClass<*>)` accepts any class → a wrong scope compiles green but the
 * binding silently fails to aggregate into the app graph. These tests pin the guard on the
 * known-NEGATIVE anchors (wrong scope, Metro's built-in AppScope, missing arg — all compile green
 * today and MUST now flag) and the known-POSITIVE (project AppScope → passes), plus the no-op case
 * (a class with no `@ContributesBinding` is ignored).
 */
internal class ContributesBindingScopeRuleTest {

    private val rule = ContributesBindingScopeRule()

    // ---- known-POSITIVE anchor: correct project AppScope passes ----

    @Test
    fun `ContributesBinding with the project AppScope passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn
            import io.github.stslex.workeeper.core.core.di.AppScope

            @ContributesBinding(AppScope::class)
            @SingleIn(AppScope::class)
            @Inject
            class NumUiUtilsImpl : NumUiUtils
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "A @ContributesBinding scoped to the project AppScope must pass — it aggregates into the app graph.",
        )
    }

    // ---- known-NEGATIVE anchors: each compiles green today, the rule MUST flag ----

    @Test
    fun `ContributesBinding with a wrong (feature) scope is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject
            import io.github.stslex.workeeper.feature.archive.di.ArchiveScope

            @ContributesBinding(ArchiveScope::class)
            @Inject
            class NumUiUtilsImpl : NumUiUtils
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @ContributesBinding scoped to a non-AppScope (ArchiveScope) must be flagged — it will not " +
                "aggregate into the app graph.",
        )
    }

    @Test
    fun `ContributesBinding with Metro's built-in AppScope is flagged`() {
        // The negative anchor that a simple-name-only check would MISS: the simple name is AppScope,
        // but it's dev.zacsweers.metro.AppScope, a different class from the project token the AppGraph
        // is scoped to → the contribution would not aggregate.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils

            import dev.zacsweers.metro.AppScope
            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject

            @ContributesBinding(AppScope::class)
            @Inject
            class NumUiUtilsImpl : NumUiUtils
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @ContributesBinding using Metro's built-in AppScope (wrong class, same simple name) must be flagged.",
        )
    }

    @Test
    fun `ContributesBinding with no scope argument is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject

            @ContributesBinding
            @Inject
            class NumUiUtilsImpl : NumUiUtils
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A @ContributesBinding with no scope argument must be flagged.",
        )
    }

    // ---- no-op: unrelated classes are ignored ----

    @Test
    fun `class without ContributesBinding is ignored`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils

            import dev.zacsweers.metro.Inject

            @Inject
            class NumUiUtilsImpl : NumUiUtils
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "A class with no @ContributesBinding must be ignored by this rule.")
    }
}
