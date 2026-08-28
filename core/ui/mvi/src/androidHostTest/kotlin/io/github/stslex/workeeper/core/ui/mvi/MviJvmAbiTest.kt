// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScopeImpl
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreCreator
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.SuspendProcessor
import io.github.stslex.workeeper.core.ui.mvi.store.StoreConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
        EXPECTED_TYPE_PARAMETER_BOUNDS.forEach { (type, expected) ->
            val actual = type.typeParameters.map { parameter ->
                "${parameter.name}:${parameter.bounds.joinToString("&") { it.typeName }}"
            }
            assertEquals(expected, actual, "${type.name} generic bounds changed")
        }

        EXPECTED_PUBLIC_METHODS.forEach { (type, expected) ->
            val actual = type.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .map(::methodSignature)
                .toSet()
            assertEquals(expected, actual, "${type.name} public JVM methods changed")
        }

        EXPECTED_ABSENT_CLASSES.forEach { name ->
            assertFalse(
                runCatching { Class.forName(name, false, BaseStore::class.java.classLoader) }
                    .isSuccess,
                "$name is outside the measured public ABI manifest",
            )
        }
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

        val STORE_PROCESSOR_FILE = Class.forName(
            "io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessorKt",
        )
        val METRO_PROCESSOR_FILE = Class.forName(
            "io.github.stslex.workeeper.core.ui.mvi.processor.MetroStoreProcessorKt",
        )
        val EXPECTED_ABSENT_CLASSES = setOf(
            "io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor\$ComposeDefaultImpls",
        )

        val EXPECTED_TYPE_PARAMETER_BOUNDS = mapOf(
            Store::class.java to storeBounds(),
            StoreConsumer::class.java to storeBounds(),
            HandlerStore::class.java to storeBounds(),
            HandlerStoreEmitter::class.java to storeBounds(),
            StoreProcessor::class.java to storeBounds(),
            StoreCreator::class.java to listOf(
                "TStoreImpl:io.github.stslex.workeeper.core.ui.mvi.BaseStore<?, ?, ?>",
            ),
            SuspendProcessor::class.java to listOf(
                "E:io.github.stslex.workeeper.core.ui.mvi.Store\$Event",
            ),
        )

        val EXPECTED_PUBLIC_METHODS = mapOf(
            Store::class.java to setOf(
                "consume(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Action;)V",
                "getEvent()Lkotlinx/coroutines/flow/SharedFlow;",
                "getState()Lkotlinx/coroutines/flow/StateFlow;",
            ),
            StoreConsumer::class.java to consumerMethods(
                "io/github/stslex/workeeper/core/ui/mvi/store/StoreConsumer",
            ),
            HandlerStore::class.java to consumerMethods(
                "io/github/stslex/workeeper/core/ui/mvi/handler/HandlerStore",
            ),
            HandlerStoreEmitter::class.java to setOf(
                "clearStore()V",
                "setStore(Lio/github/stslex/workeeper/core/ui/mvi/store/StoreConsumer;)V",
            ),
            StoreProcessor::class.java to setOf(
                "Handle(Lio/github/stslex/workeeper/core/ui/mvi/processor/SuspendProcessor;" +
                    "Landroidx/compose/runtime/Composer;I)V",
                "consume(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Action;)V",
                "getState()Landroidx/compose/runtime/State;",
            ),
            SuspendProcessor::class.java to setOf(
                "invoke(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Event;" +
                    "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
            ),
            PerformanceMetricsRecorder::class.java to setOf(
                "process(Lio/github/stslex/workeeper/core/ui/mvi/performance/RecordAction;)V",
            ),
            STORE_PROCESSOR_FILE to setOf(
                "rememberStoreProcessor(" +
                    "Lio/github/stslex/workeeper/core/ui/mvi/processor/StoreCreator;" +
                    "Landroidx/compose/runtime/Composer;I)" +
                    "Lio/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor;",
            ),
            METRO_PROCESSOR_FILE to setOf(
                "rememberMetroStoreProcessor(Lkotlin/jvm/functions/Function0;" +
                    "Landroidx/compose/runtime/Composer;I)" +
                    "Lio/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor;",
            ),
        )

        private fun storeBounds(): List<String> = listOf(
            "S:io.github.stslex.workeeper.core.ui.mvi.Store\$State",
            "A:io.github.stslex.workeeper.core.ui.mvi.Store\$Action",
            "E:io.github.stslex.workeeper.core.ui.mvi.Store\$Event",
        )

        private fun consumerMethods(owner: String): Set<String> = setOf(
            "consume(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Action;)V",
            "consumeOnMain(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Action;" +
                "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
            "getLastAction()Lio/github/stslex/workeeper/core/ui/mvi/Store\$Action;",
            "getLogger()Lio/github/stslex/workeeper/core/core/logger/Logger;",
            "getState()Lkotlinx/coroutines/flow/StateFlow;",
            "launch(Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function3;" +
                "Lkotlinx/coroutines/CoroutineDispatcher;Lkotlinx/coroutines/CoroutineDispatcher;" +
                "Lkotlin/jvm/functions/Function2;)Lkotlinx/coroutines/Job;",
            "launch(Lkotlinx/coroutines/flow/Flow;Lkotlin/jvm/functions/Function2;" +
                "Lkotlinx/coroutines/CoroutineDispatcher;Lkotlinx/coroutines/CoroutineDispatcher;" +
                "Lkotlin/jvm/functions/Function2;)Lkotlinx/coroutines/Job;",
            "launch\$default(L$owner;Lkotlin/jvm/functions/Function2;" +
                "Lkotlin/jvm/functions/Function3;Lkotlinx/coroutines/CoroutineDispatcher;" +
                "Lkotlinx/coroutines/CoroutineDispatcher;Lkotlin/jvm/functions/Function2;" +
                "ILjava/lang/Object;)Lkotlinx/coroutines/Job;",
            "launch\$default(L$owner;Lkotlinx/coroutines/flow/Flow;" +
                "Lkotlin/jvm/functions/Function2;Lkotlinx/coroutines/CoroutineDispatcher;" +
                "Lkotlinx/coroutines/CoroutineDispatcher;Lkotlin/jvm/functions/Function2;" +
                "ILjava/lang/Object;)Lkotlinx/coroutines/Job;",
            "launchDefault(Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function3;" +
                "Lkotlin/jvm/functions/Function2;)Lkotlinx/coroutines/Job;",
            "launchDefault\$default(L$owner;Lkotlin/jvm/functions/Function2;" +
                "Lkotlin/jvm/functions/Function3;Lkotlin/jvm/functions/Function2;" +
                "ILjava/lang/Object;)Lkotlinx/coroutines/Job;",
            "sendEvent(Lio/github/stslex/workeeper/core/ui/mvi/Store\$Event;)V",
            "updateState(Lkotlin/jvm/functions/Function1;)V",
            "updateStateImmediate(Lio/github/stslex/workeeper/core/ui/mvi/Store\$State;" +
                "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
            "updateStateImmediate(Lkotlin/jvm/functions/Function2;" +
                "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        )

        private fun methodSignature(method: Method): String = buildString {
            append(method.name)
            append('(')
            method.parameterTypes.forEach { append(it.descriptor()) }
            append(')')
            append(method.returnType.descriptor())
        }

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
