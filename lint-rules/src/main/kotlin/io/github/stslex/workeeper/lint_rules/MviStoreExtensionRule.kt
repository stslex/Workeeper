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
 * Rule to ensure proper BaseStore extension
 */
class MviStoreExtensionRule(config: Config = Config.Companion.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Warning,
        "MVI Store classes should extend BaseStore",
        Debt.Companion.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val className = klass.name ?: return

        if (className.endsWith("Store") && klass.isInMviModule()) {
            val superTypes = klass.getSuperTypeList()?.entries?.map { it.text }
            val extendsBaseStore = superTypes?.any { it.contains("BaseStore") } == true

            if (!extendsBaseStore) {
                report(
                    CodeSmell(
                        issue, Entity.Companion.from(klass),
                        "Store class '$className' should extend BaseStore for proper MVI implementation"
                    )
                )
            }
        }
    }

    private fun KtClass.isInMviModule(): Boolean {
        return containingKtFile.packageFqName.asString().contains("mvi") ||
               containingKtFile.virtualFilePath.contains("/mvi/")
    }
}