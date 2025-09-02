package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
@Serializable
sealed interface DialogConfig {

    @Serializable
    data object CreateExercise : DialogConfig
}