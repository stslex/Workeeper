// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Kotlin/Native proof that the exact production registry and generated serializers execute for
 * every route existing at this baseline. This fixed catalog is deliberately not the
 * hierarchy-change detector — [screenSampleCatalog] is shared with the JVM oracle, which asserts
 * the catalog's class set equals its reflected sealed-leaf set.
 */
internal class ScreenSerializationIosTest {

    private val json = Json {
        serializersModule = screenSavedStateConfiguration.serializersModule
    }

    @Test
    fun allCurrentRoutesRoundTripThroughProductionRegistry() {
        // An empty or truncated catalog would otherwise round-trip vacuously.
        assertEquals(
            SCREEN_ROUTE_BASELINE,
            screenSampleCatalog.size,
            "The shared catalog must hold exactly $SCREEN_ROUTE_BASELINE route instances",
        )

        val serializer = PolymorphicSerializer(NavKey::class)
        screenSampleCatalog.forEach { screen ->
            val encoded = json.encodeToString(serializer, screen)
            val decoded = json.decodeFromString(serializer, encoded)
            assertEquals(screen, decoded, "Round trip failed for ${screen::class.qualifiedName}")
        }

        // Pins both the literal "nav-result" prefix and the destination-class discriminator:
        // a sealed destination and its variant are DIFFERENT channels.
        assertEquals(
            "nav-result:${Screen.PlanEditor::class.qualifiedName}",
            NavResultKey.of(Screen.PlanEditor::class),
        )
        assertEquals(
            "nav-result:${Screen.PlanEditor.Existing::class.qualifiedName}",
            NavResultKey.of(Screen.PlanEditor.Existing::class),
        )
        assertNotEquals(
            NavResultKey.of(Screen.PlanEditor::class),
            NavResultKey.of(Screen.PlanEditor.Existing::class),
        )
    }
}
