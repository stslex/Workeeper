// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.mvi

import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.ui.WearOngoingNotice
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Every leaf `WearStore.Action` / `Event` is built with sentinel values and its `toString()` — the
 * payload of the Store's Analytics event and Crashlytics breadcrumb — must not carry any of them.
 * A new leaf without a registry entry fails the exhaustiveness check.
 */
internal class WearStoreActionRegistryTest {
    private val sentinelModel = WearSurfaceModel(
        kind = WearSurfaceKind.ACTIVE,
        trainingName = SENTINEL_TRAINING,
        exerciseName = SENTINEL_EXERCISE,
        completedExercises = SENTINEL_COUNT,
        totalExercises = SENTINEL_COUNT,
        setOrdinal = 1,
        totalSets = SENTINEL_COUNT,
        reps = SENTINEL_REPS,
        weightHundredthsKg = SENTINEL_WEIGHT,
        weighted = true,
        controlsVisible = true,
        controlsEnabled = true,
        completeEnabled = true,
        hasUnsubmittedDraft = true,
    )

    private val registry: Map<KClass<*>, () -> Any> = mapOf(
        Action.Common.Init::class to { Action.Common.Init },
        Action.Common.RuntimeChanged::class to { Action.Common.RuntimeChanged },
        Action.Common.PlatformChanged::class to {
            Action.Common.PlatformChanged(
                WearPlatformState(
                    ambient = WearAmbientState(isAmbient = true, timestampMillis = SENTINEL_TIMESTAMP),
                    notificationsEnabled = false,
                    previewId = SENTINEL_PREVIEW,
                ),
            )
        },
        Action.Common.PresentationChanged::class to {
            Action.Common.PresentationChanged(
                WearPresentation(
                    model = sentinelModel,
                    ambient = WearAmbientState(isAmbient = true, timestampMillis = SENTINEL_TIMESTAMP),
                    notice = WearOngoingNotice.NOTIFICATIONS_DISABLED,
                    preview = true,
                ),
            )
        },
        Action.Click.Edit::class to { Action.Click.Edit(NumericField.WEIGHT) },
        Action.Click.Complete::class to { Action.Click.Complete },
        Action.Click.Retry::class to { Action.Click.Retry },
        Action.Click.EnableNotifications::class to { Action.Click.EnableNotifications },
        Action.Input.Draft::class to { Action.Input.Draft(NumericField.WEIGHT, SENTINEL_REPS) },
        Action.Navigation.CloseEditor::class to { Action.Navigation.CloseEditor },
        Event.EnableNotificationsRequested::class to { Event.EnableNotificationsRequested },
    )

    @Test
    fun everyLeafActionAndEventHasARegistryEntry() {
        val leaves = (leaves(Action::class) + leaves(Event::class)).toSet()
        assertTrue(leaves.isNotEmpty())
        assertEquals(leaves, registry.keys, "Every sealed leaf needs a sentinel builder; extra or missing entries fail")
    }

    @Test
    fun noLeafDescriptionCarriesASentinelValue() {
        val sentinels = listOf(
            SENTINEL_REPS.toString(),
            SENTINEL_WEIGHT.toString(),
            SENTINEL_COUNT.toString(),
            SENTINEL_TIMESTAMP.toString(),
            SENTINEL_TRAINING,
            SENTINEL_EXERCISE,
            SENTINEL_PREVIEW,
        )
        registry.forEach { (leaf, build) ->
            val description = build().toString()
            sentinels.forEach { sentinel ->
                assertFalse(sentinel in description, "${leaf.simpleName}.toString() leaks $sentinel: $description")
            }
        }
    }

    private fun leaves(type: KClass<*>): List<KClass<*>> =
        if (type.isSealed) type.sealedSubclasses.flatMap(::leaves) else listOf(type)

    private companion object {
        const val SENTINEL_REPS = 987_654
        const val SENTINEL_WEIGHT = 12_345_678
        const val SENTINEL_COUNT = 4_321
        const val SENTINEL_TIMESTAMP = 1_700_000_987_654L
        const val SENTINEL_TRAINING = "SENTINEL-training"
        const val SENTINEL_EXERCISE = "SENTINEL-exercise"
        const val SENTINEL_PREVIEW = "SENTINEL-preview"
    }
}
