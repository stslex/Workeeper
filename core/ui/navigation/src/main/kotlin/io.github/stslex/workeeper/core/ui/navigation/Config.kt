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
}