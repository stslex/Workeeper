// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Screens collect a paged list with `PagingUiState.collectAsItems()` — the raw
 * `collectAsLazyPagingItems()` resets it to Loading on every recomposition. See lint-rules.md.
 */
class PagingCollectionRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Collect a paged list with `PagingUiState.collectAsItems()`. Calling " +
            "`collectAsLazyPagingItems()` directly loses the `remember` that keeps the Flow " +
            "identity stable, and the list resets to Loading on every recomposition.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeExpression?.text != RAW_COLLECT) return
        // Full repo-relative path, not basename — a same-named file elsewhere is not excused.
        val path = expression.containingKtFile.virtualFilePath.replace('\\', '/')
        if (path.endsWith(KIT_HELPER_FILE)) return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "`$RAW_COLLECT()` caches on the Flow INSTANCE, and `PagingUiState` builds a new " +
                    "Flow on every invocation — so this resets the list to `refresh = Loading`, " +
                    "`itemCount = 0` on every recomposition (measured on a debug build: 13 " +
                    "rebuilds in 12s on a screen recomposing once a second, ~23ms of spinner " +
                    "each time — the rebuild count is structural, the duration is debug's). Use " +
                    "`state.<yourPagingUiState>.collectAsItems()`, which holds the `remember` " +
                    "where a call site cannot omit it.",
            ),
        )
    }

    private companion object {
        const val RAW_COLLECT = "collectAsLazyPagingItems"

        /** The kit's own wrapper — the single legitimate caller, matched on `virtualFilePath`. */
        const val KIT_HELPER_FILE =
            "core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/" +
                "components/CollectPagingItems.kt"
    }
}
