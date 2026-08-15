// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The polymorphic registry that lets the app-owned back stack survive process death.
 *
 * `rememberNavBackStack(configuration, …)` — the common, reflection-free overload — encodes each
 * entry through this module's `NavKey` open polymorphism. Every CONCRETE [Screen] leaf must be
 * listed; a missing one fails at SAVE time, i.e. in production only, on process death. That
 * failure mode is why `ScreenSerializationTest` exists: it enumerates the sealed hierarchy via
 * `sealedSubclasses` and round-trips an instance of each leaf through
 * `encodeToSavedState`/`decodeFromSavedState` with THIS configuration, so an unregistered
 * destination is a red unit test on every PR instead.
 *
 * Registered under [NavKey] only — that is the single static type the back-stack serializer
 * uses, and a second registration under `Screen` would be a second list to forget.
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
