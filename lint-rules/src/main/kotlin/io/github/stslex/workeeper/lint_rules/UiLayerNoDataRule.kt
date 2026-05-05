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
 * UI layer must not depend on data-layer model types. Files under any
 * `/ui/` path (kit modules under core/ui and feature ui subtrees) may
 * consume only domain types; the data-to-domain bridge lives in
 * `feature/<X>/domain/mapper/`.
 *
 * Unlike `DomainLayerPurityRule` there is no exemption for
 * `ui/mapper/`. The audit categorises any UI-side data import as a
 * leak, including in mapper files; UI mappers should map between
 * domain and UI types only.
 *
 * The rule is the symmetric counterpart of `DomainLayerPurityRule`,
 * matching by file-path heuristic (`/ui/` segment) and the same
 * data-shape suffix list (`DataModel`, `Entity`, `Dto`, etc.) plus
 * any package containing `.model.` from the data layer. Repository,
 * dispatcher, and other infrastructure imports from the data layer
 * are intentionally permitted; they are abstractions, not data
 * models.
 */
class UiLayerNoDataRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "UI layer must not import core.data.* model types.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val filePath = importDirective.containingKtFile.virtualFilePath
        if (filePath.isInTestSourceSet()) return
        if (!filePath.isInUiLayer()) return

        val importPath = importDirective.importPath?.pathStr ?: return
        if (!importPath.startsWith(CORE_DATA_PREFIX)) return
        if (!importPath.isDataModelLike()) return

        val simpleName = importPath.substringAfterLast('.')
        report(
            CodeSmell(
                issue,
                Entity.from(importDirective),
                "UI file imports data model `$simpleName` from `$importPath`. " +
                    "Replace with the corresponding domain or UI type and convert " +
                    "via the feature's domain/mapper/ + mvi/mapper/.",
            ),
        )
    }

    private fun String.isInUiLayer(): Boolean {
        // `core/ui/*` kit modules and any feature `ui/` subtree.
        // `core/ui/mvi` is excluded — it is the MVI framework and is
        // permitted to reference data abstractions if ever needed
        // (none currently).
        return (contains("/core/ui/") && !contains("/core/ui/mvi/")) ||
            contains("/ui/") &&
            !contains("/core/data/") // never flag the data layer's own files
    }

    private fun String.isInTestSourceSet(): Boolean =
        contains("/src/test/") || contains("/src/androidTest/")

    private fun String.isDataModelLike(): Boolean {
        val simpleName = substringAfterLast('.')
        return DATA_MODEL_SUFFIXES.any { simpleName.endsWith(it) } ||
            this.contains(".model.")
    }

    private companion object {
        const val CORE_DATA_PREFIX = "io.github.stslex.workeeper.core.data."

        val DATA_MODEL_SUFFIXES = listOf(
            "DataModel",
            "Entity",
            "Dto",
            "DataType",
        )
    }
}
