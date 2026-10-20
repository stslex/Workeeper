// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.images.ImageRef

@Stable
sealed interface ImageDisplay {

    data object None : ImageDisplay

    data class FromPath(val path: String, val lastModified: Long) : ImageDisplay

    data class FromUri(val ref: ImageRef) : ImageDisplay
}
