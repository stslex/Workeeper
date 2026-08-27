// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * Every concrete [Screen] leaf must be registered in [screenSerializersModule]; an unregistered
 * one fails only at process-death save time. JSON stands in for the `Bundle`-backed encoder.
 *
 * Exhaustive only for the current direct/sealed-reachable hierarchy: a route implementing solely
 * [ScreenWithResult] would escape [sealedLeaves]. This is not a classpath scan.
 */
internal class ScreenSerializationTest {

    private val json = Json {
        serializersModule = screenSavedStateConfiguration.serializersModule
    }

    @Test
    fun `every concrete Screen leaf round-trips through the production registry`() {
        val leaves = sealedLeaves(Screen::class)

        // Exact, not >=: a route-set change must deliberately update this reviewed baseline
        // (kmp-phase-7-2-navigation.md §6.2). An empty enumeration would pass vacuously.
        assertEquals(SCREEN_ROUTE_BASELINE, leaves.size) {
            "Expected exactly $SCREEN_ROUTE_BASELINE Screen leaves at this baseline, " +
                "found ${leaves.size}: $leaves"
        }

        // The shared Native catalog is pinned to the hierarchy here, on the only platform that
        // can enumerate it: Kotlin/Native has no sealed-subclass reflection, so without this
        // equality the catalog could silently drift from the routes that actually exist.
        val catalogClasses = screenSampleCatalog.map { sample -> sample::class }.toSet()
        assertEquals(SCREEN_ROUTE_BASELINE, screenSampleCatalog.size) {
            "screenSampleCatalog must hold exactly $SCREEN_ROUTE_BASELINE samples, " +
                "found ${screenSampleCatalog.size}"
        }
        assertEquals(leaves.toSet(), catalogClasses) {
            "screenSampleCatalog does not match the reflected sealed-leaf set — " +
                "missing ${leaves.toSet() - catalogClasses}, unexpected ${catalogClasses - leaves.toSet()}"
        }
        // Reached only when the class sets already match, so this names the remaining way 12
        // samples can cover 12 classes wrongly: one route sampled twice, another not at all.
        assertEquals(screenSampleCatalog.size, catalogClasses.size) {
            "screenSampleCatalog must sample each concrete route exactly once; " +
                "${screenSampleCatalog.size} samples cover only ${catalogClasses.size} classes"
        }

        leaves.forEach { leaf ->
            val instance = sampleOf(leaf)
            val serializer = PolymorphicSerializer(NavKey::class)
            val encoded = json.encodeToString(serializer, instance)
            val decoded = json.decodeFromString(serializer, encoded)
            assertEquals(instance, decoded) { "Round trip failed for ${leaf.qualifiedName}" }
        }

        // The module pins JvmDefaultMode.NO_COMPATIBILITY (the classic -Xjvm-default=all ABI):
        // both getters stay Java default methods and no DefaultImpls bridge may exist —
        // Kotlin 2.4's ENABLE default would generate both bridges without failing a compile.
        assertTrue(Screen::class.java.getMethod("isSingleTop").isDefault) {
            "Screen.isSingleTop must compile as a Java interface default method"
        }
        assertTrue(Screen.BottomBar::class.java.getMethod("isSingleTop").isDefault) {
            "Screen.BottomBar.isSingleTop must compile as a Java interface default method"
        }
        listOf(
            "io.github.stslex.workeeper.core.ui.navigation.Screen\$DefaultImpls",
            "io.github.stslex.workeeper.core.ui.navigation.Screen\$BottomBar\$DefaultImpls",
        ).forEach { binaryName ->
            assertThrows(
                ClassNotFoundException::class.java,
                { Class.forName(binaryName) },
                "$binaryName must not exist under the no-compatibility JVM default ABI",
            )
        }
    }

    private fun sealedLeaves(root: KClass<out Screen>): List<KClass<out Screen>> =
        root.sealedSubclasses.flatMap { sub ->
            @Suppress("UNCHECKED_CAST")
            val screenSub = sub as KClass<out Screen>
            when {
                screenSub.isSealed -> sealedLeaves(screenSub)
                // Marker interfaces are reached as concrete leaves elsewhere in the tree.
                screenSub.java.isInterface || screenSub.isAbstract -> emptyList()
                else -> listOf(screenSub)
            }
        }.distinct()

    private fun sampleOf(leaf: KClass<out Screen>): Screen =
        leaf.objectInstance ?: run {
            val ctor = checkNotNull(leaf.primaryConstructor) {
                "${leaf.qualifiedName} has no primary constructor"
            }
            val args = ctor.parameters.associateWith { parameter -> sampleValue(parameter.type) }
            ctor.callBy(args)
        }

    /** Non-null samples even for nullable params: a `null` field encodes as an absent key. */
    private fun sampleValue(type: KType): Any = when (type.classifier) {
        String::class -> "sample"
        Boolean::class -> true
        else -> error("No sample for parameter type $type — extend sampleValue")
    }
}
