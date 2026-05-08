// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test.fakes

import android.net.Uri
import androidx.core.net.toUri
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory fake of [ImageStorage] for instrumentation tests.
 *
 * Tracks the source URI committed for each `exerciseUuid` (no actual image decoding or
 * disk I/O), surfaces a stable synthetic absolute path, and counts every method call so
 * tests can assert behaviour without reaching into internals. [snapshot] is the read
 * surface; [reset] is for setup between tests.
 */
@Singleton
class FakeImageStorage @Inject constructor() : ImageStorage {

    private val storedPaths: MutableMap<String, String> = ConcurrentHashMap()
    private val storedSources: MutableMap<String, Uri> = ConcurrentHashMap()
    private val deletedPaths: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    @Volatile private var saveCount: Int = 0
    @Volatile private var deleteCount: Int = 0
    @Volatile private var createTempCount: Int = 0
    @Volatile private var cleanupCount: Int = 0

    override suspend fun saveImage(
        sourceUri: Uri,
        exerciseUuid: String,
    ): ImageSaveResult {
        saveCount += 1
        val path = "/fake-image-storage/$exerciseUuid.jpg"
        storedPaths[exerciseUuid] = path
        storedSources[exerciseUuid] = sourceUri
        deletedPaths.remove(path)
        return ImageSaveResult.Success(absolutePath = path)
    }

    override suspend fun createTempCaptureUri(): Uri {
        createTempCount += 1
        val id = createTempCount
        return "file:///fake-image-storage/.tmp/$id.jpg".toUri()
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
        val storedSources: Map<String, Uri>,
        val deletedPaths: Set<String>,
        val saveCount: Int,
        val deleteCount: Int,
        val createTempCount: Int,
        val cleanupCount: Int,
    )
}
