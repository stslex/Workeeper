// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import android.os.storage.StorageManager
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import java.io.File

/** Injectable advisory capacity query and file-size seam. It never reserves allocation. */
interface RestoreStorageCapacity {

    fun getAllocatableBytes(path: File): Long

    fun sizeBytes(file: File): Long
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<RestoreStorageCapacity>())
public class AndroidRestoreStorageCapacity @Inject constructor(
    context: Context,
) : RestoreStorageCapacity {

    private val storageManager = context.getSystemService(StorageManager::class.java)
        ?: error("StorageManager unavailable")

    override fun getAllocatableBytes(path: File): Long {
        val storageUuid = storageManager.getUuidForPath(path)
        return storageManager.getAllocatableBytes(storageUuid)
    }

    override fun sizeBytes(file: File): Long = file.length()
}
