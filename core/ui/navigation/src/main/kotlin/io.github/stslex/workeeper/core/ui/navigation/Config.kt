package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
@Stable
sealed interface Config {

    val isBackAllow: Boolean
        get() = true

    @Serializable
    data object Home : Config {

        override val isBackAllow: Boolean = false
    }

    @Serializable
    data class Exercise(val data: Data) : Config {

        @Serializable
        sealed interface Data {

            data object New : Data

            data class Edit(
                val uuid: String,
                val name: String,
                val sets: Int,
                val reps: Int,
                val weight: Double,
                val timestamp: Long,
            ) : Data
        }
    }
}