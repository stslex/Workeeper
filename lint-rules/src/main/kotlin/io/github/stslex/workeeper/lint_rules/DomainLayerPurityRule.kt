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
 * Domain layer must not depend on data MODEL types from `core.data.*` —
 * the data → domain conversion lives in `feature/<X>/domain/mapper/` and
 * is the only place such imports are allowed.
 *
 * The rule flags imports under `feature/<X>/domain/` whose simple name
 * matches a known data-shape suffix (e.g. `DataModel`, `Entity`,
 * `*Row`) OR whose package path includes `.model.`. Repository
 * interfaces, dispatcher qualifiers, storage helpers, and other
 * infrastructure imports under `core.data.*` are intentionally
 * permitted — they are abstractions, not data models, and the audit
 * scope (V1/V3/V6) covers types in public surface, not transitive
 * dependencies.
 *
 * Files inside `feature/<X>/domain/mapper/` are exempt: a mapper's
 * job is to convert from data to domain, so the data import is the
 * contract.
 */
class DomainLayerPurityRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Domain layer must not import core.data.* model types except in /mapper/.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val filePath = importDirective.containingKtFile.virtualFilePath
        if (!filePath.isInFeatureDomain() || filePath.isInDomainMapper()) return

        val importPath = importDirective.importPath?.pathStr ?: return
        if (!importPath.startsWith(CORE_DATA_PREFIX)) return

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
        // Match any /feature/<X>/domain/ file regardless of nesting depth.
        return contains("/feature/") && contains("/domain/")
    }

    private fun String.isInDomainMapper(): Boolean = contains("/domain/mapper/")

    private fun String.isDataModelLike(): Boolean {
        // Heuristic: suffix-based detection for the known data-shape patterns
        // the audit identifies as V1 leaks. Repository / Storage / Dao /
        // Dispatcher / similar utility types are intentionally not in this
        // list — they are abstractions, not data models.
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
