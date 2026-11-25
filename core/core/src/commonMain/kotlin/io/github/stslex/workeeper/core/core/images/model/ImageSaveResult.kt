// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images.model

sealed interface ImageSaveResult {

    data class Success(
        val absolutePath: String,
        /** This write created a private candidate that can be deleted after a rejected row update. */
        val discardOnRejection: Boolean = false,
    ) : ImageSaveResult

    data class Failure(val error: ImageSaveError) : ImageSaveResult
}
