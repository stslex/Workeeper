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
                    java.lang.reflect.Modifier.isStatic(helper.modifiers),
                    "${type.simpleName}.${helper.name} must be static",
                )
            }
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
    }
}
