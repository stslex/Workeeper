// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.core.images.ImageRef

@Stable
sealed interface PendingImage {

    data object Unchanged : PendingImage

    data class NewFromUri(val ref: ImageRef) : PendingImage

    data object RemoveExisting : PendingImage
}
