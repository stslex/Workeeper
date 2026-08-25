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
 * Domain layer must not import UI / Compose / `R` / mvi types; the flagged shapes are the `when`
 * below. `domain/mapper/` is not exempt — display strings belong in the feature's mvi/mapper/.
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
