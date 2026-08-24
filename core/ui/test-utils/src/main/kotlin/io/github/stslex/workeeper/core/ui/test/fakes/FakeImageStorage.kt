// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test.fakes

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fake of [ImageStorage] for instrumentation tests: records the committed source per
 * exercise, returns a synthetic path, counts calls. Read through [snapshot]; [reset] between tests.
 */
class FakeImageStorage : ImageStorage {

    private val storedPaths: MutableMap<String, String> = ConcurrentHashMap()
    private val storedSources: MutableMap<String, ImageRef> = ConcurrentHashMap()
    private val deletedPaths: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    @Volatile private var saveCount: Int = 0
    @Volatile private var deleteCount: Int = 0
    @Volatile private var createTempCount: Int = 0
    @Volatile private var cleanupCount: Int = 0

    override suspend fun saveImage(
        sourceRef: ImageRef,
        exerciseUuid: String,
    ): ImageSaveResult {
        saveCount += 1
        val path = "/fake-image-storage/$exerciseUuid.jpg"
        storedPaths[exerciseUuid] = path
        storedSources[exerciseUuid] = sourceRef
        deletedPaths.remove(path)
        return ImageSaveResult.Success(absolutePath = path)
    }

    override suspend fun createTempCaptureRef(): ImageRef {
        createTempCount += 1
        val id = createTempCount
        return ImageRef("file:///fake-image-storage/.tmp/$id.jpg")
    }

    override suspend fun deleteImage(path: String): Boolean {
        deleteCount += 1
        val removed = storedPaths.values.removeAll(setOf(path))
        deletedPaths += path
        return removed
    }

    override suspend fun cleanupTempFiles() {
        cleanupCount += 1
    }

    fun snapshot(): Snapshot = Snapshot(
        storedPaths = storedPaths.toMap(),
        storedSources = storedSources.toMap(),
        deletedPaths = deletedPaths.toSet(),
        saveCount = saveCount,
        deleteCount = deleteCount,
        createTempCount = createTempCount,
        cleanupCount = cleanupCount,
    )

    fun reset() {
        storedPaths.clear()
        storedSources.clear()
        deletedPaths.clear()
        saveCount = 0
        deleteCount = 0
        createTempCount = 0
        cleanupCount = 0
    }

    data class Snapshot(
        val storedPaths: Map<String, String>,
        val storedSources: Map<String, ImageRef>,
        val deletedPaths: Set<String>,
        val saveCount: Int,
        val deleteCount: Int,
        val createTempCount: Int,
        val cleanupCount: Int,
    )
}
