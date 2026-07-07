// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the Metro-path awareness added to `HiltScopeRule` for the KMP C.1 M0
 * migration (feature/archive flipped from Hilt to Metro DI).
 *
 * A Metro-managed component carries `@SingleIn(<Scope>::class)` instead of Hilt's
 * `@ViewModelScoped` / `@Singleton`. The rule accepts `@SingleIn` as satisfying the scope
 * requirement for the scoped buckets (`Handler`, `Interactor`, `Mapper`, singleton names),
 * and — because a Metro graph owns the scope, not a Hilt component — does not then demand the
 * Hilt annotation.
 *
 * These tests pin BOTH directions: the Metro path passes, AND the Hilt path still fires (the
 * guard is not disarmed) for the untouched 11 features.
 */
internal class HiltScopeRuleMetroTest {

    private val rule = HiltScopeRule()

    @Test
    fun `Metro Handler with SingleIn ctor injection passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.mvi.handler

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(ArchiveScope::class)
            internal class ArchiveClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "A @SingleIn Metro Handler with ctor @Inject must pass — @SingleIn is the @ViewModelScoped analogue.",
        )
    }

    @Test
    fun `Metro Interactor with SingleIn class-level injection passes`() {
        // Non-Handler classes carry class-level @Inject; the rule's hasInject check reads the
        // primary constructor, so it already short-circuits. @SingleIn keeps it explicitly valid.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.domain

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @Inject
            @SingleIn(ArchiveScope::class)
            internal class ArchiveInteractorImpl(
                private val repository: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "A @SingleIn Metro Interactor must pass.")
    }

    @Test
    fun `Metro Store with class-level Inject and no scope passes`() {
        // A Metro Store is intentionally UNSCOPED (retained by the Android ViewModelStore). Its
        // class-level @Inject leaves the primary constructor un-annotated, so the rule
        // short-circuits at hasInject and never demands @HiltViewModel.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.mvi.store

            import dev.zacsweers.metro.Inject

            @Inject
            internal class ArchiveStoreImpl(
                private val handler: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "A Metro @Inject Store (unscoped, class-level @Inject) must pass.")
    }

    @Test
    fun `Hilt Handler without any scope still triggers a finding`() {
        // Guard intact: a ctor-@Inject Handler on the Hilt path with NO @SingleIn and NO
        // @ViewModelScoped must still be flagged for the untouched features.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject

            internal class ExampleClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A Hilt Handler (@Inject, no @SingleIn, no @ViewModelScoped) must still be flagged.",
        )
    }
}
