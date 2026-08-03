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
 * `collectAsLazyPagingItems()` is the kit's to call. Screens use `PagingUiState.collectAsItems()`.
 *
 * ## The defect this exists to prevent, measured
 *
 * `PagingUiState` is a `fun interface`, so invoking it builds a **new `Flow`** every time.
 * `collectAsLazyPagingItems()` caches on that flow — `remember(this) { LazyPagingItems(this) }` —
 * so a new flow means a new `LazyPagingItems`, which starts at `refresh = Loading` /
 * `itemCount = 0` (paging-compose 3.5.0, `InitialLoadStates`). Calling the fun-interface inline in
 * a composable therefore resets the list to *loading* on every recomposition.
 *
 * Three screens wrote `remember(state.pagingUiState) { state.pagingUiState() }`; Home wrote
 * `state.pagingUiState().collectAsLazyPagingItems()`. Measured on device on a **`debug`** build,
 * with a workout running — Home recomposes once a second on the session timer — that was **13 flow
 * rebuilds in 12 seconds**, each blanking the list to the paging spinner for ~23 ms. The three
 * wrapped screens composed twice on entry and never again.
 *
 * The build type splits that evidence in two, and only one half is a shipping claim: the **rebuild
 * count is structural** — a `fun interface` invocation allocates a new `Flow` under R8 exactly as
 * it does without it — while the **~23 ms blank is a debug duration**. Release would blank for
 * less; it would still blank, once a second. `CollectPagingItems`' KDoc carries the same split.
 *
 * ## Why a rule as well as a helper
 *
 * `collectAsItems()` puts the `remember` where a call site cannot omit it, which is the real fix.
 * This rule stops the raw call from being reintroduced — three of four sites were right *by
 * copying*, and copying is what failed on the fourth, so the guard has to be that there is nothing
 * unsafe left to copy. Neither half is sufficient alone: the helper does not stop someone reaching
 * past it, and a rule alone would only flag the mistake after it was made.
 *
 * ## Scope
 *
 * PSI-only, deliberately — this repo's custom rules run without type resolution, so a
 * type-resolving rule silently finds nothing in CI (see `detekt-no-type-resolution`). It matches
 * the callee **name**, which needs no types and cannot be defeated by an unusual receiver
 * expression. The kit's own `CollectPagingItems.kt` is the one legitimate caller and is excluded
 * by path suffix, not by package, so moving the file is a deliberate act that fails loudly.
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
        // The exclusion matches the helper's FULL repo-relative path, not its basename. A suffix of
        // "/CollectPagingItems.kt" exempts any file that happens to carry that name, anywhere in the
        // tree — which is an exemption granted by filename rather than by identity, and it would let
        // a feature file reintroduce the raw call with the rule watching and silent.
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

        /**
         * The kit's own wrapper — the single legitimate caller, matched on `virtualFilePath`.
         *
         * **`containingKtFile.name` does not work here and the first version used it.** Detekt
         * reported the rule against `CollectPagingItems.kt` itself on the first real run, i.e. it
         * flagged the one call it exists to permit. Every other rule in this module reaches for
         * `virtualFilePath`; diverging from that idiom is what produced a guard that never
         * matched. Kept as a leading `/` match so a same-named file elsewhere is not silently
         * excused.
         */
        const val KIT_HELPER_FILE =
            "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/" +
                "components/CollectPagingItems.kt"
    }
}
