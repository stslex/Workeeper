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
 * Rule to ensure MVI State classes are immutable (data classes)
 */
class MviStateImmutabilityRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.CodeSmell,
        "MVI State classes should be immutable data classes",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("State") &&
            klass.isInMviModule() &&
            !klass.isData() &&
            !klass.hasModifier(KtTokens.SEALED_KEYWORD)
        ) {

            report(
                CodeSmell(
                    issue, Entity.Companion.from(klass),
                    "State class '$className' should be a data class for immutability"
                )
            )
        }

        // Check for mutable properties in State classes
        if (className.endsWith("State") && klass.isInMviModule()) {
            klass.getProperties().forEach { property ->
                if (property.isVar) {
                    report(
                        CodeSmell(
                            issue, Entity.Companion.from(property),
                            "State properties should be immutable (val, not var)"
                        )
                    )
                }

                // Check for mutable collections
                val typeReference = property.typeReference?.text
                if (typeReference?.contains("MutableList") == true ||
                    typeReference?.contains("MutableSet") == true ||
                    typeReference?.contains("MutableMap") == true
                ) {
                    report(
                        CodeSmell(
                            issue, Entity.Companion.from(property),
                            "State should use immutable collections (List, Set, Map instead of Mutable*)"
                        )
                    )
                }
            }
        }
    }

    private fun KtClass.isInMviModule(): Boolean {
        return containingKtFile.packageFqName.asString().contains("mvi") ||
                containingKtFile.virtualFilePath.contains("/mvi/")
    }
}