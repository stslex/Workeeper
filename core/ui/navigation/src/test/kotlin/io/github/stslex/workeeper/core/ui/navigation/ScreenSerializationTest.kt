// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * Every concrete [Screen] leaf must be registered in [screenSerializersModule]; an unregistered
 * one fails only at process-death save time. JSON stands in for the `Bundle`-backed encoder.
 */
internal class ScreenSerializationTest {

    private val json = Json {
        serializersModule = screenSavedStateConfiguration.serializersModule
    }

    @Test
    fun `every concrete Screen leaf round-trips through the production registry`() {
        val leaves = sealedLeaves(Screen::class)

        // Guard the discovery itself: an empty enumeration would pass vacuously.
        assertTrue(leaves.size >= 12) { "Expected >= 12 Screen leaves, found ${leaves.size}: $leaves" }

        leaves.forEach { leaf ->
            val instance = sampleOf(leaf)
            val serializer = PolymorphicSerializer(NavKey::class)
            val encoded = json.encodeToString(serializer, instance)
            val decoded = json.decodeFromString(serializer, encoded)
            assertEquals(instance, decoded) { "Round trip failed for ${leaf.qualifiedName}" }
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
