package io.github.stslex.workeeper.lint_rules

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** Evaluates preview scaffolding only. Reviewed production subjects terminate traversal. */
internal class KmpPreviewContracts(files: List<KtFile>, private val subjects: Set<String>) {
    private val functions = files.flatMap { it.collectDescendantsOfType<KtNamedFunction>() }
    private val bySymbol = functions.groupBy { it.fqName?.asString() }
    private val rendered = mutableListOf<String>()
    private val errors = mutableListOf<String>()

    fun render(function: KtNamedFunction): List<String> {
        rendered.clear()
        errors.clear()
        walk(function.bodyExpression, emptyMap(), null, setOf(function))
        return (rendered + errors.map { "ERROR:$it" }).sorted()
    }

    private fun walk(
        element: KtElement?,
        environment: Map<String, Value>,
        theme: String?,
        stack: Set<KtNamedFunction>,
    ): Boolean {
        when (element) {
            is KtBlockExpression -> {
                val locals = environment.toMutableMap()
                for (statement in element.statements) {
                    if (statement is KtProperty) {
                        if (statement.isVar) errors += "mutable preview alias"
                        statement.name?.let { locals[it] = value(statement.initializer, locals) }
                    } else if (walk(statement, locals, theme, stack)) return true
                }
            }
            is KtReturnExpression -> {
                walk(element.returnedExpression, environment, theme, stack)
                return true
            }
            is KtParenthesizedExpression -> return walk(element.expression, environment, theme, stack)
            is KtCallExpression -> call(element, element, environment, theme, stack)
            is KtDotQualifiedExpression -> {
                val selector = element.selectorExpression as? KtCallExpression
                if (selector != null) call(selector, element, environment, theme, stack)
            }
            is KtNamedFunction, is KtLambdaExpression, is KtProperty, null -> Unit
            else -> errors += "unsupported preview control flow"
        }
        return false
    }

    private fun call(
        call: KtCallExpression,
        expression: KtElement,
        environment: Map<String, Value>,
        theme: String?,
        stack: Set<KtNamedFunction>,
    ) {
        val simple = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        val content = if (expression === call) environment[simple] as? Value.Content else null
        if (content != null) {
            walk(content.lambda.bodyExpression, content.environment, theme, stack)
            return
        }
        val receiver = (expression as? KtDotQualifiedExpression)?.receiverExpression as? KtNameReferenceExpression
        if (simple in environment || receiver?.getReferencedName() in environment) {
            errors += "unresolved shadowed callable"
            return
        }
        val symbol = symbol(expression)
        if (symbol in subjects) {
            rendered += "$symbol@${theme ?: "UNSCOPED"}"
            return
        }
        if (symbol == APP_THEME) {
            val arguments = call.valueArguments.filter { it !in call.lambdaArguments }
            val mode = arguments.firstOrNull { it.getArgumentName()?.asName?.asString() == "themeMode" }
                ?: arguments.firstOrNull { it.getArgumentName() == null }
            val effective = if (mode == null) "SYSTEM" else (value(mode.getArgumentExpression(), environment) as? Value.Theme)?.name
            if (effective == null) errors += "unresolved AppTheme mode"
            val body = call.lambdaArguments.lastOrNull()?.getLambdaExpression()?.let { Value.Content(it, environment) }
                ?: arguments.firstOrNull { it.getArgumentName()?.asName?.asString() == "content" }
                    ?.getArgumentExpression()?.let { value(it, environment) as? Value.Content }
                ?: arguments.getOrNull(1)?.getArgumentExpression()?.let { value(it, environment) as? Value.Content }
            if (body == null) errors += "missing AppTheme content"
            else walk(body.lambda.bodyExpression, body.environment, effective, stack)
            return
        }
        if (symbol in LAYOUTS) {
            val bodies = call.lambdaArguments.mapNotNull { it.getLambdaExpression() }
            bodies.forEach { walk(it.bodyExpression, environment, theme, stack) }
            call.valueArguments.filter { it.getArgumentName()?.asName?.asString() == "content" }
                .mapNotNull { value(it.getArgumentExpression(), environment) as? Value.Content }
                .forEach { walk(it.lambda.bodyExpression, it.environment, theme, stack) }
            return
        }
        val targets = bySymbol[symbol].orEmpty().filter { function ->
            val count = call.valueArguments.size
            function.valueParameters.count { !it.hasDefaultValue() && !it.isVarArg } <= count &&
                (count <= function.valueParameters.size || function.valueParameters.any { it.isVarArg })
        }
        if (targets.isEmpty()) {
            if (call.lambdaArguments.isNotEmpty()) errors += "unknown content owner $symbol"
            return
        }
        if (targets.size != 1) {
            errors += "ambiguous helper $symbol"
            return
        }
        val helper = targets.single()
        if (helper in stack) {
            errors += "recursive preview helper"
            return
        }
        if (!helper.hasModifier(KtTokens.PRIVATE_KEYWORD) && hasAnnotation(helper, COMPOSABLE)) {
            errors += "unreviewed production subject $symbol"
            return
        }
        val bound = helper.valueParameters.associate { it.name.orEmpty() to value(it.defaultValue, environment) }.toMutableMap()
        call.valueArguments.filter { it !in call.lambdaArguments }.forEachIndexed { position, argument ->
            val name = argument.getArgumentName()?.asName?.asString()
                ?: helper.valueParameters.getOrNull(position)?.name
            if (name != null) bound[name] = value(argument.getArgumentExpression(), environment)
        }
        call.lambdaArguments.lastOrNull()?.getLambdaExpression()?.let { lambda ->
            helper.valueParameters.lastOrNull()?.name?.let { bound[it] = Value.Content(lambda, environment) }
        }
        walk(helper.bodyExpression, bound, theme, stack + helper)
    }

    private fun value(element: KtElement?, environment: Map<String, Value>): Value = when (element) {
        is KtNameReferenceExpression -> environment[element.getReferencedName()] ?: Value.Unknown
        is KtParenthesizedExpression -> value(element.expression, environment)
        is KtLambdaExpression -> Value.Content(element, environment.toMap())
        is KtDotQualifiedExpression -> {
            val name = KotlinContractNames(element.containingKtFile).qualified(element)
            val mode = name.removePrefix("$THEME_MODE.")
            if (name.startsWith("$THEME_MODE.") && mode in setOf("LIGHT", "DARK", "SYSTEM")) Value.Theme(mode)
            else Value.Unknown
        }
        else -> Value.Unknown
    }

    private fun symbol(expression: KtElement): String {
        val file = expression.containingKtFile
        val name = KotlinContractNames(file).qualified(expression)
        val raw = when (expression) {
            is KtCallExpression -> (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
            else -> null
        }
        val scopes = generateSequence(expression.parent) { it.parent }.toList()
        val local = functions.filter { it.containingKtFile == file && it.name == raw }
            .filter { it.parent in scopes }.minByOrNull { scopes.indexOf(it.parent) }
        if (local != null) return local.fqName?.asString() ?: "<local:$raw>"
        val own = "${file.packageFqName}.$name"
        return if (own in bySymbol || own in subjects) own else name
    }

    private sealed interface Value {
        data class Theme(val name: String) : Value
        data class Content(val lambda: KtLambdaExpression, val environment: Map<String, Value>) : Value
        data object Unknown : Value
    }

    companion object {
        const val PREVIEW = "androidx.compose.ui.tooling.preview.Preview"
        const val COMPOSABLE = "androidx.compose.runtime.Composable"
        const val APP_THEME = "io.github.stslex.workeeper.core.ui.kit.theme.AppTheme"
        const val THEME_MODE = "io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode"
        private val LAYOUTS = setOf(
            "androidx.compose.foundation.layout.Box", "androidx.compose.foundation.layout.Column",
            "androidx.compose.foundation.layout.Row", "androidx.compose.foundation.layout.FlowRow",
            "androidx.compose.foundation.layout.FlowColumn", "androidx.compose.runtime.CompositionLocalProvider",
        )

        fun hasAnnotation(function: KtNamedFunction, symbol: String): Boolean = function.annotationEntries.any { annotation ->
            val names = KotlinContractNames(function.containingKtFile)
            val type = (annotation.typeReference?.typeElement as? org.jetbrains.kotlin.psi.KtUserType)
            type != null && names.type(type) == symbol
        }
    }
}
