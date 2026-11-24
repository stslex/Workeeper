package io.github.stslex.workeeper.lint_rules

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.io.File

internal class KmpDatabaseReaders(
    private val module: String,
    private val files: Set<String>,
    private val symbolFiles: Set<String>,
) {
    private var aliases: Map<String, KtTypeAlias>? = null
    private var knownTypes = emptySet<String>()
    private var readers = emptyMap<String, Set<String>>()
    private var symbols = emptySet<String>()

    fun verify(file: KtFile): List<Pair<KtElement, String>> {
        if (module !in POLICIES || File(file.virtualFilePath).absoluteFile.normalize().path !in files) return emptyList()
        if (aliases == null) {
            val factory = KtPsiFactory(file.project)
            val sources = files.map { path ->
                if (path == file.virtualFilePath) file else factory.createFile(path, File(path).readText())
            }
            val symbolSources = symbolFiles.map { factory.createFile(it, File(it).readText()) }
            symbols = symbolSources.flatMap { it.collectDescendantsOfType<KtNamedDeclaration>() }
                .filter(::visibleAcrossFiles).mapNotNull { it.fqName?.asString() }.filter { it.startsWith("$DATABASE.") }.toSet()
            aliases = (sources + symbolSources).flatMap { it.collectDescendantsOfType<KtTypeAlias>() }
                .filter(::visibleAcrossFiles).associateBy { it.fqName?.asString() ?: it.name.orEmpty() }
            knownTypes = sources.flatMap { it.collectDescendantsOfType<KtNamedDeclaration>() }
                .filter(::visibleAcrossFiles).mapNotNull { it.fqName?.asString() }.toSet()
            val canonical = KotlinContractNames(aliases = checkNotNull(aliases), knownTypes = knownTypes, externalSymbols = symbols)
            readers = POLICIES.getValue(module).mapKeys { (owner, _) -> canonical.declaration(owner) }
        }
        val names = KotlinContractNames(file, checkNotNull(aliases), knownTypes, symbols)
        val findings = mutableListOf<Pair<KtElement, String>>()
        fun check(element: KtElement, reference: String) {
            if (element.getStrictParentOfType<KtImportDirective>() != null ||
                element.getStrictParentOfType<KtTypeAlias>() != null
            ) return
            if (reference.startsWith("<ambiguous>:")) {
                findings += element to "Ambiguous database reference needs an explicit import or qualification"
                return
            }
            if (!reference.startsWith("$DATABASE.")) return
            val owners = generateSequence(element.parent) { it.parent }.filterIsInstance<KtClassOrObject>()
                .mapNotNull { it.fqName?.asString() }
            val allowed = owners.any { owner -> readers[owner].orEmpty().any { type ->
                reference == type || reference.startsWith("$type.")
            } }
            if (!allowed) findings += element to "Database reference is outside its reviewed reader: $reference"
        }
        file.collectDescendantsOfType<KtUserType>().filter { it.parent !is KtUserType }.forEach { type ->
            names.types(type).forEach { check(type, it) }
        }
        file.collectDescendantsOfType<KtNameReferenceExpression>().forEach { check(it, names.reference(it)) }
        file.collectDescendantsOfType<KtDotQualifiedExpression>().filter { it.parent !is KtDotQualifiedExpression }
            .forEach { check(it, names.qualified(it)) }
        return findings.distinct()
    }

    private fun visibleAcrossFiles(declaration: KtNamedDeclaration): Boolean =
        generateSequence(declaration as KtElement?) { it.parent as? KtElement }
            .filterIsInstance<KtNamedDeclaration>().none { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }

    private companion object {
        const val DATABASE = "io.github.stslex.workeeper.core.data.database"
        const val LIVE = "io.github.stslex.workeeper.feature.live_workout"
        const val CHART = "io.github.stslex.workeeper.feature.exercise_chart"
        val POLICIES = mapOf(
            "feature:live-workout" to mapOf(
                "$LIVE.domain.mapper.LiveWorkoutDomainMapper" to setOf(DATABASE),
                "$LIVE.domain.LiveWorkoutInteractorImplTest" to setOf(
                    "$DATABASE.sets.PlanSetDataModel", "$DATABASE.sets.SetTypeDataModel",
                ),
                "$LIVE.mvi.mapper.ExerciseDoneRuleTest" to setOf("$DATABASE.testfixtures.WorkoutTargetParityFixture"),
            ),
            "feature:exercise-chart" to mapOf(
                "$CHART.domain.ChartFolderPrRuleParityTest" to setOf("$DATABASE.testfixtures.PrRuleFixture"),
            ),
        )
    }
}
