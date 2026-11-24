package io.github.stslex.workeeper.lint_rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.io.File

/** Evaluated compilation inputs scope these PSI checks; see kmp-verification-ownership.md. */
class KmpSharedContractsRule(config: Config = Config.empty) : Rule(config, RequiredFindings()) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Shared source APIs, payloads and State updates must retain portable ownership boundaries.",
        Debt.TWENTY_MINS,
    )

    private val common by lazy { configured("commonProductionFiles") }
    private val native by lazy { configured("nativeProductionFiles") }
    private val sharedTests by lazy { configured("sharedTestFiles") }
    private val databaseReaders by lazy {
        KmpDatabaseReaders(valueOrDefault("modulePath", ""), configured("allFiles"), configured("databaseSymbolFiles"))
    }
    private var names = KotlinContractNames()
    private var aliasIndex: Map<String, KtTypeAlias>? = null
    private var classIndex = emptyMap<String, KtClass>()
    private var mapperTypes = emptySet<String>()
    private var pureMutators = PURE_MUTATORS
    private var knownTypes = emptySet<String>()
    private var shared = false
    private var portable = false
    private var feature = false
    private var testing = false
    private var resolvingHelpers = emptySet<KtNamedFunction>()
    private var functions = emptyList<KtNamedFunction>()
    private var declarations = emptyList<KtNamedDeclaration>()

    override fun visitCondition(root: KtFile): Boolean = active

    override fun visitKtFile(file: KtFile) {
        databaseReaders.verify(file).forEach { (element, diagnostic) -> finding(element, diagnostic) }
        val path = File(file.virtualFilePath).absoluteFile.normalize().path
        portable = path in common
        shared = portable || path in native
        testing = path in sharedTests
        if (!shared && !testing) return
        if (aliasIndex == null) {
            val factory = KtPsiFactory(file.project)
            val sources = (common + path).map { source ->
                if (source == path) file else factory.createFile(source, File(source).readText())
            }
            aliasIndex = sources.flatMap { it.collectDescendantsOfType<KtTypeAlias>() }
                .associateBy { it.fqName?.asString() ?: it.name.orEmpty() }
            classIndex = sources.flatMap { it.collectDescendantsOfType<KtClass>() }
                .mapNotNull { klass -> klass.fqName?.asString()?.let { it to klass } }.toMap()
            mapperTypes = sources.filter { ".mapper." in "${it.packageFqName}." }
                .flatMap { it.collectDescendantsOfType<KtClassOrObject>() }
                .mapNotNull { it.fqName?.asString() }.toSet()
            knownTypes = sources.flatMap { it.collectDescendantsOfType<KtClassOrObject>() }
                .mapNotNull { it.fqName?.asString() }.toSet()
            val mutatorNames = KotlinContractNames(aliases = checkNotNull(aliasIndex), knownTypes = knownTypes)
            pureMutators = PURE_MUTATORS.map(mutatorNames::declaration).toSet()
            if (valueOrDefault("requirePureMutatorInventory", false)) {
                PURE_MUTATORS.filter { mutatorNames.declaration(it) !in knownTypes }.forEach {
                    finding(file, "Missing reviewed State mutator: $it")
                }
            }
        }
        names = KotlinContractNames(file, checkNotNull(aliasIndex), knownTypes)
        feature = file.packageFqName.asString().startsWith(FEATURE_PACKAGE)
        declarations = file.collectDescendantsOfType<KtNamedDeclaration>()
        functions = file.collectDescendantsOfType<KtNamedFunction>()
        val helpers = functions.filter { it.bodyExpression?.let(::hasResourceWork) == true }.toMutableSet()
        while (true) {
            val before = helpers.size
            functions.filterTo(helpers) { function -> function.bodyExpression?.collectDescendantsOfType<KtCallExpression>()
                ?.any { call -> localTargets(call).any { it in helpers } } == true }
            if (before == helpers.size) break
        }
        resolvingHelpers = helpers
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        checkReference(importDirective, importDirective.importedFqName?.asString().orEmpty())
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.parent is KtDotQualifiedExpression ||
            expression.getStrictParentOfType<KtImportDirective>() != null
        ) return
        checkReference(expression, names.qualified(expression))
    }

    override fun visitUserType(type: KtUserType) {
        super.visitUserType(type)
        if (type.parent is KtUserType) return
        val name = names.type(type)
        names.types(type).forEach { checkReference(type, it) }
        if (portable && isPayload(type) && names.types(type).any { forbiddenPayload(type, it) }) {
            finding(type, "Payload contains a resource or platform handle: $name")
        }
        if (portable && inState(type) && names.types(type).any { it.substringAfterLast('.') in MUTABLE_TYPES }) {
            finding(type, "State contains a mutable collection: $name")
        }
        if (portable && feature && name.substringAfterLast('.') == "ResourceWrapper") {
            val owner = type.contractOwner()
            if (owner !in WRAPPER_CARRIERS) finding(type, "ResourceWrapper is restricted to reviewed date readers")
        }
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        if (expression !is KtNameReferenceExpression) return
        if (expression.getStrictParentOfType<KtImportDirective>() != null) return
        val name = names.reference(expression)
        checkReference(expression, name)
        if (portable && isPayload(expression) && forbiddenPayload(expression, name)) {
            finding(expression, "Payload resolves a resource or platform handle: $name")
        }
    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (portable) verifySettings(klass)
        if (portable && feature) KmpFactoryOwnership.verify(klass, names).forEach { (element, diagnostic) ->
            finding(element, diagnostic)
        }
        if (!portable || !isState(klass) || klass.isInterface()) return
        if (!klass.isData() && !klass.hasModifier(KtTokens.SEALED_KEYWORD)) {
            finding(klass, "State must be an immutable data or sealed class")
        }
        klass.primaryConstructorParameters.filter { it.isMutable }.forEach {
            finding(it, "State constructor property must be val")
        }
        klass.getProperties().filter { it.isVar }.forEach { finding(it, "State property must be val") }
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        if (!portable || typeAlias.fqName?.asString() !in AUTH_MARKERS) return
        val target = (typeAlias.getTypeReference()?.typeElement as? KtUserType)?.let { classIndex[names.type(it)] }
        if (target == null || !opaqueMarker(target) || typeAlias.typeParameters.isNotEmpty() ||
            NON_PUBLIC.any(typeAlias::hasModifier)
        ) finding(typeAlias, "Settings authentication carriers must remain public opaque markers")
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!portable || !feature) return
        val qualified = (expression.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression === expression }
        val callee = expression.calleeExpression as? KtNameReferenceExpression
        val copyName = if (qualified != null) names.qualified(qualified) else callee?.let(names::reference).orEmpty()
        checkReference(expression, copyName)
        if (qualified == null && localTargets(expression).isEmpty() && resolverReceiverType(expression) == null &&
            expression.containingKtFile.importDirectives.any {
                it.isAllUnder && it.importedFqName?.asString() == "org.jetbrains.compose.resources"
            } && "org.jetbrains.compose.resources.$copyName" in CMP_SUSPENDED_COPY + CMP_COMPOSED_COPY
        ) checkReference(expression, "org.jetbrains.compose.resources.$copyName")
        if (expression.calledName() in STATE_UPDATES) {
            val lambdas = expression.valueArguments.mapNotNull { it.getArgumentExpression()?.unwrap() as? KtLambdaExpression } +
                expression.lambdaArguments.mapNotNull { it.getLambdaExpression() }
            lambdas.distinct().forEach { lambda ->
                if (hasResourceWork(lambda) || lambda.collectDescendantsOfType<KtCallExpression>()
                    .any { call -> localTargets(call).any { it in resolvingHelpers } }) {
                    finding(lambda, "Resolve resources and dates before the State update, including helper calls")
                }
            }
        }
        val receiverType = resolverReceiverType(expression)
        val packageName = expression.containingKtFile.packageFqName.asString()
        if (".ui." in "$packageName." && ".mvi." !in "$packageName." &&
            receiverType == "StringResolver" &&
            expression.calledName() in setOf("getString", "getPluralString")
        ) {
            finding(expression, "Feature UI reads copy in composition; resolve suspended copy before the UI boundary")
        }
        if (receiverType == "ResourceWrapper") {
            val method = expression.calledName()
            if (method in WRAPPER_COPY || method in DATE_METHODS && method !in DATE_READERS[expression.contractOwner()].orEmpty()) {
                finding(expression, "ResourceWrapper method is outside its reviewed date reader: $method")
            }
        }
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        if (!portable || classOrObject.fqName?.asString() !in pureMutators) return
        val references = classOrObject.collectDescendantsOfType<KtNameReferenceExpression>().map(names::reference) +
            classOrObject.collectDescendantsOfType<KtDotQualifiedExpression>().map(names::qualified)
        val otherMapper = references.any { name ->
            val candidates = listOf(name, "${classOrObject.containingKtFile.packageFqName}.$name")
            mapperTypes.filter { type -> pureMutators.none { type == it || type.startsWith("$it.") } }
                .any { type -> candidates.any { it == type || it.startsWith("$type.") } }
        }
        if (otherMapper || hasResourceWork(classOrObject) || classOrObject.collectDescendantsOfType<KtUserType>().any {
                names.types(it).any { name -> name.substringAfterLast('.') in RESOLVERS }
            }) {
            finding(classOrObject, "State mutators must not resolve resources or dates")
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!portable || function.name != "initial") return
        if (generateSequence(function.parent) { it.parent }.filterIsInstance<KtClass>()
                .none { it.fqName?.asString() == SETTINGS_STATE }
        ) return
        verifyAvailability(function, function.valueParameters)
    }

    private fun verifySettings(klass: KtClass) {
        when (klass.fqName?.asString()) {
            SETTINGS_STATE -> verifyAvailability(klass, klass.primaryConstructorParameters)
            in AUTH_MARKERS -> if (!opaqueMarker(klass)) {
                finding(klass, "Settings authentication carriers must remain public opaque markers")
            }
        }
    }

    private fun opaqueMarker(klass: KtClass): Boolean = klass.isInterface() && klass.declarations.isEmpty() &&
        klass.superTypeListEntries.isEmpty() && klass.typeParameters.isEmpty() && NON_PUBLIC.none(klass::hasModifier)

    private fun resolverReceiverType(call: KtCallExpression): String? {
        val qualified = (call.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression === call }
        val explicit = qualified?.receiverExpression?.unwrap()
        fun typeOf(element: KtElement?): String? {
            val reference = (element as? KtNameReferenceExpression) ?: (element as? KtQualifiedExpression)
                ?.selectorExpression as? KtNameReferenceExpression
            return reference?.let(::declaredType)
        }
        if (explicit != null && explicit !is KtThisExpression) return typeOf(explicit)
        for (lambda in generateSequence(call.parent) { it.parent }.filterIsInstance<KtLambdaExpression>()) {
            val scope = lambda.getStrictParentOfType<KtCallExpression>() ?: continue
            val receiver = (scope.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression === scope }
                ?.receiverExpression
            when (scope.calledName()) {
                "with" -> typeOf(scope.valueArguments.firstOrNull()?.getArgumentExpression())?.let { return it }
                "run", "apply" -> typeOf(receiver)?.let { return it }
            }
        }
        val extension = call.getStrictParentOfType<KtNamedFunction>()?.receiverTypeReference
        return extension?.collectDescendantsOfType<KtUserType>()?.flatMap { names.types(it) }
            ?.map { it.substringAfterLast('.') }?.firstOrNull { it in RESOLVERS }
    }

    private fun verifyAvailability(owner: KtElement, parameters: List<KtParameter>) {
        val availability = parameters.singleOrNull { it.name == "backupAvailable" }
        if (availability == null || availability.hasDefaultValue()) {
            finding(availability ?: owner, "Settings backupAvailable must remain an explicit required argument")
        }
    }

    private fun hasResourceWork(element: KtElement): Boolean =
        element.collectDescendantsOfType<KtNameReferenceExpression>().any {
            (it.parent !is KtCallExpression && declaredType(it) in RESOLVERS) ||
                names.reference(it).substringAfterLast('.') in RESOURCE_WORK
        }

    private fun KtElement.unwrap(): KtElement =
        if (this is KtParenthesizedExpression) expression?.unwrap() ?: this else this

    private fun localTargets(call: KtCallExpression): List<KtNamedFunction> {
        val owner = call.getStrictParentOfType<KtClassOrObject>()
        val count = call.valueArguments.size
        val ancestors = generateSequence(call.parent) { it.parent }.toSet()
        val candidates = functions.filter { function ->
            (function.parent !is KtBlockExpression || function.parent in ancestors) &&
                function.name == call.calledName() && function.valueParameters.count { !it.hasDefaultValue() && !it.isVarArg } <= count &&
                (count <= function.valueParameters.size || function.valueParameters.any { it.isVarArg })
        }
        val member = candidates.filter { it.getStrictParentOfType<KtClassOrObject>() == owner }
        return member.ifEmpty { candidates.filter { it.getStrictParentOfType<KtClassOrObject>() == null } }
    }

    private fun declaredType(reference: KtNameReferenceExpression, seen: Set<KtNamedDeclaration> = emptySet()): String? {
        val ancestors = generateSequence(reference.parent) { it.parent }.toList()
        val declaration = declarations.filter { it.name == reference.getReferencedName() && it !in seen }
            .mapNotNull { declaration ->
                val scope = when (declaration) {
                    is KtParameter -> generateSequence(declaration.parent) { it.parent }.firstOrNull {
                        it is KtNamedFunction || it is KtClassOrObject || it is KtFunctionLiteral
                    }
                    is KtProperty -> declaration.parent
                    else -> null
                }
                val distance = ancestors.indexOf(scope)
                if (distance < 0 || scope is KtBlockExpression && declaration.textOffset > reference.textOffset) null
                else declaration to distance
            }.minByOrNull { it.second }?.first ?: return null
        val type = when (declaration) {
            is KtParameter -> declaration.typeReference
            is KtProperty -> declaration.typeReference
            else -> null
        }
        val direct = type?.collectDescendantsOfType<KtUserType>()?.flatMap { names.types(it) }
            ?.firstOrNull { it.substringAfterLast('.') in RESOLVERS }
        if (direct != null) return direct.substringAfterLast('.')
        if (type != null) return null
        val initializer = (declaration as? KtProperty)?.initializer as? KtNameReferenceExpression
        return initializer?.let { declaredType(it, seen + declaration) }
    }

    private fun checkReference(element: KtElement, name: String) {
        val forbidden = when {
            shared && PLATFORM_PACKAGES.any { name == it.removeSuffix(".") || name.startsWith(it) } -> true
            portable && name.startsWith("platform.") -> true
            testing && TEST_PACKAGES.any { name == it.removeSuffix(".") || name.startsWith(it) } -> true
            testing && name in DISABLED_TEST_ANNOTATIONS -> true
            portable && feature && name.substringAfterLast('.') in FEATURE_LOOKUPS -> true
            else -> false
        }
        if (forbidden) finding(element, "Shared source references a forbidden API: $name")
        if (!portable || !feature || element is KtImportDirective) return
        val pkg = element.containingKtFile.packageFqName.asString()
        val suspendedCopy = name in CMP_SUSPENDED_COPY
        val composedCopy = name in CMP_COMPOSED_COPY
        if ((suspendedCopy && element.contractOwner() !in LEGACY_CMP_READERS) ||
            composedCopy && ".mvi." in "$pkg."
        ) {
            finding(element, "Read non-composition feature copy through the injected StringResolver")
        }
    }

    private fun isPayload(element: KtElement): Boolean {
        val pkg = element.containingKtFile.packageFqName.asString()
        return feature && (".domain." in "$pkg." || ".model." in "$pkg." || inState(element) ||
            generateSequence(element.parent) { it.parent }.filterIsInstance<KtClassOrObject>().any {
                it.name?.endsWith("Store") == true || it.name in setOf("DialogState", "ErrorType")
            })
    }

    private fun inState(element: KtElement): Boolean =
        generateSequence(element.parent) { it.parent }.filterIsInstance<KtClass>().any(::isState)

    private fun isState(klass: KtClass): Boolean = klass.name?.endsWith("State") == true &&
        klass.containingKtFile.packageFqName.pathSegments().any { it.asString() == "mvi" }

    private fun forbiddenPayload(element: KtElement, name: String): Boolean {
        val copyEnum = generateSequence(element.parent) { it.parent }.filterIsInstance<KtClassOrObject>()
            .any { it.fqName?.asString() == "${FEATURE_PACKAGE}settings.mvi.model.BackupErrorUi" }
        if (copyEnum &&
            (name == "org.jetbrains.compose.resources.StringResource" ||
                name.startsWith("${FEATURE_PACKAGE}settings.resources."))
        ) return false
        return name.substringAfterLast('.') in PAYLOAD_TYPES ||
            name.startsWith("org.jetbrains.compose.resources.") || ".resources." in name
    }

    private fun KtElement.contractOwner(): String? =
        getStrictParentOfType<KtClassOrObject>()?.fqName?.asString()
            ?: getStrictParentOfType<KtNamedFunction>()?.fqName?.asString()

    private fun configured(key: String) = valueOrDefault(key, emptyList<String>()).map {
        val file = File(it)
        if (file.isAbsolute) file.path else File(valueOrDefault("moduleDirectory", "."), it).absoluteFile.normalize().path
    }.toSet()
    private fun KtCallExpression.calledName() = (calleeExpression as? KtNameReferenceExpression)?.let(names::reference)?.substringAfterLast('.')
    private fun finding(element: KtElement, message: String) = report(CodeSmell(issue, Entity.from(element), message))

    private companion object {
        val NON_PUBLIC = listOf(KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD, KtTokens.PROTECTED_KEYWORD)
        const val FEATURE_PACKAGE = "io.github.stslex.workeeper.feature."
        const val SETTINGS_STATE = "${FEATURE_PACKAGE}settings.mvi.store.SettingsStore.State"
        val AUTH_MARKERS = setOf("${FEATURE_PACKAGE}settings.mvi.model.SettingsAuthRequest",
            "${FEATURE_PACKAGE}settings.mvi.model.SettingsAuthResult")
        val CMP_SUSPENDED_COPY = setOf("org.jetbrains.compose.resources.getString",
            "org.jetbrains.compose.resources.getPluralString")
        val CMP_COMPOSED_COPY = setOf("org.jetbrains.compose.resources.stringResource",
            "org.jetbrains.compose.resources.pluralStringResource")
        val LEGACY_CMP_READERS = setOf(
            "${FEATURE_PACKAGE}archive.mvi.handler.ArchivePagingHandler",
            "${FEATURE_PACKAGE}archive.ui.archiveGraph",
            "${FEATURE_PACKAGE}all_trainings.mvi.mapper.TrainingListItemMapper",
            "${FEATURE_PACKAGE}all_trainings.ui.bulkArchiveMessage",
        )
        val PLATFORM_PACKAGES = listOf("android.", "androidx.activity.", "androidx.annotation.",
            "androidx.compose.ui.res.", "java.", "javax.", "com.google.firebase.")
        val TEST_PACKAGES = listOf("org.junit.", "io.mockk.")
        val DISABLED_TEST_ANNOTATIONS = setOf("kotlin.test.Ignore", "Ignore", "Disabled")
        val FEATURE_LOOKUPS = setOf("LocalContext", "appDeps", "VisibleForTesting", "CompositionLocal",
            "compositionLocalOf", "staticCompositionLocalOf", "ServiceLocator", "FactoryRegistry")
        val PAYLOAD_TYPES = setOf("StringResource", "PluralStringResource", "DrawableResource", "StringRes",
            "PluralsRes", "ResourceWrapper", "StringResolver", "Context", "Intent", "Uri", "Bitmap", "Res")
        val MUTABLE_TYPES = setOf("MutableList", "MutableSet", "MutableMap", "ArrayList", "HashMap", "HashSet",
            "Array", "IntArray", "LongArray", "ByteArray", "SnapshotStateList", "SnapshotStateMap", "MutableState")
        val RESOLVERS = setOf("StringResolver", "ResourceWrapper")
        val RESOURCE_WORK = RESOLVERS + setOf("Res", "getString", "getPluralString", "getQuantityString",
            "stringResource", "pluralStringResource", "formatMediumDate", "formatDayMonth", "getAbbreviatedRelativeTime")
        val STATE_UPDATES = setOf("updateState", "updateStateImmediate")
        val WRAPPER_COPY = setOf("getString", "getQuantityString")
        val DATE_READERS = mapOf(
            "${FEATURE_PACKAGE}archive.mvi.handler.ArchivePagingHandler" to setOf("formatDayMonth"),
            "${FEATURE_PACKAGE}past_session.mvi.mapper.PastSessionUiMapper" to setOf("formatMediumDate"),
            "${FEATURE_PACKAGE}home.mvi.mapper.HomeUiMapper" to setOf("getAbbreviatedRelativeTime"),
            "${FEATURE_PACKAGE}exercise_chart.mvi.mapper.ChartReadoutMapper" to setOf("formatDayMonth"),
            "${FEATURE_PACKAGE}single_training.mvi.handler.CommonHandler" to setOf("formatMediumDate"),
            "${FEATURE_PACKAGE}exercise.ui.mvi.mapper.ExerciseUiMapper" to setOf("formatDayMonth", "formatMediumDate"),
            "${FEATURE_PACKAGE}app_dialogs.impl.mvi.mapper.toDateLabel" to setOf("formatMediumDate"),
        )
        val DATE_METHODS = DATE_READERS.values.flatten().toSet()
        val WRAPPER_CARRIERS = DATE_READERS.keys + setOf(
            "${FEATURE_PACKAGE}past_session.mvi.handler.CommonHandler",
            "${FEATURE_PACKAGE}home.mvi.handler.ClickHandler",
            "${FEATURE_PACKAGE}home.mvi.handler.PagingHandler",
            "${FEATURE_PACKAGE}exercise_chart.mvi.handler.ClickHandler",
            "${FEATURE_PACKAGE}exercise_chart.mvi.handler.CommonHandler",
            "${FEATURE_PACKAGE}exercise.ui.mvi.handler.CommonHandler",
            "${FEATURE_PACKAGE}app_dialogs.impl.mvi.handler.AppDialogRepoHandler",
        )
        val PURE_MUTATORS = setOf(
            "${FEATURE_PACKAGE}live_workout.mvi.mapper.LiveSetMutator",
            "${FEATURE_PACKAGE}live_workout.mvi.mapper.StateStatusMapper",
            "${FEATURE_PACKAGE}live_workout.mvi.mapper.LiveWorkoutMapper",
            "${FEATURE_PACKAGE}live_workout.mvi.mapper.LiveSetRowsResolver",
            "${FEATURE_PACKAGE}live_workout.domain.mapper.LiveWorkoutDomainMapper",
            "${FEATURE_PACKAGE}live_workout.mvi.mapper.ExerciseDoneRule",
        )
    }
}
