package io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model

import androidx.compose.runtime.Stable

@Stable
data class MenuItem<T : Any>(
    val uuid: String,
    val text: String,
    val itemModel: T,
)
