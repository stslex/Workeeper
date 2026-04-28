// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.model

import android.net.Uri
import androidx.compose.runtime.Stable

@Stable
sealed interface PendingImage {

    data object Unchanged : PendingImage

    data class NewFromUri(val uri: Uri) : PendingImage

    data object RemoveExisting : PendingImage
}
