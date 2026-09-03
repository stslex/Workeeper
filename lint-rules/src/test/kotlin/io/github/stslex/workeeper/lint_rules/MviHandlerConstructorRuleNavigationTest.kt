// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for `MviHandlerConstructorRule`: the literal class name `NavigationHandler` is exempt
 * from the `@Inject` requirement, and no other `*Handler` name inherits that exemption.
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
        // Pinned so the literal-name exemption is removed only deliberately.
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
