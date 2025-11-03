package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass

/**
 * Rule to enforce proper naming conventions for MVI Events
 */
class MviEventNamingRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "MVI Events should follow naming conventions",
        debt = Debt.FIVE_MINS,
    )

    private val validSuffixes = listOf(
        "Success",
        "Error",
        "Completed",
        "Started",
        "Failed",
        "Requested",
    )
    private val validPatterns = listOf("Show", "Navigate", "Haptic", "Snackbar", "Scroll")

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("Event") && klass.isInMviModule()) {
            // Events should be sealed classes
            if (!klass.hasModifier(KtTokens.SEALED_KEYWORD) && !klass.isInterface()) {
                report(
                    CodeSmell(
                        issue, Entity.from(klass),
                        "Event class '$className' should be sealed class",
                    ),
                )
            }

            // Check nested event classes
            klass.declarations.filterIsInstance<KtClass>().forEach { nestedClass ->
                val nestedName = nestedClass.name ?: return@forEach
                if (!nestedName.endsWith("Event") && !isValidEventName(nestedName)) {
                    report(
                        CodeSmell(
                            issue, Entity.from(nestedClass),
                            "Event '$nestedName' should describe what happened (e.g., NavigationRequested, ErrorShown)",
                        ),
                    )
                }
            }
        }
    }

    private fun isValidEventName(name: String): Boolean = validSuffixes.any { name.endsWith(it) } ||
            validPatterns.any { name.contains(it) }

    private fun KtClass.isInMviModule(): Boolean =
        containingKtFile.packageFqName.asString().contains("mvi") ||
                containingKtFile.virtualFilePath.contains("/mvi/")
}