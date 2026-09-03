// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [ContributesBindingScopeRule]: wrong scope, Metro's built-in AppScope and a missing
 * argument all flag; the project AppScope passes; every `@Repeatable` entry is validated.
 */
internal class ContributesBindingScopeRuleTest {

    private val rule = ContributesBindingScopeRule()

    // Known-positive anchor: the project AppScope passes.

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

    // Known-negative anchors: each compiles green, so the rule must flag it.

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
        // The anchor a simple-name-only check would miss: same simple name, different class.
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

    // @Repeatable: every entry is validated, not just the first.

    /** The shipped shape: one impl, two entries, one per supertype, both on project AppScope. */
    @Test
    fun `two ContributesBinding entries both on the project AppScope pass`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.binding
            import io.github.stslex.workeeper.core.core.di.AppScope

            @ContributesBinding(AppScope::class, binding = binding<ActivityHolder>())
            @ContributesBinding(AppScope::class, binding = binding<ActivityHolderProducer>())
            @Inject
            class ActivityHolderImpl : ActivityHolder, ActivityHolderProducer
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "both entries name the project AppScope, so both aggregate")
    }

    /** The negative a `firstOrNull` check misses: correct first entry, feature-scoped second. */
    @Test
    fun `a mis-scoped SECOND ContributesBinding entry is flagged even when the first is correct`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.binding
            import io.github.stslex.workeeper.core.core.di.AppScope
            import io.github.stslex.workeeper.feature.archive.di.ArchiveScope

            @ContributesBinding(AppScope::class, binding = binding<ActivityHolder>())
            @ContributesBinding(ArchiveScope::class, binding = binding<ActivityHolderProducer>())
            @Inject
            class ActivityHolderImpl : ActivityHolder, ActivityHolderProducer
            """.trimIndent(),
        )

        assertEquals(
            1,
            findings.size,
            "the second, ArchiveScope-scoped entry must be reported — a correct first entry must not " +
                "shield it",
        )
        assertTrue(
            findings.first().message.contains("ArchiveScope"),
            "the finding must name the offending scope: ${findings.first().message}",
        )
    }

    @Test
    fun `every invalid entry is reported, not only the first invalid one`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.ui.kit.utils.activityHolder

            import dev.zacsweers.metro.ContributesBinding
            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.binding
            import io.github.stslex.workeeper.feature.archive.di.ArchiveScope
            import io.github.stslex.workeeper.feature.home.di.HomeScope

            @ContributesBinding(ArchiveScope::class, binding = binding<ActivityHolder>())
            @ContributesBinding(HomeScope::class, binding = binding<ActivityHolderProducer>())
            @Inject
            class ActivityHolderImpl : ActivityHolder, ActivityHolderProducer
            """.trimIndent(),
        )

        assertEquals(2, findings.size, "both mis-scoped entries must produce their own finding")
    }

    // No-op: unrelated classes are ignored.

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
