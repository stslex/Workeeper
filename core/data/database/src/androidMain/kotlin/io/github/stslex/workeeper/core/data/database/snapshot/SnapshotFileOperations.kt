// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal object SqliteFileValidator {

    fun verifyStructural(source: File): BackupResult<Unit> {
        val magic = runCatching { readMagic(source) }.fold(
            onSuccess = { valid ->
                if (valid) {
                    BackupResult.Success(Unit)
                } else {
                    BackupResult.Failure(
                        BackupError.CorruptedBackup(reason = "invalid SQLite header"),
                    )
                }
            },
            onFailure = { error ->
                BackupResult.Failure(
                    BackupError.CorruptedBackup(
                        reason = error.message ?: "header read failed",
                    ),
                )
            },
        )
        if (magic is BackupResult.Failure) return magic
        return SqliteHeaderCheck.verifyComplete(source)
    }

    private fun readMagic(source: File): Boolean {
        val actual = ByteArray(SQLITE_MAGIC.size)
        var filled = 0
        source.inputStream().use { input ->
            while (filled < actual.size) {
                val read = input.read(actual, filled, actual.size - filled)
                if (read < 0) break
                filled += read
            }
        }
        return filled == actual.size && actual.contentEquals(SQLITE_MAGIC)
    }

    private val SQLITE_MAGIC: ByteArray =
        "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0x00.toByte()
}

internal object LiveDatabaseFileReplacer {

    fun replace(
        context: Context,
        source: File,
        durability: SnapshotFileDurability = PlatformSnapshotFileDurability,
    ): BackupResult<Unit> = backupIoResult {
        val target = context.getDatabasePath(AppDatabase.NAME)
        val parent = target.parentFile ?: throw IOException("database parent dir missing")
        requireDirectory(parent)
        Files.deleteIfExists(File(parent, "${AppDatabase.NAME}-wal").toPath())
        Files.deleteIfExists(File(parent, "${AppDatabase.NAME}-shm").toPath())
        val temporary = File(parent, "${AppDatabase.NAME}.tmp")
        try {
            if (Files.exists(temporary.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                !Files.deleteIfExists(temporary.toPath())
            ) {
                throw IOException("database temporary file cannot be replaced")
            }
            copyFile(source, temporary)
            durability.syncFile(temporary)
            // Persist the complete temporary and sidecar deletions before publishing its name.
            durability.syncDirectory(parent)
            moveSyncedFileReplacing(temporary, target, durability)
        } finally {
            temporary.delete()
        }
    }
}

/** Crash-durability seam for file data and directory-entry publication. */
internal interface SnapshotFileDurability {

    fun syncFile(file: File)

    fun syncDirectory(directory: File)
}

internal object PlatformSnapshotFileDurability : SnapshotFileDurability {

    override fun syncFile(file: File) {
        if (!file.isFile) throw IOException("file unavailable for sync: ${file.name}")
        FileOutputStream(file, true).use { output -> output.fd.sync() }
    }

    override fun syncDirectory(directory: File) {
        if (!directory.isDirectory) {
            throw IOException("directory unavailable for sync: ${directory.name}")
        }
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
            channel.force(true)
        }
    }
}

/** Publishes a previously synced, same-directory file and then persists the directory entry. */
internal fun moveSyncedFileReplacing(
    source: File,
    target: File,
    durability: SnapshotFileDurability = PlatformSnapshotFileDurability,
) {
    val parent = requireReplacementParent(target)
    requireSameReplacementDirectory(source, parent)
    requireRegularReplacementSource(source)
    requireRegularReplacementTargetOrMissing(target)
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        if (!source.renameTo(target)) throw IOException("atomic replacement failed")
    }
    durability.syncDirectory(parent)
}

internal fun requireReplacementParent(target: File): File =
    target.parentFile ?: throw IOException("replacement parent dir missing")

internal fun requireSameReplacementDirectory(source: File, parent: File) {
    if (source.parentFile?.canonicalFile != parent.canonicalFile) {
        throw IOException("atomic replacement requires one directory")
    }
}

internal fun requireRegularReplacementSource(source: File) {
    if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw IOException("replacement source is not a regular file")
    }
}

internal fun requireRegularReplacementTargetOrMissing(target: File) {
    if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) &&
        !Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)
    ) {
        throw IOException("replacement target is not a regular file")
    }
}

internal fun copyFile(source: File, target: File) {
    if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw IOException("replacement source is missing or not a regular file")
    }
    source.inputStream().use { input ->
        FileOutputStream(target, false).use { output -> input.copyTo(output) }
    }
}

internal fun checkStorageCapacity(
    storageCapacity: RestoreStorageCapacity,
    allocationPath: File,
    vararg sizes: Long,
): BackupResult<Unit> {
    val available = runCatching {
        storageCapacity.getAllocatableBytes(allocationPath)
    }.getOrElse { error ->
        return BackupResult.Failure(BackupError.StorageCapacityUnavailable(error))
    }
    if (available < 0L || sizes.any { it < 0L }) {
        return BackupResult.Failure(
            BackupError.StorageCapacityUnavailable(
                IllegalArgumentException("negative storage capacity or file size"),
            ),
        )
    }
    val required = runCatching {
        sizes.fold(DatabaseSnapshotProviderImpl.CAPACITY_MARGIN_BYTES, Math::addExact)
    }.getOrElse {
        return BackupResult.Failure(
            BackupError.InsufficientLocalStorage(
                requiredBytes = Long.MAX_VALUE,
                availableBytes = available,
            ),
        )
    }
    return if (available >= required) {
        BackupResult.Success(Unit)
    } else {
        BackupResult.Failure(
            BackupError.InsufficientLocalStorage(
                requiredBytes = required,
                availableBytes = available,
            ),
        )
    }
}

internal inline fun <T> backupIoResult(block: () -> T): BackupResult<T> =
    runCatching(block).fold(
        onSuccess = { BackupResult.Success(it) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            BackupResult.Failure(BackupError.Io(error))
        },
    )

internal inline fun <T> corruptedBackupResult(
    fallback: String,
    block: () -> T,
): BackupResult<T> = runCatching(block).fold(
    onSuccess = { BackupResult.Success(it) },
    onFailure = { error ->
        if (error is CancellationException) throw error
        BackupResult.Failure(
            BackupError.CorruptedBackup(reason = error.message ?: fallback),
        )
    },
)

internal fun missingAssetFailure(label: String): BackupResult.Failure =
    BackupResult.Failure(BackupError.CorruptedBackup(reason = "$label is missing"))

internal fun requireDirectory(directory: File) {
    if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
        throw IOException("directory unavailable: ${directory.name}")
    }
}
