// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for [MetroScopeRule] — the Metro-only successor to the former `HiltScopeRule`.
 *
 * DI is 100% Metro. A Metro-injected, name-matched dependency (Handler / Interactor / Mapper /
 * Repository / …) must declare a `@SingleIn(<Scope>::class)`; a `*Handler` must not be
 * `@SingleIn(AppScope)`. Both `@Inject` shapes are inspected — on the primary constructor and on the
 * class — and the only name-based exemption is the ViewModelStore-retained `*StoreImpl`, which does NOT
 * extend to the `*HandlerStoreImpl` adapters. The Hilt-annotation branches (requiring
 * `@ViewModelScoped` / `@HiltViewModel`)
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

    /**
     * Class-level `@Inject` is the dominant shape in the tree (every `*InteractorImpl`,
     * `*HandlerStoreImpl`, `StateStatusMapper`, the `*DataStoreImpl`s …). A 0-finding assertion on the
     * scoped variant alone proves nothing — it stays green if the rule never inspects the class at all.
     * This is the pair: identical source, `@SingleIn` removed, must flag.
     */
    @Test
    fun `class-level Inject is inspected — scoped Interactor passes, unscoped one is flagged`() {
        val scoped = rule.lint(
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
        val unscoped = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.domain

            import dev.zacsweers.metro.Inject

            @Inject
            internal class ArchiveInteractorImpl(
                private val repository: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, scoped.size, "A @SingleIn Metro Interactor with class-level @Inject must pass.")
        assertTrue(
            unscoped.isNotEmpty(),
            "A class-level-@Inject Interactor with NO @SingleIn must be flagged — class-level @Inject is " +
                "injection too, and the class is silently unscoped.",
        )
    }

    /**
     * The `*HandlerStoreImpl` adapters carry class-level `@Inject` and no primary-constructor parens at
     * all, so `primaryConstructor` is null. They are ordinary feature-scoped graph nodes (NOT the
     * ViewModelStore-retained Store), so the scope requirement applies to them.
     */
    @Test
    fun `HandlerStoreImpl with class-level Inject and no scope is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.di

            import dev.zacsweers.metro.Inject

            @Inject
            class ArchiveHandlerStoreImpl : ArchiveHandlerStore,
                BaseHandlerStore<State, Action, Event>()
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "A *HandlerStoreImpl with no @SingleIn must be flagged — it is not exempt as a Store.",
        )
    }

    @Test
    fun `HandlerStoreImpl scoped to its feature scope passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.di

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @Inject
            @SingleIn(ArchiveScope::class)
            class ArchiveHandlerStoreImpl : ArchiveHandlerStore,
                BaseHandlerStore<State, Action, Event>()
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "the shape every feature ships must stay green")
    }

    @Test
    fun `HandlerStoreImpl scoped to AppScope is flagged by the Handler guard`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.archive.di

            import dev.zacsweers.metro.Inject
            import dev.zacsweers.metro.SingleIn

            @Inject
            @SingleIn(AppScope::class)
            class ArchiveHandlerStoreImpl : ArchiveHandlerStore,
                BaseHandlerStore<State, Action, Event>()
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "@SingleIn(AppScope) on a HandlerStore pins one BaseHandlerStore._store to the process.",
        )
    }

    /**
     * A Metro Store is intentionally UNSCOPED (retained by the Android `ViewModelStore`), and is now
     * exempted BY NAME rather than by which `@Inject` shape it happens to use. This input has two reasons
     * to pass — the `*StoreImpl` exemption and the fact that `ArchiveStoreImpl` matches no dependency
     * bucket — so it is a regression anchor, not a proof of the exemption. The `*HandlerStoreImpl` tests
     * above are what pin the exemption's boundary: it must not reach the adapters.
     */
    @Test
    fun `Metro Store with class-level Inject and no scope passes`() {
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

    /**
     * `@AssistedInject` is NOT injection for this rule: Metro forbids scoping an assisted type, so
     * demanding a `@SingleIn` would be unsatisfiable. `DataStoreProvider` is the live example.
     */
    @Test
    fun `AssistedInject class in a bucket is not required to declare a scope`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.core.data.dataStore.core

            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject

            class DataStoreProvider @AssistedInject constructor(
                @Assisted private val name: String,
                context: Any,
            )
            """.trimIndent(),
        )

        assertEquals(0, findings.size, "Metro forbids scoping an assisted type, so it must not be flagged.")
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
