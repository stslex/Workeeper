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
 * Domain layer must stay platform-neutral (KMP-portable), so it enforces two things on
 * files under `feature/<X>/domain/`:
 *
 * 1. **No `android.*` imports.** The domain layer must not reference Android framework
 *    types — they must be neutralised behind an `expect`/`actual` seam or a neutral
 *    abstraction and converted at the mvi/ui edge. `androidx.*` is intentionally NOT
 *    flagged: `androidx.paging` / `androidx.datastore` types are multiplatform-portable
 *    and used legitimately in domain interactors (`startsWith("android.")` excludes
 *    `androidx.`). This check applies to `/domain/mapper/` too — a mapper must be portable.
 *    Limitation: it inspects import directives only; an inline fully-qualified
 *    `android.foo.Bar` reference with no import is not caught.
 * 2. **No data MODEL types from `core.data.*`** — the data → domain conversion lives in
 *    `feature/<X>/domain/mapper/` and is the only place such imports are allowed.
 *
 * For the data-model check, the rule flags imports under `feature/<X>/domain/` whose simple name
 * matches a known data-shape suffix (e.g. `DataModel`, `Entity`,
 * `*Row`) OR whose package path includes `.model.`. Repository
 * interfaces, dispatcher qualifiers, storage helpers, and other
 * infrastructure imports under `core.data.*` are intentionally
 * permitted — they are abstractions, not data models, and the audit
 * scope (V1/V3/V6) covers types in public surface, not transitive
 * dependencies.
 *
 * Two exemptions apply:
 *
 *  1. Files inside `feature/<X>/domain/mapper/` are exempt — a mapper's
 *     job is to convert from data to domain, so the data import is the
 *     contract.
 *  2. Imports from `core.data.<feature>.api.*` submodules are exempt —
 *     `core/data/<X>/api/` modules are the public contract surface
 *     shared across the codebase (analogous to a multi-module Maven
 *     api / impl split). Types under `.api.` (including `.api.model.*`)
 *     are intentionally consumable from any layer.
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

        // (1) Platform leak. Applies to ALL of /feature/*/domain/, mappers included — the
        // domain layer must be KMP-portable. `startsWith("android.")` excludes `androidx.*`
        // (multiplatform-portable, legitimately used in domain).
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

        // (2) Data-model leak. The /domain/mapper/ exemption applies to THIS check only — a
        // mapper's job is data → domain conversion, so the core.data import is its contract.
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
        // Match any /feature/<X>/domain/ file regardless of nesting depth.
        return contains("/feature/") && contains("/domain/")
    }

    private fun String.isInTestSourceSet(): Boolean =
        contains("/src/test/") || contains("/src/androidTest/")

    private fun String.isInDomainMapper(): Boolean = contains("/domain/mapper/")

    /**
     * Matches imports under `core.data.<feature>.api.*` — the second segment
     * after the [CORE_DATA_PREFIX] strip is `api`. The package convention
     * mirrors the module path `:core:data:<feature>:api/`, so this is a
     * structural check, not a substring search.
     */
    private fun String.isFromApiSubmodule(): Boolean {
        val suffix = removePrefix(CORE_DATA_PREFIX)
        val segments = suffix.split('.')
        return segments.getOrNull(1) == "api"
    }

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
        // Trailing dot is load-bearing: it makes `androidx.` fall outside the prefix, so
        // multiplatform-portable androidx types in domain/ are not misflagged as leaks.
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
