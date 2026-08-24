// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Coverage for `FadeToTransparentRule`; PSI-only, so every case below is text matching. */
internal class FadeToTransparentRuleTest {

    private val rule = FadeToTransparentRule()

    @Test
    fun `flags a colour animation that fades to Color Transparent`() {
        val findings = rule.lint(
            """
            fun body(pressed: Boolean) {
                val bg by animateColorAsState(
                    targetValue = if (pressed) AppUi.colors.surfaceTier1 else Color.Transparent,
                )
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("fadedOut()"))
    }

    @Test
    fun `flags it whichever branch the transparent sits in`() {
        val findings = rule.lint(
            """
            fun body(done: Boolean) {
                val fill by animateColorAsState(
                    targetValue = if (done) Color.Transparent else plate,
                )
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `accepts the fadedOut form`() {
        val findings = rule.lint(
            """
            fun body(pressed: Boolean) {
                val bg by animateColorAsState(
                    targetValue = AppUi.colors.surfaceTier1.let { if (pressed) it else it.fadedOut() },
                )
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty())
    }

    /** An invisible surface never interpolates, so there is no mid-frame to be wrong. */
    @Test
    fun `ignores a static Color Transparent outside an animation`() {
        val findings = rule.lint(
            """
            fun body() {
                Box(modifier = Modifier.background(Color.Transparent))
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `ignores a non-colour animation`() {
        val findings = rule.lint(
            """
            fun body(lifted: Boolean) {
                val dp by animateDpAsState(targetValue = if (lifted) 8.dp else 0.dp)
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty())
    }

    /**
     * Recorded limit: a transparent laundered through a local carries no matching text into the
     * call. `FadeOutTest`'s per-site measurement is what covers it.
     */
    @Test
    fun `does NOT see a transparent laundered through a local — recorded limit`() {
        val findings = rule.lint(
            """
            fun body(pressed: Boolean) {
                val gone = Color.Transparent
                val bg by animateColorAsState(targetValue = if (pressed) plate else gone)
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), "if this ever fails the rule got stronger — update the KDoc")
    }
}
