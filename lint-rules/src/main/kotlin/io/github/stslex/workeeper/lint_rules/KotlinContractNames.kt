package io.github.stslex.workeeper.lint_rules

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** Import and typealias expansion over PSI. This is not compiler overload/type resolution. */
internal class KotlinContractNames(
    private val file: KtFile? = null,
    private val aliases: Map<String, KtTypeAlias> = file?.collectDescendantsOfType<KtTypeAlias>()
        .orEmpty().associateBy { it.fqName?.asString() ?: it.name.orEmpty() },
    private val knownTypes: Set<String> = emptySet(),
    private val externalSymbols: Set<String> = emptySet(),
) {
    private val scopedAliases = aliases + file?.collectDescendantsOfType<KtTypeAlias>()
        .orEmpty().associateBy { it.fqName?.asString() ?: it.name.orEmpty() }
    private val localTypes by lazy { file?.collectDescendantsOfType<KtClassOrObject>().orEmpty() }
    private val localDeclarations by lazy { file?.collectDescendantsOfType<KtNamedDeclaration>().orEmpty() }

    private val imports = file?.importDirectives.orEmpty().filterNot { it.isAllUnder }.mapNotNull { directive ->
        directive.importedFqName?.asString()?.let { (directive.aliasName ?: it.substringAfterLast('.')) to it }
    }.toMap()

    fun type(type: KtUserType): String = expand(type.path(), emptySet(), type)

    fun declaration(name: String): String = expand(name, emptySet())

    fun types(type: KtUserType, seen: Set<String> = emptySet()): Set<String> {
        val name = canonical(type.path(), type)
        val alias = scopedAliases[name]
        val nested = type.collectDescendantsOfType<KtUserType>()
            .filter { it !== type && it.parent !is KtUserType }.flatMap { types(it, seen) }
        val expanded = if (alias != null && name !in seen) {
            val context = KotlinContractNames(alias.containingKtFile, aliases, knownTypes, externalSymbols)
            alias.getTypeReference()?.collectDescendantsOfType<KtUserType>()
                .orEmpty().filter { it.parent !is KtUserType }.flatMap { context.types(it, seen + name) }
        } else emptyList()
        return (listOf(type(type)) + nested + expanded).toSet()
    }

    fun reference(
        reference: KtNameReferenceExpression,
        shadows: (KtNamedDeclaration) -> Boolean = { true },
    ): String = if (shadowed(reference, shadows)) {
        reference.getReferencedName()
    } else {
        expand(reference.getReferencedName(), emptySet(), reference)
    }

    fun declaredType(reference: KtNameReferenceExpression): String? {
        val ancestors = generateSequence(reference.parent) { it.parent }.toList()
        val declaration = visibleNamedDeclarations(reference, ancestors)
            .filterIsInstance<KtCallableDeclaration>().filterNot { it is KtNamedFunction }
            .minByOrNull { ancestors.indexOf(scopeOf(it)) }
        return declaration?.typeReference?.typeElement?.let { type ->
            val userType = if (type is KtNullableType) type.innerType else type
            (userType as? KtUserType)?.let(::type)
        }
    }

    fun qualified(element: KtElement): String {
        val root = generateSequence(element) {
            when (it) {
                is KtQualifiedExpression -> it.receiverExpression
                is KtCallExpression -> it.calleeExpression
                else -> null
            }
        }.last() as? KtNameReferenceExpression
        return if (root != null && shadowed(root)) referencePath(element) else expand(referencePath(element), emptySet(), element)
    }

    private fun shadowed(
        reference: KtNameReferenceExpression,
        shadows: (KtNamedDeclaration) -> Boolean = { true },
    ): Boolean {
        if (externalSymbols.isEmpty()) return false
        val ancestors = generateSequence(reference.parent) { it.parent }.toSet()
        return visibleNamedDeclarations(reference, ancestors).any(shadows)
    }

    private fun visibleNamedDeclarations(
        reference: KtNameReferenceExpression,
        ancestors: Collection<PsiElement>,
    ): Sequence<KtNamedDeclaration> = localDeclarations.asSequence().filter { declaration ->
        val scope = scopeOf(declaration)
        val visible = scope !is KtBlockExpression ||
            (if (declaration is KtNamedFunction) declaration.textRange.startOffset else declaration.textRange.endOffset) <=
            reference.textRange.startOffset
        declaration.name == reference.getReferencedName() && declaration !is KtClassOrObject &&
            declaration !is KtTypeAlias && scope in ancestors && visible
    }

    private fun scopeOf(declaration: KtNamedDeclaration) = if (declaration is KtParameter) {
        generateSequence(declaration.parent) { it.parent }.firstOrNull {
            it is KtFunctionLiteral || it is KtNamedFunction || it is KtClassOrObject
        }
    } else declaration.parent

    fun nullable(type: KtUserType, seen: Set<String> = emptySet()): Boolean {
        val name = canonical(type.path(), type)
        val alias = scopedAliases[name] ?: return false
        if (name in seen) return true
        val target = alias.getTypeReference()?.typeElement
        return target is KtNullableType || target is KtUserType &&
            KotlinContractNames(alias.containingKtFile, aliases, knownTypes, externalSymbols).nullable(target, seen + name)
    }

    private fun referencePath(element: KtElement): String = when (element) {
        is KtNameReferenceExpression -> element.getReferencedName()
        is KtQualifiedExpression -> referencePath(element.receiverExpression) + "." +
            (element.selectorExpression?.let(::referencePath) ?: "")
        is KtCallExpression -> element.calleeExpression?.let(::referencePath).orEmpty()
        else -> ""
    }

    private fun expand(name: String, seen: Set<String>, context: KtElement? = null): String {
        val canonical = canonical(name, context)
        val alias = scopedAliases[canonical]
        if (alias == null || canonical in seen) return canonical
        val element = alias.getTypeReference()?.typeElement
        val target = (element as? KtUserType) ?: (element as? KtNullableType)?.innerType as? KtUserType
        return if (target == null) canonical else KotlinContractNames(alias.containingKtFile, aliases, knownTypes, externalSymbols)
            .expand(target.path(), seen + canonical, target)
    }

    private fun canonical(name: String, context: KtElement? = null): String {
        val first = name.substringBefore('.')
        val ancestors = generateSequence(context?.parent) { it.parent }.toList()
        val localType = localTypes.filter { declaration ->
            declaration.name == first && (declaration.parent is KtFile || declaration.parent in ancestors) &&
                (declaration.parent !is KtBlockExpression || context == null ||
                    declaration.textRange.startOffset <= context.textRange.startOffset)
        }.minByOrNull { ancestors.indexOf(it.parent).takeIf { distance -> distance >= 0 } ?: Int.MAX_VALUE }
            ?.let { it.fqName?.asString() ?: "<local>:$first" }
        if (localType != null) return localType + name.removePrefix(first)
        val imported = imports[first]
        if (imported != null) return imported + name.removePrefix(first)
        val local = "${file?.packageFqName}.$first"
        if (local in scopedAliases || local in knownTypes || local in externalSymbols) return local + name.removePrefix(first)
        val stars = file?.importDirectives.orEmpty().filter { it.isAllUnder }.mapNotNull {
            it.importedFqName?.asString()?.let { prefix -> "$prefix.$first" }
        }.filter { it in externalSymbols }.distinct()
        return when (stars.size) {
            0 -> name
            1 -> stars.single() + name.removePrefix(first)
            else -> "<ambiguous>:${stars.joinToString()}"
        }
    }

    private fun KtUserType.path(): String = generateSequence(this) { it.qualifier }
        .mapNotNull { it.referencedName }.toList().asReversed().joinToString(".")
}
