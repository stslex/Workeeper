package io.github.stslex.workeeper.core.ui.kit.components.topbar

import androidx.compose.runtime.Stable

@Stable
data class TopbarAction(
    val titleRes: Int,
    val testTag: String,
    val onClick: () -> Unit,
)
