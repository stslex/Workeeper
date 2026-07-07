// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images.model

sealed interface ImageSaveResult {

    data class Success(val absolutePath: String) : ImageSaveResult

    data class Failure(val error: ImageSaveError) : ImageSaveResult
}
