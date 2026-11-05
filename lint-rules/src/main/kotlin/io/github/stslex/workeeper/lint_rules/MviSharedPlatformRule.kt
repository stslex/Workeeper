// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias

class MviSharedPlatformRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Shared MVI source sets must avoid Android platform imports and legacy lifetime lookup.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.containingKtFile.virtualFilePath
        val imported = importDirective.importedFqName?.asString() ?: return
        val sourceSet = path.mviSourceSet()
        if (sourceSet in SHARED_PRODUCTION && PLATFORM_PACKAGES.any(imported::startsWith)) {
            finding(importDirective, "Shared MVI imports platform API `$imported`.")
        }
        if (sourceSet in SHARED_SOURCE_SETS && imported == APP_DEPS_HOLDER) {
            finding(importDirective, "AppDepsHolder belongs to the Android MVI source set.")
        }
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        checkLookupDeclaration(classOrObject)
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        checkLookupDeclaration(typeAlias)
    }

    private fun checkLookupDeclaration(declaration: KtNamedDeclaration) {
        val path = declaration.containingKtFile.virtualFilePath
        when {
            declaration.name == "StoreGenerationDeps" && !TestSourceSets.isTestFile(path) ->
                finding(declaration, "StoreGenerationDeps is retired. Inject the generation lifetime directly.")

            declaration.name == "AppDepsHolder" && path.mviSourceSet() in SHARED_SOURCE_SETS ->
                finding(declaration, "AppDepsHolder belongs to the Android MVI source set.")
        }
    }

    private fun finding(element: KtElement, message: String) {
        report(CodeSmell(issue, Entity.from(element), message))
    }

    private fun String.mviSourceSet(): String? {
        val location = substringAfter("/core/ui/mvi/src/", "")
        return location.substringBefore('/').takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val APP_DEPS_HOLDER = "io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder"
        val SHARED_PRODUCTION = setOf("commonMain", "iosMain")
        val SHARED_SOURCE_SETS = SHARED_PRODUCTION + setOf("commonTest", "iosTest")
        val PLATFORM_PACKAGES = listOf("android.", "androidx.activity.", "java.", "javax.", "com.google.firebase.")
    }
}
