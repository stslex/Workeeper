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
 * every route existing at this baseline. The fixed catalog is deliberately not a
 * hierarchy-change detector — the JVM reflection oracle in `ScreenSerializationTest` fills that
 * role; this list must be updated together with its exact-count baseline.
 */
internal class ScreenSerializationIosTest {

    private val json = Json {
        serializersModule = screenSavedStateConfiguration.serializersModule
    }

    @Test
    fun allCurrentRoutesRoundTripThroughProductionRegistry() {
        // Non-null, non-default samples throughout: a null field encodes as an absent key and
        // would let an asymmetric field slip through the round trip undetected.
        val catalog: List<Screen> = listOf(
            Screen.BottomBar.Home,
            Screen.BottomBar.AllExercises,
            Screen.BottomBar.AllTrainings,
            Screen.Training(uuid = "training-uuid"),
            Screen.Exercise(uuid = "exercise-uuid"),
            Screen.LiveWorkout(sessionUuid = "session-uuid", trainingUuid = "training-uuid"),
            Screen.Settings,
            Screen.Archive,
            Screen.PastSession(sessionUuid = "session-uuid"),
            Screen.ExerciseChart(exerciseUuid = "exercise-uuid"),
            Screen.ExerciseImage(model = "content://sample/image", editable = true),
            Screen.PlanEditor.Existing(
                performedExerciseUuid = "performed-exercise-uuid",
                exerciseUuid = "exercise-uuid",
                trainingUuid = "training-uuid",
            ),
        )

        assertEquals(12, catalog.size, "The catalog must hold exactly 12 route instances")
        assertEquals(
            setOf(
                Screen.BottomBar.Home::class,
                Screen.BottomBar.AllExercises::class,
                Screen.BottomBar.AllTrainings::class,
                Screen.Training::class,
                Screen.Exercise::class,
                Screen.LiveWorkout::class,
                Screen.Settings::class,
                Screen.Archive::class,
                Screen.PastSession::class,
                Screen.ExerciseChart::class,
                Screen.ExerciseImage::class,
                Screen.PlanEditor.Existing::class,
            ),
            catalog.map { it::class }.toSet(),
            "The catalog must cover the exact 12 concrete route classes",
        )

        val serializer = PolymorphicSerializer(NavKey::class)
        catalog.forEach { screen ->
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
