// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult

interface ImageStorage {

    /**
     * Downsamples [sourceRef] and writes it atomically as JPEG to the exercise's canonical path.
     * Never throws; the caller must delete the exercise's previous path itself.
     */
    suspend fun saveImage(sourceRef: ImageRef, exerciseUuid: String): ImageSaveResult

    /** A temp capture reference for the camera launcher; pass it to [saveImage] to persist. */
    suspend fun createTempCaptureRef(): ImageRef

    /** Deletes the file at [path]. No-op if absent; returns true if a file was deleted. */
    suspend fun deleteImage(path: String): Boolean

    /** Removes temp capture files left behind by killed processes. Runs once at app startup. */
    suspend fun cleanupTempFiles()

    companion object {
        const val MAX_EDGE: Int = 1280
        const val QUALITY: Int = 85
        const val DIRECTORY: String = "exercise_images"
        const val TEMP_SUBDIRECTORY: String = ".tmp"
        const val FILE_EXTENSION: String = ".jpg"
    }
}
