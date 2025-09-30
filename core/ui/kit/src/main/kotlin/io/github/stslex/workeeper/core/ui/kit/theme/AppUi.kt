package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Suppress("unused")
object AppUi {

    val uiFeatures: AppUiFeatures
        @Composable
        @ReadOnlyComposable
        get() = LocalAppUiFeatures.current
}
