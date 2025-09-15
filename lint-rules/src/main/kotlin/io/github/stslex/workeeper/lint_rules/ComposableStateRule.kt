package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Rule to check proper Composable state handling
 */
class ComposableStateRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Warning,
        "Composables should properly handle state",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val functionName = function.name ?: return
        val annotations = function.annotationEntries
        val isComposable = annotations.any { it.shortName?.asString() == "Composable" }

        if (isComposable && functionName.endsWith("Screen")) {
            // Check if screen composable has proper state handling
            var hasStateParameter = false
            var hasEventParameter = false

            function.valueParameters.forEach { param ->
                val paramType = param.typeReference?.text ?: return@forEach
                if (paramType.endsWith("State")) {
                    hasStateParameter = true
                }
                if (paramType.contains("Event") || paramType.contains("Action")) {
                    hasEventParameter = true
                }
            }

            if (!hasStateParameter) {
                report(
                    CodeSmell(
                        issue, Entity.Companion.from(function),
                        "Screen Composable '$functionName' should have a state parameter"
                    )
                )
            }

            if (!hasEventParameter) {
                report(
                    CodeSmell(
                        issue, Entity.Companion.from(function),
                        "Screen Composable '$functionName' should have an event handler parameter"
                    )
                )
            }
        }
    }
}