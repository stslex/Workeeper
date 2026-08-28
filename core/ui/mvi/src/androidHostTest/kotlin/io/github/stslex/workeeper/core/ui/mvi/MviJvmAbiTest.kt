// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScopeImpl
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.TypeVariable
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

/**
 * Pins this module's JVM interface ABI to the shape measured before the KMP conversion, when the
 * classic Compose convention supplied `-Xjvm-default=all`.
 *
 * Two independent mechanisms are involved and they are NOT the same thing:
 *
 * * **default interface bodies** — none of these interfaces declares one, so no member is a Java
 *   `default` method and no `DefaultImpls` holder is emitted for that reason;
 * * **default-argument helpers** — `HandlerStore` and `StoreConsumer` DO declare default argument
 *   values, and under `-Xjvm-default=all` / [org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode]
 *   `NO_COMPATIBILITY` their synthetic `name$default` bridges are static members of the interface
 *   itself. Under `ENABLE` the very same helpers move into a `DefaultImpls` compatibility class,
 *   which changes the binary interface every consumer compiles against.
 *
 * Flipping this module's `jvmDefault` to `ENABLE` therefore reddens this test, which is the
 * point: the setting lives in `core/ui/mvi/build.gradle.kts` and nothing else guards it.
 */
internal class MviJvmAbiTest {

    @Test
    fun noMviInterfaceCarriesADefaultImplsHolder() {
        ABI_PINNED_INTERFACES.forEach { type ->
            val holder = "${type.name}\$DefaultImpls"
            val found = runCatching { Class.forName(holder, false, type.classLoader) }.isSuccess
            assertFalse(
                found,
                "$holder must not exist: it appears when this module's jvmDefault mode is " +
                    "ENABLE instead of NO_COMPATIBILITY, moving the \$default helpers off the " +
                    "interface and changing the ABI all 15 consumers compile against",
            )
        }
    }

    @Test
    fun defaultArgumentHelpersStayStaticMembersOfTheInterface() {
        EXPECTED_DEFAULT_HELPERS.forEach { (type, expectedCount) ->
            val helpers = type.declaredMethods.filter { it.name.endsWith(DEFAULT_HELPER_SUFFIX) }
            assertEquals(
                expectedCount,
                helpers.size,
                "${type.simpleName} must declare exactly $expectedCount \$default helper(s) on " +
                    "the interface itself; found ${helpers.map(Method::getName).sorted()}",
            )
            helpers.forEach { helper ->
                assertTrue(
                    Modifier.isStatic(helper.modifiers),
                    "${type.simpleName}.${helper.name} must be static",
                )
            }
        }
    }

    @Test
    fun publicMviAbiMatchesTheMeasuredManifest() {
        val actual = publicJvmAbiManifest(BaseStore::class.java)
        val expected = requireNotNull(
            javaClass.getResourceAsStream(ABI_MANIFEST_RESOURCE),
        ) {
            "$ABI_MANIFEST_RESOURCE is the checked-in pre-conversion ABI plus the explicit " +
                "Phase 7.3 allowlist"
        }.bufferedReader().use { it.readText() }

        assertEquals(
            expected.trimEnd(),
            actual.trimEnd(),
            "The complete public JVM ABI changed. Regenerate only from the measured " +
                "pre-conversion output and apply only an approved Phase 7.3 delta.",
        )
    }

    @Test
    fun noMviInterfaceMemberIsAJavaDefaultMethod() {
        ABI_PINNED_INTERFACES.forEach { type ->
            val defaults = type.declaredMethods.filter(Method::isDefault).map(Method::getName)
            assertTrue(
                defaults.isEmpty(),
                "${type.simpleName} declares no member with a body, so none may compile to a " +
                    "Java default method; found $defaults",
            )
        }
    }

    /**
     * The approved public deltas, asserted positively so the list cannot quietly grow:
     * `BaseStore` now requires an `AppScopeLifetime` and its `init` no longer accepts a Job.
     */
    @Test
    fun lifetimeConstructorDeltasAreRequiredAndHaveNoDefaults() {
        val init = BaseStore::class.java.declaredMethods.single { it.name == "init" }
        assertEquals(
            listOf("androidx.lifecycle.LifecycleOwner"),
            init.parameterTypes.map { it.name },
            "init must take only the LifecycleOwner; the generation now arrives by construction",
        )

        val constructor = BaseStore::class.java.declaredConstructors
            .single { it.parameterTypes.none { p -> p.name.endsWith("DefaultConstructorMarker") } }
        assertTrue(
            constructor.parameterTypes.any {
                it.name == "io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime"
            },
            "BaseStore's primary constructor must require an AppScopeLifetime; found " +
                constructor.parameterTypes.map { it.name },
        )

        val scopeConstructor = AppCoroutineScopeImpl::class.java.declaredConstructors
            .single {
                it.parameterTypes.none { parameter ->
                    parameter.name.endsWith("DefaultConstructorMarker")
                }
            }
        assertEquals(
            "kotlinx.coroutines.Job",
            scopeConstructor.parameterTypes.last().name,
            "AppCoroutineScopeImpl must require the non-null generation Job as its last argument",
        )
        assertFalse(
            AppCoroutineScopeImpl::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { parameter ->
                    parameter.name.endsWith("DefaultConstructorMarker")
                }
            },
            "AppCoroutineScopeImpl must expose no synthetic default constructor",
        )
    }

    /** `StoreGenerationDeps` is deleted, and nothing may resurrect it under the old name. */
    @Test
    fun theDeletedGenerationDepsInterfaceIsAbsent() {
        val loaded = runCatching {
            Class.forName(
                "io.github.stslex.workeeper.core.ui.mvi.di.StoreGenerationDeps",
                false,
                BaseStore::class.java.classLoader,
            )
        }.isSuccess
        assertFalse(
            loaded,
            "StoreGenerationDeps was removed with the Context-backed lifetime lookup",
        )
    }

    private companion object {

        const val DEFAULT_HELPER_SUFFIX = "\$default"

        val ABI_PINNED_INTERFACES = listOf(
            Store::class.java,
            StoreConsumer::class.java,
            HandlerStore::class.java,
            HandlerStoreEmitter::class.java,
            StoreProcessor::class.java,
        )

        /** Measured on the pre-conversion classic build: three helpers on each of the two. */
        val EXPECTED_DEFAULT_HELPERS = mapOf(
            HandlerStore::class.java to 3,
            StoreConsumer::class.java to 3,
        )

        const val ABI_MANIFEST_RESOURCE = "/mvi-public-jvm-abi.txt"
        const val MVI_PACKAGE_PREFIX = "io.github.stslex.workeeper.core.ui.mvi."
        const val GENERATED_RESOURCE_PREFIX = "workeeper.core.ui.mvi.generated.resources."

        /**
         * The sole package exclusion is generated by the Compose resources compiler. This module
         * owns no Compose resources, and these empty accessors are build artifacts rather than MVI
         * declarations. ACC_SYNTHETIC classes and local/anonymous classes carrying EnclosingMethod
         * metadata are compiler implementation artifacts. Every remaining public class in the
         * compiled module is manifested, including named nested classes and Kotlin file facades.
         */
        val EXCLUDED_CLASS_PREFIXES = mapOf(
            GENERATED_RESOURCE_PREFIX to "empty Compose resource compiler accessors",
        )
        val EXCLUDED_CLASS_NAMES = mapOf(
            "${MVI_PACKAGE_PREFIX}BuildConfig" to "AGP-generated build constant holder",
        )

        private fun publicJvmAbiManifest(anchor: Class<*>): String {
            val classLoader = anchor.classLoader
            val classNames = compiledClassNames(anchor)
            val publicClasses = classNames
                .asSequence()
                .filterNot { name -> name in EXCLUDED_CLASS_NAMES }
                .filter { name -> EXCLUDED_CLASS_PREFIXES.keys.none(name::startsWith) }
                .map { name -> Class.forName(name, false, classLoader) }
                .filter { type ->
                    Modifier.isPublic(type.modifiers) &&
                        !type.isSynthetic &&
                        type.enclosingMethod == null &&
                        type.enclosingConstructor == null
                }
                .sortedBy(Class<*>::getName)
                .toList()

            check(publicClasses.isNotEmpty()) { "No public classes found beside ${anchor.name}" }
            check(publicClasses.any { it.name == "${MVI_PACKAGE_PREFIX}NavResults" }) {
                "The compiled-output scan did not cover NavResults"
            }

            return buildString {
                appendLine("# core:ui:mvi complete public JVM ABI")
                appendLine("# source: measured pre-conversion output plus Phase 7.3 allowlist")
                appendLine("# Phase 7.3 allowlist:")
                appendLine("# - BaseStore constructor adds AppScopeLifetime; init removes Job")
                appendLine("# - StoreGenerationDeps is deleted")
                appendLine("# - internal expect/actual performance backend and screen-recorder JVM seams")
                appendLine("# - PerformanceMetricsRecorder.process delegates synchronization to its backend")
                appendLine("# - rememberStoreProcessor is non-inline and therefore not ACC_SYNTHETIC")
                appendLine("# excluded class rules:")
                appendLine("# - ACC_SYNTHETIC classes: compiler implementation artifacts")
                appendLine("# - EnclosingMethod/EnclosingConstructor classes: local implementation artifacts")
                EXCLUDED_CLASS_NAMES.toSortedMap().forEach { (name, reason) ->
                    appendLine("# - $name : $reason")
                }
                EXCLUDED_CLASS_PREFIXES.toSortedMap().forEach { (prefix, reason) ->
                    appendLine("# - $prefix* : $reason")
                }
                publicClasses.forEach { type ->
                    appendClass(type)
                }
            }
        }

        private fun compiledClassNames(anchor: Class<*>): List<String> {
            val location = requireNotNull(anchor.protectionDomain?.codeSource?.location) {
                "${anchor.name} has no compiled-output location"
            }
            val uri = location.toURI()
            return when {
                uri.scheme == "file" && Files.isDirectory(Paths.get(uri)) -> {
                    val root = Paths.get(uri)
                    Files.walk(root).use { paths ->
                        paths
                            .filter { path -> Files.isRegularFile(path) }
                            .map { path -> root.relativize(path).toString() }
                            .filter { relative -> relative.endsWith(".class") }
                            .map(::classNameFromPath)
                            .sorted()
                            .toList()
                    }
                }

                uri.scheme == "file" -> JarFile(Paths.get(uri).toFile()).use { jar ->
                    jar.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".class") }
                        .map(::classNameFromPath)
                        .sorted()
                        .toList()
                }

                uri.scheme == "jar" -> {
                    val connection = location.openConnection() as JarURLConnection
                    connection.jarFile.use { jar ->
                        jar.entries().asSequence()
                            .map { it.name }
                            .filter { it.endsWith(".class") }
                            .map(::classNameFromPath)
                            .sorted()
                            .toList()
                    }
                }

                else -> error("Unsupported compiled-output location: $location")
            }
        }

        private fun classNameFromPath(path: Path): String = classNameFromPath(path.toString())

        private fun classNameFromPath(path: String): String = path
            .removeSuffix(".class")
            .replace('/', '.')
            .replace('\\', '.')

        private fun StringBuilder.appendClass(type: Class<*>) {
            appendLine()
            append("CLASS ")
            append(type.name)
            append(" flags=")
            append(type.modifiers.hexFlags())
            append(" typeParameters=")
            append(type.typeParameters.renderTypeParameters())
            append(" superclass=")
            append(type.genericSuperclass?.typeName ?: "-")
            append(" interfaces=")
            append(type.genericInterfaces.map { it.typeName }.sorted())
            appendLine()

            type.declaredConstructors
                .filter { Modifier.isPublic(it.modifiers) }
                .sortedBy { constructor -> constructor.descriptor() }
                .forEach { constructor ->
                    append("  CONSTRUCTOR ")
                    append(constructor.descriptor())
                    append(" flags=")
                    append(constructor.modifiers.hexFlags())
                    append(" typeParameters=")
                    append(constructor.typeParameters.renderTypeParameters())
                    append(" genericParameters=")
                    append(constructor.genericParameterTypes.map { it.typeName })
                    append(" throws=")
                    append(constructor.genericExceptionTypes.map { it.typeName }.sorted())
                    appendLine()
                }

            type.declaredFields
                .filter { Modifier.isPublic(it.modifiers) }
                .sortedWith(compareBy({ it.name }, { it.type.descriptor() }))
                .forEach { field ->
                    append("  FIELD ")
                    append(field.name)
                    append(' ')
                    append(field.type.descriptor())
                    append(" flags=")
                    append(field.modifiers.hexFlags())
                    append(" genericType=")
                    append(field.genericType.typeName)
                    appendLine()
                }

            type.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .sortedWith(compareBy({ it.name }, { it.descriptor() }))
                .forEach { method ->
                    append("  METHOD ")
                    append(method.name)
                    append(method.descriptor())
                    append(" flags=")
                    append(method.modifiers.hexFlags())
                    append(" typeParameters=")
                    append(method.typeParameters.renderTypeParameters())
                    append(" genericReturn=")
                    append(method.genericReturnType.typeName)
                    append(" genericParameters=")
                    append(method.genericParameterTypes.map { it.typeName })
                    append(" throws=")
                    append(method.genericExceptionTypes.map { it.typeName }.sorted())
                    appendLine()
                }
        }

        private fun Array<out TypeVariable<*>>.renderTypeParameters(): String = map { parameter ->
            "${parameter.name}:${parameter.bounds.joinToString("&") { it.typeName }}"
        }.toString()

        private fun java.lang.reflect.Constructor<*>.descriptor(): String = buildString {
            append('(')
            parameterTypes.forEach { append(it.descriptor()) }
            append(")V")
        }

        private fun Method.descriptor(): String = buildString {
            append('(')
            parameterTypes.forEach { append(it.descriptor()) }
            append(')')
            append(returnType.descriptor())
        }

        private fun Int.hexFlags(): String = "0x${toString(16).padStart(4, '0')}"

        private fun Class<*>.descriptor(): String = when {
            isPrimitive -> when (this) {
                Void.TYPE -> "V"
                Boolean::class.javaPrimitiveType -> "Z"
                Byte::class.javaPrimitiveType -> "B"
                Char::class.javaPrimitiveType -> "C"
                Short::class.javaPrimitiveType -> "S"
                Int::class.javaPrimitiveType -> "I"
                Long::class.javaPrimitiveType -> "J"
                Float::class.javaPrimitiveType -> "F"
                Double::class.javaPrimitiveType -> "D"
                else -> error("unknown primitive $this")
            }

            isArray -> name.replace('.', '/')
            else -> "L${name.replace('.', '/')};"
        }
    }
}
