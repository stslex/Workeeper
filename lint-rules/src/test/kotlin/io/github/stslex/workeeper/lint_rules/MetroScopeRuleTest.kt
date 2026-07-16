// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [MetroScopeRule] — the Metro-only successor to the former `HiltScopeRule`.
 *
 * DI is 100% Metro. A constructor-injected, name-matched dependency (Handler / Interactor / Mapper /
 * Repository / …) must declare a `@SingleIn(<Scope>::class)`; a `*Handler` must not be
 * `@SingleIn(AppScope)`. The Hilt-annotation branches (requiring `@ViewModelScoped` / `@HiltViewModel`)
 * were deleted once those FQNs left every classpath — the checks here all key off annotations a developer
 * can still write (`dev.zacsweers.metro.SingleIn`, and the retained-but-wrong `javax.inject.@Singleton`).
 */
internal class MetroScopeRuleTest {

    private val rule = MetroScopeRule()

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
            "A @SingleIn Metro Handler with ctor @Inject must pass.",
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
        // class-level @Inject leaves the primary constructor un-annotated, so the rule short-circuits at
        // hasInject and never demands a scope.
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
    fun `ctor-Inject Handler with no scope is flagged (must declare SingleIn)`() {
        // The retained "must declare a scope" guard: a name-matched ctor-@Inject DI class with NO
        // @SingleIn is flagged — it either forgot the scope or used a non-Metro one.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dev.zacsweers.metro.Inject

            internal class ExampleClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A ctor-@Inject Handler with no @SingleIn must be flagged — it must declare its Metro scope.",
        )
    }

    @Test
    fun `ctor-Inject class annotated only javax Singleton is flagged (not a Metro scope)`() {
        // javax.inject is retained (Metro includeJavax), so @Singleton still RESOLVES and compiles — but
        // the Metro graph does not honour it. A name-matched ctor-@Inject class with only @Singleton and
        // no @SingleIn is silently unscoped under Metro, so it must be flagged.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject
            import javax.inject.Singleton

            @Singleton
            internal class ExampleClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "@Singleton (javax, resolves but Metro ignores it) with no @SingleIn must be flagged.",
        )
    }

    @Test
    fun `Metro Handler scoped to AppScope is flagged (soundness guard reads the scope arg)`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(AppScope::class)
            internal class ExampleClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A Handler @SingleIn(AppScope) must be flagged — a Handler is feature-scoped, never app-scoped.",
        )
    }

    @Test
    fun `Metro Handler scoped to its feature scope is not flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(ExampleScope::class)
            internal class ExampleClickHandler @Inject constructor(
                private val interactor: Any,
            )
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "A Handler @SingleIn(<FeatureScope>) must pass — only AppScope is blacklisted for Handlers.",
        )
    }

    @Test
    fun `non-Handler class scoped to AppScope is not flagged by the Handler guard`() {
        // The AppScope blacklist is Handler-specific: an Interactor/other class may legitimately
        // be app-scoped in some designs, so the guard must not fire outside the Handler bucket.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.domain

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @SingleIn(AppScope::class)
            internal class ExampleInteractorImpl @Inject constructor(
                private val repository: Any,
            )
            """.trimIndent(),
        )

        assertEquals(
            0,
            findings.size,
            "The AppScope guard is Handler-only; a non-Handler @SingleIn(AppScope) must not be flagged here.",
        )
    }
}
