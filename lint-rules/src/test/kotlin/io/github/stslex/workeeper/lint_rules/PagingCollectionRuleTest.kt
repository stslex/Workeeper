// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Coverage for `PagingCollectionRule`.
 *
 * PSI-only by necessity — this repo's detekt runs without type resolution, so a type-resolving
 * rule silently finds nothing in CI. The rule matches the callee name, which is why the receiver
 * shape does not matter and why the "safe" cases below are safe for a reason the rule can actually
 * see.
 *
 * **The kit-helper exclusion is deliberately NOT tested here.** `lint()` synthesises a file name,
 * so a case asserting "silent inside `CollectPagingItems.kt`" would be asserting the synthetic name
 * and not the exclusion — a green that means nothing. It is covered instead by the real detekt run
 * over the tree: if the exclusion broke, `CollectPagingItems.kt` itself would be flagged and the
 * build would go red. Proven by mutation (removing the `containingKtFile.name` guard reddens the
 * kit), not by a test that cannot see it.
 */
internal class PagingCollectionRuleTest {

    private val rule = PagingCollectionRule()

    @Test
    @DisplayName("flags the exact shape Home shipped")
    fun flagsTheHomeShape() {
        val findings = rule.lint(
            """
            @Composable
            fun HomeScreen(state: State) {
                val recent = state.pagingUiState().collectAsLazyPagingItems()
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("collectAsItems()"))
    }

    @Test
    @DisplayName("flags it even when wrapped in remember — the helper is the one supported form")
    fun flagsTheWrappedShapeToo() {
        // The three sibling screens' old form. It is CORRECT, and still flagged: leaving two
        // supported spellings is what let the fourth screen copy the wrong one. The rule's job is
        // that there is exactly one way in, not that every other way is broken.
        val findings = rule.lint(
            """
            @Composable
            fun Screen(state: State) {
                val items = remember(state.pagingUiState) {
                    state.pagingUiState()
                }.collectAsLazyPagingItems()
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("silent on the supported call")
    fun silentOnTheHelper() {
        val findings = rule.lint(
            """
            @Composable
            fun Screen(state: State) {
                val items = state.pagingUiState.collectAsItems()
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("silent on unrelated collects — the name match is exact, not a prefix")
    fun silentOnUnrelatedCollects() {
        val findings = rule.lint(
            """
            @Composable
            fun Screen(flow: Flow<Int>) {
                val v by flow.collectAsState(initial = 0)
                val w by flow.collectAsStateWithLifecycle()
            }
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }
}
