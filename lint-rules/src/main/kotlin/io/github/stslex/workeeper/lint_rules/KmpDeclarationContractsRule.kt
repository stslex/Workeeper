package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.io.File

/** Inventory is compared across evaluated module inputs, so removing a file cannot erase its obligation. */
class KmpDeclarationContractsRule(config: Config = Config.empty) : Rule(config, RequiredFindings()) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Reviewed expect seams, suppressions and portable previews must remain accounted for.",
        Debt.TWENTY_MINS,
    )
    private val files by lazy { configured("allFiles").sorted() }
    private val common by lazy { configured("commonProductionFiles").toSet() }
    private val expected by lazy { valueOrDefault("expectedDeclarations", emptyList<String>()).sorted() }

    private fun configured(key: String) = valueOrDefault(key, emptyList<String>()).map {
        val file = File(it)
        if (file.isAbsolute) file.path else File(valueOrDefault("moduleDirectory", "."), it).absoluteFile.normalize().path
    }

    override fun visitCondition(root: KtFile): Boolean = active

    override fun visitKtFile(file: KtFile) {
        val current = File(file.virtualFilePath).absoluteFile.normalize().path
        if (current != files.firstOrNull()) return
        val factory = KtPsiFactory(file.project)
        val inputs = files.map { path ->
            val parsed = if (path == current) file else factory.createFile(path, File(path).readText())
            DeclarationSource(parsed, path in common)
        }
        val actual = KmpDeclarationInventory.collect(
            inputs,
            valueOrDefault("previewSubjects", emptyList<String>()).toSet(),
            valueOrDefault("verifyPreviewBodies", false),
        )
        val missing = expected.toMutableList()
        val unexpected = actual.toMutableList()
        actual.forEach { row -> if (missing.remove(row)) unexpected.remove(row) }
        (missing.map { "Missing reviewed declaration: $it" } + unexpected.map { "Unreviewed declaration: $it" })
            .forEach { report(CodeSmell(issue, Entity.from(file), it)) }
    }
}

internal data class DeclarationSource(val file: KtFile, val common: Boolean)

internal object KmpDeclarationInventory {
    fun collect(
        sources: List<DeclarationSource>,
        subjects: Set<String> = emptySet(),
        verifyPreviewBodies: Boolean = false,
    ): List<String> {
        val previews = KmpPreviewContracts(sources.filter { it.common }.map { it.file }, subjects)
        return sources.flatMap { source ->
        val file = source.file
        val names = KotlinContractNames(file)
        val rows = mutableListOf<String>()
        val functions = file.collectDescendantsOfType<KtNamedFunction>()
        file.collectDescendantsOfType<KtAnnotationEntry>().forEach { annotation ->
            val type = (annotation.typeReference?.typeElement as? org.jetbrains.kotlin.psi.KtUserType)?.let(names::type)
            if (type in setOf("Suppress", "kotlin.Suppress")) {
                val owner = annotation.getStrictParentOfType<KtNamedDeclaration>()?.identity()
                    ?: "file:${file.packageFqName}:${file.declarations.mapNotNull { (it as? KtNamedDeclaration)?.name }.sorted()}"
                val target = annotation.useSiteTarget?.getAnnotationUseSiteTarget()?.renderName.orEmpty()
                val diagnostics = annotation.valueArguments.map { scalar(it.getArgumentExpression(), names) }.sorted()
                rows += "suppress|$owner|$target|${diagnostics.joinToString(",")}"
            }
            if (source.common && type == KmpPreviewContracts.PREVIEW) {
                val function = annotation.getStrictParentOfType<KtNamedFunction>()
                if (function == null) {
                    rows += "preview|invalid-target"
                } else {
                    val arguments = annotation.valueArguments.mapIndexed { index, argument ->
                        val name = argument.getArgumentName()?.asName?.asString() ?: if (index == 0) "name" else "position:$index"
                        "$name=${scalar(argument.getArgumentExpression(), names)}"
                    }.sorted().joinToString(",")
                    val rendered = if (verifyPreviewBodies) "|subjects=" + previews.render(function).joinToString("|") else ""
                    rows += "preview|${function.identity()}|$arguments$rendered"
                }
            }
        }
        if (source.common) file.collectDescendantsOfType<KtNamedDeclaration>().filter {
            (it as? KtModifierListOwner)?.hasModifier(KtTokens.EXPECT_KEYWORD) == true
        }.forEach { rows += "expect|${it.identity()}" }
        rows
        }.sorted()
    }

    private fun KtNamedDeclaration.identity(): String = fqName?.asString() ?: (
        listOf(containingKtFile.packageFqName.asString()) +
            generateSequence(this as KtElement?) { it.parent as? KtElement }
                .filterIsInstance<KtNamedDeclaration>().mapNotNull { it.name }.toList().asReversed()
        ).joinToString(".")

    private fun scalar(element: KtElement?, names: KotlinContractNames): String = when (element) {
        is KtStringTemplateExpression -> element.entries.joinToString("") {
            when (it) {
                is KtLiteralStringTemplateEntry -> it.text
                is KtEscapeStringTemplateEntry -> it.unescapedValue
                else -> "<nonliteral>"
            }
        }.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")
        is KtConstantExpression -> element.text
        is KtNameReferenceExpression -> names.reference(element)
        is KtDotQualifiedExpression -> names.qualified(element)
        else -> "<nonliteral>"
    }
}
