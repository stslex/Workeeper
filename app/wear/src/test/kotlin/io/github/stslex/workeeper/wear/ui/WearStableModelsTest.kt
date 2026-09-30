// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.wear.ambient.WearAmbientOffset
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.mvi.store.WearStore
import kotlinx.collections.immutable.ImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * architecture.md, "@Stable and @Immutable": every data class a Composable reads is annotated and
 * carries `ImmutableList`, never `List`. Compose's annotations have binary retention, so the check
 * reads each class file and looks for the annotation descriptor instead of using reflection.
 */
internal class WearStableModelsTest {
    private val stable: Map<KClass<*>, String> = mapOf(
        WearStore.State::class to STABLE,
        WearPresentation::class to IMMUTABLE,
        WearPlatformState::class to IMMUTABLE,
        WearSurfaceModel::class to IMMUTABLE,
        WearSetScaleSlot::class to IMMUTABLE,
        WearFormattedValues::class to IMMUTABLE,
        WearAmbientState::class to IMMUTABLE,
        WearAmbientOffset::class to IMMUTABLE,
    )

    @Test
    fun everyComposableFacingModelCarriesItsStabilityAnnotation() {
        stable.forEach { (type, descriptor) ->
            val bytes = requireNotNull(type.java.getResourceAsStream("/${type.java.name.replace('.', '/')}.class")) {
                "class file of ${type.java.name}"
            }.use { it.readBytes() }
            assertTrue(bytes.contains(descriptor.toByteArray()), "${type.simpleName} must be annotated $descriptor")
        }
    }

    @Test
    fun composableFacingCollectionsAreImmutableLists() {
        assertEquals(ImmutableList::class.java, WearSurfaceModel::class.java.getMethod("getSetScaleSlots").returnType)
        val slots = Class.forName("io.github.stslex.workeeper.wear.ui.WearSetScaleSlotKt")
            .getDeclaredMethod("wearSetScaleSlots", Int::class.java, Int::class.java)
        assertEquals(ImmutableList::class.java, slots.returnType)
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        indices.any { start -> start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] } }

    private companion object {
        const val STABLE = "Landroidx/compose/runtime/Stable;"
        const val IMMUTABLE = "Landroidx/compose/runtime/Immutable;"
    }
}
