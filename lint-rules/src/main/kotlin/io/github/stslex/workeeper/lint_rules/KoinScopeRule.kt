package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass

/**
 * Rule to check proper Koin scope usage
 */
class KoinScopeRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Warning,
        "Koin scoped components should use proper scope annotations",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        // Check ViewModels for proper scoping
        val annotations = klass.annotationEntries
        val hasKoinViewModel = annotations.any { it.shortName?.asString() == "KoinViewModel" }
        val hasScoped = annotations.any { it.shortName?.asString() == "Scoped" }

        if (className.endsWith("Store") && hasKoinViewModel && !hasScoped) {
            report(
                CodeSmell(
                    issue, Entity.Companion.from(klass),
                    "Store class '$className' with @KoinViewModel should also have @Scoped annotation"
                )
            )
        }
    }
}