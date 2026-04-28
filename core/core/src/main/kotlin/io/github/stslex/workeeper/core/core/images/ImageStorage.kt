// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.net.Uri
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult

interface ImageStorage {

    /**
     * Reads the image at [sourceUri], decodes it, downsamples to fit within
     * [MAX_EDGE]x[MAX_EDGE], compresses as JPEG quality [QUALITY], and writes
     * it atomically to filesDir/exercise_images/<exerciseUuid>.jpg.
     *
     * If a file already exists at the destination, it is overwritten (atomic
     * via temp file + rename — old file remains intact if the process dies).
     *
     * Caller is responsible for invoking deleteImage() on any *previous* path
     * that this exercise had — saveImage does not track that.
     *
     * @return [ImageSaveResult.Success] on success, or [ImageSaveResult.Failure]
     *         on failure. Never throws.
     */
    suspend fun saveImage(sourceUri: Uri, exerciseUuid: String): ImageSaveResult

    /**
     * Returns a temporary file URI suitable for handing to
     * ActivityResultContracts.TakePicture. The caller passes this URI to the
     * camera launcher; on success, the camera writes the captured image there.
     * The caller is then expected to call saveImage(tempUri, exerciseUuid) to
     * downsample + persist to the canonical location.
     *
     * Temp files live in filesDir/exercise_images/.tmp/ and are cleaned up by
     * cleanupTempFiles() on next app start.
     */
    suspend fun createTempCaptureUri(): Uri

    /**
     * Deletes the file at [path]. No-op if absent. Returns true if a file
     * was actually deleted.
     */
    suspend fun deleteImage(path: String): Boolean

    /**
     * Removes any temp capture files left behind by killed processes.
     * Called once at app startup.
     */
    suspend fun cleanupTempFiles()

    companion object {
        const val MAX_EDGE: Int = 1280
        const val QUALITY: Int = 85
        const val DIRECTORY: String = "exercise_images"
        const val TEMP_SUBDIRECTORY: String = ".tmp"
        const val FILE_EXTENSION: String = ".jpg"
    }
}
