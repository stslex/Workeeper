// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.model

import android.net.Uri
import androidx.compose.runtime.Stable

@Stable
sealed interface ImageDisplay {

    data object None : ImageDisplay

    data class FromPath(val path: String, val lastModified: Long) : ImageDisplay

    data class FromUri(val uri: Uri) : ImageDisplay
}
