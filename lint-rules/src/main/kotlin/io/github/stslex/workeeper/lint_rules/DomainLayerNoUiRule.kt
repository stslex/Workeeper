package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Domain layer must not depend on UI / Compose / resource / mvi types.
 * The rule flags any import under `feature/<X>/domain/` (including
 * `domain/mapper/`, since UI dependencies are forbidden in mappers too)
 * that matches:
 *
 * - `*UiModel` — UI model types belong in mvi/.
 * - `androidx.compose.*` — Compose annotations / state types belong in UI.
 * - `*.R` and `*.R.*` — string/resource lookups go through the UI mapper
 *   via stringResource(R.string.*), not through the domain.
 * - imports whose package path contains `/ui/` or `/mvi/` — UI / mvi
 *   types must not flow back into domain.
 */
class DomainLayerNoUiRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Domain layer must not import UI / Compose / R / mvi types.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val filePath = importDirective.containingKtFile.virtualFilePath
        if (!filePath.isInFeatureDomain()) return

        val importPath = importDirective.importPath?.pathStr ?: return
        val simpleName = importPath.substringAfterLast('.')

        val violation = when {
            importPath.startsWith("androidx.compose.") -> "Compose"
            simpleName == "R" -> "R class"
            importPath.contains(".R.") -> "R resource"
            simpleName.endsWith("UiModel") -> "UiModel"
            importPath.contains(".ui.") -> "UI package"
            importPath.contains(".mvi.") -> "MVI package"
            else -> null
        } ?: return

        report(
            CodeSmell(
                issue,
                Entity.from(importDirective),
                "Domain file imports a $violation type `$simpleName`. " +
                    "Move display strings / UI conversions to the feature's mvi/mapper/.",
            ),
        )
    }

    private fun String.isInFeatureDomain(): Boolean {
        return contains("/feature/") && contains("/domain/")
    }
}
