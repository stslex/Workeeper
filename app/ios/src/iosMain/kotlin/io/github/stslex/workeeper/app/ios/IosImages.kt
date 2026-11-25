// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.ios

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveError
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.ui.kit.media.AppleImagePresenter
import io.github.stslex.workeeper.core.ui.kit.media.AppleImageSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

interface AppleMediaServices {
    fun save(source: String, uuid: String, maxEdge: Int, quality: Int): AppleImageWrite
    fun capturePath(): String
    fun resolve(path: String): String?
    fun delete(path: String): Boolean
    fun modified(path: String): Long
    fun cleanTemps()
    fun cameraGranted(): Boolean
    fun camera(target: String, request: AppleMediaRequest)
    fun gallery(request: AppleMediaRequest)
    fun permission(request: AppleMediaRequest)
    fun settings()
    fun retire(request: AppleMediaRequest)
}

class AppleImageWrite(val path: String?, val outOfSpace: Boolean, val unreadable: Boolean)

interface AppleMediaRequest {
    fun picked(path: String)
    fun cancelled()
    fun permissionResult(granted: Boolean)
}

internal class IosImages(private val services: AppleMediaServices) : ImageStorage {
    override suspend fun saveImage(
        sourceRef: ImageRef,
        exerciseUuid: String,
    ): ImageSaveResult = withContext(Dispatchers.IO) {
        val write = services.save(sourceRef.value, exerciseUuid, ImageStorage.MAX_EDGE, ImageStorage.QUALITY)
        when {
            write.path != null -> ImageSaveResult.Success(write.path)
            write.outOfSpace -> ImageSaveResult.Failure(ImageSaveError.OutOfSpace)
            write.unreadable -> ImageSaveResult.Failure(ImageSaveError.SourceUnreadable)
            else -> ImageSaveResult.Failure(ImageSaveError.IoFailure)
        }
    }

    override suspend fun createTempCaptureRef() = withContext(Dispatchers.IO) { ImageRef(services.capturePath()) }
    override fun resolvePath(path: String): String? = services.resolve(path)
    override suspend fun deleteImage(path: String) = withContext(Dispatchers.IO) { services.delete(path) }
    override suspend fun lastModified(path: String) = withContext(Dispatchers.IO) { services.modified(path) }
    override suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) { services.cleanTemps() }
}

internal class AppleMediaPresenter(
    private val media: AppleMediaServices,
    private val currentId: () -> Int?,
) : AppleImagePresenter {
    override fun bind(
        picked: (String) -> Unit,
        cancelled: () -> Unit,
        permission: (Boolean) -> Unit,
    ): AppleImageSession {
        val generation = currentId()
        return object : AppleImageSession, AppleMediaRequest {
            var active = true
            fun live() = active && generation != null && generation == currentId()
            override fun picked(path: String) {
                if (live()) picked.invoke(path)
            }

            override fun cancelled() {
                if (live()) cancelled.invoke()
            }

            override fun permissionResult(granted: Boolean) {
                if (live()) permission(granted)
            }

            override fun launchGallery() {
                if (live()) media.gallery(this)
            }

            override fun launchCamera(targetRef: String) {
                if (live()) media.camera(targetRef, this)
            }

            override fun requestCameraPermission() {
                if (live()) media.permission(this)
            }

            override fun openAppSettings() {
                if (live()) media.settings()
            }

            override fun retire() {
                active = false
                media.retire(this)
            }
        }
    }
}
