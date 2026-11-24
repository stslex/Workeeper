package io.github.stslex.workeeper.lint_rules

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal object KmpFactoryOwnership {
    private const val RETAINER = "io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor"
    private val BASES = setOf(
        "io.github.stslex.workeeper.core.ui.mvi.Feature",
        "io.github.stslex.workeeper.core.ui.mvi.FeatureAssisted",
        "io.github.stslex.workeeper.core.ui.mvi.AppFeature",
    )

    fun verify(klass: KtClass, names: KotlinContractNames): List<Pair<KtElement, String>> {
        val bases = klass.superTypeListEntries.flatMap { it.collectDescendantsOfType<KtUserType>() }.map(names::type)
        if (bases.none { it in BASES }) return emptyList()
        val findings = mutableListOf<Pair<KtElement, String>>()
        val factories = klass.primaryConstructorParameters.filter { parameter ->
            val type = parameter.typeReference?.typeElement
            val outer = (type as? KtUserType) ?: (type as? KtNullableType)?.innerType as? KtUserType
            outer != null && names.type(outer).endsWith(".Factory")
        }
        if (factories.size != 1 || factories.any {
                val type = it.typeReference?.typeElement
                it.defaultValue != null || type is KtNullableType ||
                    type is KtUserType && names.nullable(type) || it.isMutable
            }) {
            findings += klass to "Feature requires one non-null immutable factory constructor argument without a default"
        }
        val factoryName = factories.singleOrNull()?.name
        val classBindings = setOfNotNull(factoryName).toMutableSet()
        fun bound(element: KtElement?, bindings: Set<String>): Boolean = when (element) {
            is KtNameReferenceExpression -> element.getReferencedName() in bindings
            is KtDotQualifiedExpression -> element.receiverExpression is KtThisExpression &&
                (element.selectorExpression as? KtNameReferenceExpression)?.getReferencedName() == factoryName
            else -> false
        }
        val processor = klass.declarations.filterIsInstance<KtNamedFunction>().singleOrNull { it.name == "processor" }
        if (processor == null) return findings + (klass to "Feature must declare its production processor")
        val functions = klass.containingKtFile.collectDescendantsOfType<KtNamedFunction>()
        var creations = 0
        var retainers = 0
        fun walk(element: KtElement?, retained: Boolean, stack: Set<KtNamedFunction>, bindings: Set<String>) {
            if (element == null || element is KtNamedFunction || element is KtLambdaExpression) return
            if (element is KtBlockExpression) {
                val localBindings = bindings.toMutableSet()
                element.statements.forEach { statement ->
                    walk(statement, retained, stack, localBindings)
                    if (statement is KtProperty) {
                        localBindings.remove(statement.name)
                        if (!statement.isVar && bound(statement.initializer, localBindings)) statement.name?.let(localBindings::add)
                    }
                }
                return
            }
            if (element is KtCallExpression) {
                val reference = element.calleeExpression as? KtNameReferenceExpression
                val called = reference?.getReferencedName().orEmpty()
                val local = functions.filter { function ->
                    function.name == called && function !== processor &&
                        (function.parent === klass.body || function.parent === klass.containingKtFile ||
                            generateSequence(element.parent) { it.parent }.any { it === function.parent }) &&
                        function.valueParameters.count { !it.hasDefaultValue() && !it.isVarArg } <= element.valueArguments.size &&
                        (element.valueArguments.size <= function.valueParameters.size || function.valueParameters.any { it.isVarArg })
                }
                val qualified = (element.parent as? KtDotQualifiedExpression)?.let(names::qualified)
                    ?: reference?.let(names::reference)
                val realRetainer = qualified == RETAINER && (element.parent is KtDotQualifiedExpression || local.isEmpty())
                if (realRetainer) {
                    retainers++
                    val lambdas = element.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression } +
                        element.lambdaArguments.mapNotNull { it.getLambdaExpression() }
                    if (lambdas.isEmpty()) findings += element to "Retained factory must have an inspectable creation callback"
                    lambdas.distinct().forEach { walk(it.bodyExpression, true, stack, bindings - it.valueParameters.mapNotNull { parameter -> parameter.name }) }
                    element.valueArguments.mapNotNull { it.getArgumentExpression() }
                        .filterNot { it is KtLambdaExpression }.forEach { walk(it, retained, stack, bindings) }
                    return
                }
                if (called.startsWith("create") && called.endsWith("Graph")) {
                    creations++
                    val receiver = (element.parent as? KtDotQualifiedExpression)?.receiverExpression
                    if (!bound(receiver, bindings)) findings += element to "Create the graph from the supplied factory"

                    if (!retained) findings += element to "Create the feature graph inside the retained processor callback"
                }
                if (local.size > 1) findings += element to "Ambiguous feature factory helper"
                local.forEach { helper ->
                    if (helper in stack) findings += element to "Recursive feature factory helper"
                    else {
                        val helperBindings = if (helper.parent === klass.body) classBindings.toMutableSet() else if (helper.parent is KtBlockExpression) bindings.toMutableSet() else mutableSetOf()
                        helper.valueParameters.forEachIndexed { index, parameter ->
                            helperBindings.remove(parameter.name)
                            val argument = element.valueArguments.firstOrNull {
                                it.getArgumentName()?.asName?.asString() == parameter.name
                            } ?: element.valueArguments.getOrNull(index)?.takeIf { it.getArgumentName() == null }
                            if (bound(argument?.getArgumentExpression(), bindings)) parameter.name?.let(helperBindings::add)
                        }
                        walk(helper.bodyExpression, retained, stack + helper, helperBindings)
                    }
                }
                element.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
                    .forEach { walk(it.bodyExpression, retained, stack, bindings - it.valueParameters.mapNotNull { parameter -> parameter.name }) }
            }
            element.children.filterIsInstance<KtElement>().forEach { walk(it, retained, stack, bindings) }
        }
        klass.declarations.forEach { declaration ->
            when (declaration) {
                is KtProperty -> {
                    walk(declaration.initializer, false, emptySet(), classBindings)
                    walk(declaration.delegateExpression, false, emptySet(), classBindings)
                    if (!declaration.isVar && bound(declaration.initializer, classBindings)) declaration.name?.let(classBindings::add)
                }
                is KtClassInitializer -> walk(declaration.body, false, emptySet(), classBindings)
                is KtSecondaryConstructor -> walk(declaration.bodyExpression, false, emptySet(), classBindings)
            }
        }
        walk(processor.bodyExpression, false, setOf(processor), classBindings)
        if (retainers != 1 || creations != 1) {
            findings += processor to "Feature processor must retain exactly one graph creation (retainers=$retainers, creations=$creations)"
        }
        return findings
    }
}
