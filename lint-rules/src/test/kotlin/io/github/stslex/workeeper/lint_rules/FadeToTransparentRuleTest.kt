// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for `FadeToTransparentRule`.
 *
 * The rule is PSI-only by necessity — this repo's detekt runs without type resolution, so a
 * type-resolving rule silently finds nothing in CI. Everything below therefore tests text
 * matching, including the cases the rule deliberately cannot see.
 */
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

    /**
     * A surface that is simply invisible never interpolates, so there is no mid-frame to be wrong.
     * Flagging static transparency would make the rule noise and get it switched off.
     */
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
     * The known blind spot, asserted so it is a recorded limit rather than an assumption. A value
     * laundered through a local carries no `Color.Transparent` text into the call, and a PSI rule
     * cannot follow it. `FadeOutTest`'s per-site measurement is what covers this.
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
