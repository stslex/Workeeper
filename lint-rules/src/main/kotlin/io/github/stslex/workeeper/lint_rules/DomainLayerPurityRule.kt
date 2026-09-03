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
 * Keeps `feature/<X>/domain/` platform-neutral: no `android.*` imports, and no `core.data.*` model
 * types outside `/domain/mapper/` and `.api.` submodules. See documentation/lint-rules.md.
 */
class DomainLayerPurityRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Domain layer must stay platform-neutral: no android.* imports, and no " +
            "core.data.* model types except in /mapper/.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val filePath = importDirective.containingKtFile.virtualFilePath
        if (filePath.isInTestSourceSet()) return
        if (!filePath.isInFeatureDomain()) return

        val importPath = importDirective.importPath?.pathStr ?: return

        // (1) Platform leak, mappers included; `android.` excludes portable `androidx.*`.
        // TODO(tech-debt): inline `android.*` FQN (no import) is not detected — the rule
        //  inspects import directives only. This gap SELF-CLOSES at the KMP split: commonMain
        //  physically cannot see `android.*`, so an inline android FQN won't compile there. FQN
        //  detection in Detekt is only needed for the transitional period (domain still in
        //  androidMain but required portable), covered by review until then. Domain is
        //  FQN-clean today (verified), so the gap is inactive.
        if (importPath.startsWith(ANDROID_PREFIX)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(importDirective),
                    "Domain file imports platform type `${importPath.substringAfterLast('.')}` " +
                        "from `$importPath`. The domain layer must be platform-neutral " +
                        "(KMP-portable) — neutralise it behind an expect/actual seam or a " +
                        "neutral abstraction and convert at the mvi/ui edge.",
                ),
            )
            return
        }

        // (2) Data-model leak. The /domain/mapper/ exemption applies to THIS check only.
        if (filePath.isInDomainMapper()) return
        if (!importPath.startsWith(CORE_DATA_PREFIX)) return
        if (importPath.isFromApiSubmodule()) return
        if (!importPath.isDataModelLike()) return

        val simpleName = importPath.substringAfterLast('.')
        report(
            CodeSmell(
                issue,
                Entity.from(importDirective),
                "Domain file imports data model `$simpleName` from `$importPath`. " +
                    "Replace with the feature-local *Domain type and convert via " +
                    "/domain/mapper/.",
            ),
        )
    }

    private fun String.isInFeatureDomain(): Boolean {
        return contains("/feature/") && contains("/domain/")
    }

    private fun String.isInTestSourceSet(): Boolean =
        contains("/src/test/") || contains("/src/androidTest/")

    private fun String.isInDomainMapper(): Boolean = contains("/domain/mapper/")

    /**
     * Matches `core.data.<feature>.api.*` — a structural segment check mirroring the module path
     * `:core:data:<feature>:api/`, not a substring search.
     */
    private fun String.isFromApiSubmodule(): Boolean {
        val suffix = removePrefix(CORE_DATA_PREFIX)
        val segments = suffix.split('.')
        return segments.getOrNull(1) == "api"
    }

    private fun String.isDataModelLike(): Boolean {
        // Suffix heuristic for data shapes; repositories and DAOs are abstractions, not models.
        val simpleName = substringAfterLast('.')
        return DATA_MODEL_SUFFIXES.any { simpleName.endsWith(it) } ||
            this.contains(".model.") ||
            this.contains(".sets.") && simpleName.endsWith("Sets") ||
            this == "io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem" ||
            this == "io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.BulkArchiveOutcome" ||
            this == "io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.BulkArchiveOutcome" ||
            this == "io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.InlineAdhocResult" ||
            this == "io.github.stslex.workeeper.core.data.exercise.session.SessionRepository.ActiveSessionWithStats" ||
            this == "io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver.Resolution" ||
            this == "io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver.StartDecision"
    }

    private companion object {
        // Trailing dot is load-bearing: it keeps portable `androidx.` outside the prefix.
        const val ANDROID_PREFIX = "android."
        const val CORE_DATA_PREFIX = "io.github.stslex.workeeper.core.data."

        val DATA_MODEL_SUFFIXES = listOf(
            "DataModel",
            "Entity",
            "Dto",
            "DataType",
            "HistoryEntry",
            "SetSummary",
            "PlanSet",
            "ActiveSessionInfo",
        )
    }
}
