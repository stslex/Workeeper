// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Context
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSetId
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias
import java.io.File

class MviSharedPlatformRule(config: Config = Config.empty) : Rule(config, RequiredFindings()) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Shared MVI source sets must avoid Android platform imports and legacy lifetime lookup.",
        debt = Debt.TWENTY_MINS,
    )

    private val sharedProduction by lazy { configuredFiles("sharedProductionFiles") }
    private val sharedTest by lazy { configuredFiles("sharedTestFiles") }
    private val production by lazy { configuredFiles("productionFiles") }
    private val tests by lazy { configuredFiles("testFiles") }

    // The required migration check must still visit files with @file:Suppress.
    override fun visitCondition(root: KtFile): Boolean = active

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.modelPath()
        val imported = importDirective.importedFqName?.asString() ?: return
        if (path in sharedProduction && PLATFORM_PACKAGES.any(imported::startsWith)) {
            finding(importDirective, "Shared MVI imports platform API `$imported`.")
        }
        if (imported == APP_DEPS_HOLDER && isShared(path)) {
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
        when (declaration.name) {
            "StoreGenerationDeps" -> {
                val path = declaration.modelPath()
                if (path in production || path !in tests && !TestSourceSets.isTestFile(path)) {
                    finding(declaration, "StoreGenerationDeps is retired. Inject the generation lifetime directly.")
                }
            }
            "AppDepsHolder" -> if (isShared(declaration.modelPath())) {
                finding(declaration, "AppDepsHolder belongs to the Android MVI source set.")
            }
        }
    }

    private fun finding(element: KtElement, message: String) {
        report(CodeSmell(issue, Entity.from(element), message))
    }

    private fun configuredFiles(key: String): Set<String> = valueOrDefault(key, emptyList<String>()).toSet()

    private fun KtElement.modelPath(): String = File(containingKtFile.virtualFilePath).absoluteFile.normalize().path

    private fun isShared(path: String): Boolean = path in sharedProduction || path in sharedTest

    private companion object {
        const val APP_DEPS_HOLDER = "io.github.stslex.workeeper.core.ui.mvi.di.AppDepsHolder"
        val PLATFORM_PACKAGES = listOf("android.", "androidx.activity.", "java.", "javax.", "com.google.firebase.")
    }
}

// This rule reports a migration invariant even when its declaration has a suppression annotation.
private class RequiredFindings : Context {

    private val reported = mutableListOf<Finding>()
    override val findings: List<Finding> get() = reported.toList()

    override fun report(finding: Finding, aliases: Set<String>, ruleSetId: RuleSetId?) {
        reported.add(finding)
    }

    override fun clearFindings() = reported.clear()
}
