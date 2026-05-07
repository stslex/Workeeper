// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for `MviHandlerConstructorRule` after the lifecycle-safe navigation refactor.
 *
 * The rule expects every `*Handler` class implementing `Handler<...>` to declare a primary
 * constructor with `@Inject` and at least one parameter. The literal class name
 * `NavigationHandler` is exempt at the source level (`MviHandlerConstructorRule.kt:74`)
 * for historical reasons. The current architecture uses normal Hilt constructor injection
 * on every handler — `NavigationHandler` included — so the exemption is only there for
 * back-compat. These tests pin both halves of that contract: the canonical
 * `@Inject` shape passes cleanly, and missing-`@Inject` non-NavigationHandler variants
 * still get flagged.
 */
internal class MviHandlerConstructorRuleNavigationTest {

    private val rule = MviHandlerConstructorRule()

    @Test
    fun `NavigationHandler with Inject Navigator passes the rule`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject

            interface Handler<A>

            internal class NavigationHandler @Inject constructor(
                private val navigator: Any,
            ) : Handler<Any>
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `NavigationHandler without Inject still passes due to the literal-name exemption`() {
        // The rule has a literal-name exemption (line 74). New code should not rely on
        // it — the canonical shape is `@Inject` on every handler — but the exemption
        // remains so legacy code compiles. Pin its current behavior so the exemption
        // is removed only deliberately.
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            interface Handler<A>

            internal class NavigationHandler(
                private val navigator: Any,
            ) : Handler<Any>
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `Variant NavigationHandler name does NOT inherit the exemption`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            interface Handler<A>

            internal class SettingsNavigationHandler(
                private val navigator: Any,
            ) : Handler<Any>
            """.trimIndent(),
        )

        assertTrue(
            findings.isNotEmpty(),
            "SettingsNavigationHandler must be flagged when missing @Inject — only the literal name `NavigationHandler` is exempt.",
        )
    }

    @Test
    fun `ClickHandler missing Inject is flagged`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            interface Handler<A>

            internal class ClickHandler(
                private val store: Any,
            ) : Handler<Any>
            """.trimIndent(),
        )

        assertTrue(findings.isNotEmpty())
    }

    @Test
    fun `ClickHandler with Inject and dependencies passes`() {
        val findings = rule.lint(
            """
            package io.github.stslex.workeeper.feature.example.mvi.handler

            import javax.inject.Inject

            interface Handler<A>

            internal class ClickHandler @Inject constructor(
                private val store: Any,
            ) : Handler<Any>
            """.trimIndent(),
        )

        assertEquals(0, findings.size)
    }
}
