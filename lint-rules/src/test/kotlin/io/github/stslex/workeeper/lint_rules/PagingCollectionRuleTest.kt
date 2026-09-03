// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Coverage for `PagingCollectionRule`. The kit-helper exclusion is deliberately untested here —
 * `lint()` synthesises the file name; the real detekt run over the tree is its coverage.
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
        // Correct, and still flagged: there must be exactly one supported spelling.
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
