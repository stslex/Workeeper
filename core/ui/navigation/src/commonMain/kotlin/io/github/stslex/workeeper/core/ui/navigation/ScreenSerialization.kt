// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Polymorphic registry that lets the app-owned back stack survive process death. Every concrete
 * [Screen] leaf must be listed; a missing one fails at SAVE time, in production only.
 */
val screenSerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Screen.BottomBar.Home::class)
        subclass(Screen.BottomBar.AllExercises::class)
        subclass(Screen.BottomBar.AllTrainings::class)
        subclass(Screen.Training::class)
        subclass(Screen.Exercise::class)
        subclass(Screen.LiveWorkout::class)
        subclass(Screen.Settings::class)
        subclass(Screen.Archive::class)
        subclass(Screen.PastSession::class)
        subclass(Screen.ExerciseChart::class)
        subclass(Screen.ExerciseImage::class)
        subclass(Screen.PlanEditor.Existing::class)
    }
}

val screenSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = screenSerializersModule
}
