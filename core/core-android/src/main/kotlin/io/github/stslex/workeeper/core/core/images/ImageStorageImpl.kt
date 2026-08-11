// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.DIRECTORY
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.FILE_EXTENSION
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.MAX_EDGE
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.QUALITY
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.TEMP_SUBDIRECTORY
import io.github.stslex.workeeper.core.core.images.model.ImageSaveError
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

// Plain class constructed by `buildImageStorage` (the create()-root factory); ctor params are plain.
class ImageStorageImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageStorage {

    private val logger: Logger = Log.tag("ImageStorage")

    private val rootDir: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private val tempDir: File
        get() = File(rootDir, TEMP_SUBDIRECTORY).apply { mkdirs() }

    private val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"

    override suspend fun saveImage(
        sourceRef: ImageRef,
        exerciseUuid: String,
    ): ImageSaveResult = withContext(ioDispatcher) {
        val sourceUri = sourceRef.value.toUri()
        val destFile = File(rootDir, "$exerciseUuid$FILE_EXTENSION")
        val tempFile = File(rootDir, "$exerciseUuid$FILE_EXTENSION.tmp")
        try {
            val bitmap = decodeDownsampled(sourceUri)
                ?: return@withContext ImageSaveResult.Failure(ImageSaveError.SourceUnreadable)
            try {
                writeJpeg(bitmap, tempFile)
            } finally {
                bitmap.recycle()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.delete()
                return@withContext ImageSaveResult.Failure(ImageSaveError.IoFailure)
            }
            ImageSaveResult.Success(destFile.absolutePath)
        } catch (cause: FileNotFoundException) {
            logger.e(cause, "Source not found: $sourceUri")
            tempFile.delete()
            ImageSaveResult.Failure(ImageSaveError.SourceUnreadable)
        } catch (cause: ImageDecoder.DecodeException) {
            logger.e(cause, "Failed to decode $sourceUri")
            tempFile.delete()
            ImageSaveResult.Failure(ImageSaveError.SourceUnreadable)
        } catch (cause: IOException) {
            logger.e(cause, "IO failure saving $sourceUri")
            tempFile.delete()
            ImageSaveResult.Failure(diagnoseIoError(cause))
        } catch (cause: SecurityException) {
            logger.e(cause, "Security failure reading $sourceUri")
            tempFile.delete()
            ImageSaveResult.Failure(ImageSaveError.SourceUnreadable)
        }
    }

    override suspend fun createTempCaptureRef(): ImageRef = withContext(ioDispatcher) {
        val tempFile = File.createTempFile("capture_", FILE_EXTENSION, tempDir)
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority, tempFile)
        ImageRef(uri.toString())
    }

    override suspend fun deleteImage(path: String): Boolean = withContext(ioDispatcher) {
        if (path.isBlank()) return@withContext false
        val file = File(path)
        file.exists() && file.delete()
    }

    override suspend fun cleanupTempFiles() {
        withContext(ioDispatcher) {
            tempDir.listFiles()?.forEach(File::delete)
        }
    }

    private fun decodeDownsampled(sourceUri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width
            val height = info.size.height
            val longestEdge = maxOf(width, height)
            if (longestEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longestEdge
                val targetWidth = (width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (height * scale).toInt().coerceAtLeast(1)
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }
    }

    private fun writeJpeg(bitmap: Bitmap, destination: File) {
        destination.outputStream().use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (!ok) throw IOException("Bitmap.compress returned false for ${destination.absolutePath}")
            out.flush()
        }
    }

    private fun diagnoseIoError(cause: IOException): ImageSaveError {
        val message = cause.message.orEmpty().lowercase()
        return if ("space" in message || "enospc" in message) {
            ImageSaveError.OutOfSpace
        } else {
            ImageSaveError.IoFailure
        }
    }
}
