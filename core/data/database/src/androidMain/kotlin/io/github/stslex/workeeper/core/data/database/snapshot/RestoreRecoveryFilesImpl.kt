// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreAttempt
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreGarbageCollectionReport
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreProtocolState
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreTerminal
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Database-module additions needed only while retiring released positional recovery files. */
interface RestoreRecoveryFileStore : RestoreRecoveryFiles {

    suspend fun deleteLegacyPreRestore(): Boolean

    suspend fun deleteRecoveryExport(): Boolean
}

/**
 * Installation-scoped owner of restore durability files. Every protocol path is derived from a
 * validated opaque owner below `noBackupFilesDir/restore-recovery`; persisted paths are never read.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<RestoreRecoveryFiles>())
@ContributesBinding(AppScope::class, binding = binding<RestoreRecoveryFileStore>())
public class RestoreRecoveryFilesImpl internal constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val durability: SnapshotFileDurability,
) : RestoreRecoveryFileStore {

    @Inject
    public constructor(
        context: Context,
        @IODispatcher dispatcher: CoroutineDispatcher,
    ) : this(context, dispatcher, PlatformSnapshotFileDurability)

    override suspend fun installEpoch(): InstallEpoch = withContext(dispatcher) {
        protocolMutex.withLock {
            val root = requireRecoveryRoot(context, durability)
            val target = File(root, INSTALL_EPOCH_NAME)
            if (target.isRegularFileWithoutFollowingLinks()) {
                return@withLock readDurableEpoch(target, durability)
            }

            val partial = uniqueCreatingFile(root, INSTALL_EPOCH_NAME)
            requirePartialCanBeReplaced(partial)
            val generated = InstallEpoch(RestoreOwnerId(UUID.randomUUID().toString()))
            val publishError = runCatching {
                writePartial(partial, generated.toString().toByteArray(Charsets.US_ASCII))
                publishImmutableNoReplace(partial, target, durability)
            }.exceptionOrNull()
            if (publishError != null) {
                partial.delete()
                if (target.isRegularFileWithoutFollowingLinks()) {
                    return@withLock readDurableEpoch(target, durability)
                }
                throw IOException("installation epoch creation failed", publishError)
            }
            readEpoch(target)
        }
    }

    override suspend fun publishUndo(source: File, ref: UndoRef): BackupResult<File> =
        publishImmutable(
            source = source,
            targetName = undoName(ref),
        )

    override suspend fun publishRestoreSource(
        source: File,
        ref: RestoreSourceRef,
    ): BackupResult<File> {
        val published = publishImmutable(
            source = source,
            targetName = restoreSourceName(ref),
        )
        if (published is BackupResult.Success) {
            consumeCallerCacheSource(context, source, published.data)
        }
        return published
    }

    override fun undoFile(ref: UndoRef): File? =
        existingProtocolFile(context, undoName(ref))

    override fun restoreSourceFile(ref: RestoreSourceRef): File? =
        existingProtocolFile(context, restoreSourceName(ref))

    override fun legacyPreRestoreFile(): File? =
        File(context.cacheDir, LEGACY_PRE_RESTORE_NAME)
            .takeIf { it.isRegularFileWithoutFollowingLinks() }

    override suspend fun migrateLegacyUndo(ref: UndoRef): BackupResult<File> =
        withContext(dispatcher) {
            protocolMutex.withLock {
                val root = runCatching { requireRecoveryRoot(context, durability) }
                    .getOrElse { return@withLock recoveryFailure(it) }
                val targetName = undoName(ref)
                val target = File(root, targetName)
                if (target.isRegularFileWithoutFollowingLinks()) {
                    return@withLock backupIoResult {
                        durability.syncFile(target)
                        durability.syncDirectory(root)
                        target
                    }
                }
                if (target.existsWithoutFollowingLinks()) {
                    return@withLock recoveryFailure(
                        IOException("legacy undo target is not a regular file"),
                    )
                }
                val source = legacyPreRestoreFile()
                    ?: return@withLock BackupResult.Failure(
                        BackupError.CorruptedBackup(reason = "legacy undo is missing"),
                    )
                publishImmutableLocked(root, source, targetName)
            }
        }

    override suspend fun deleteLegacyPreRestore(): Boolean = withContext(dispatcher) {
        protocolMutex.withLock { deleteExact(File(context.cacheDir, LEGACY_PRE_RESTORE_NAME)) }
    }

    override suspend fun deleteUndo(ref: UndoRef): Boolean = withContext(dispatcher) {
        protocolMutex.withLock { deleteExact(protocolFile(context, undoName(ref))) }
    }

    override suspend fun deleteRestoreSource(ref: RestoreSourceRef): Boolean =
        withContext(dispatcher) {
            protocolMutex.withLock { deleteExact(protocolFile(context, restoreSourceName(ref))) }
        }

    override suspend fun publishRecoveryExport(source: File): BackupResult<File> =
        withContext(dispatcher) {
            protocolMutex.withLock {
                val root = runCatching { requireRecoveryRoot(context, durability) }
                    .getOrElse { return@withLock recoveryFailure(it) }
                val target = File(root, RECOVERY_EXPORT_NAME)
                if (source.sameFileAs(target) && target.isRegularFileWithoutFollowingLinks()) {
                    return@withLock BackupResult.Success(target)
                }
                if (target.existsWithoutFollowingLinks() &&
                    !target.isRegularFileWithoutFollowingLinks()
                ) {
                    return@withLock recoveryFailure(
                        IOException("recovery export target is not a regular file"),
                    )
                }
                val partial = File(root, RECOVERY_EXPORT_CREATING_NAME)
                val written = backupIoResult {
                    requirePartialCanBeReplaced(partial)
                    copyPartial(source, partial)
                    durability.syncFile(partial)
                    moveSyncedFileReplacing(partial, target, durability)
                    target
                }
                if (written is BackupResult.Failure) {
                    partial.delete()
                }
                written
            }
        }

    override fun recoveryExportFile(): File? = existingProtocolFile(context, RECOVERY_EXPORT_NAME)

    override suspend fun deleteRecoveryExport(): Boolean = withContext(dispatcher) {
        protocolMutex.withLock { deleteExact(protocolFile(context, RECOVERY_EXPORT_NAME)) }
    }

    override suspend fun createShareCopy(
        source: File,
        fileName: String,
    ): BackupResult<File> = withContext(dispatcher) {
        protocolMutex.withLock {
            if (!SHARE_FILE_PATTERN.matches(fileName) || ".." in fileName) {
                return@withLock BackupResult.Failure(
                    BackupError.Io(IOException("invalid recovery share filename")),
                )
            }
            val durableExport = recoveryExportFile()
            if (durableExport == null || !source.sameFileAs(durableExport)) {
                return@withLock BackupResult.Failure(
                    BackupError.CorruptedBackup(reason = "durable recovery export is missing"),
                )
            }
            val shareRoot = File(context.cacheDir, RECOVERY_SHARE_DIR)
            if (!ensureDirectoryWithoutFollowingLinks(shareRoot)) {
                return@withLock recoveryFailure(IOException("recovery share directory unavailable"))
            }
            val target = File(shareRoot, fileName)
            if (target.existsWithoutFollowingLinks() &&
                !target.isRegularFileWithoutFollowingLinks()
            ) {
                return@withLock recoveryFailure(
                    IOException("recovery share target is not a regular file"),
                )
            }
            val partial = File(shareRoot, "$fileName$CREATING_SUFFIX")
            val written = backupIoResult {
                requirePartialCanBeReplaced(partial)
                copyPartial(durableExport, partial)
                durability.syncFile(partial)
                moveSyncedFileReplacing(partial, target, durability)
                target
            }
            if (written is BackupResult.Failure) {
                partial.delete()
            }
            written
        }
    }

    override suspend fun sweep(
        state: RestoreProtocolState,
    ): RestoreGarbageCollectionReport = withContext(dispatcher) {
        protocolMutex.withLock {
            val root = runCatching { requireRecoveryRoot(context, durability) }.getOrElse {
                return@withLock RestoreGarbageCollectionReport(
                    deletedNames = emptyList(),
                    retryNames = listOf(RECOVERY_ROOT_NAME),
                )
            }
            val protected = protectedRecoveryNames(state)
            val deleted = mutableListOf<String>()
            val retry = mutableListOf<String>()
            root.listFiles().orEmpty()
                .filter { file -> isStrictProtocolOwnedName(file.name) && file.name !in protected }
                .sortedBy(File::getName)
                .forEach { file ->
                    if (file.delete()) deleted += file.name else retry += file.name
                }
            RestoreGarbageCollectionReport(
                deletedNames = deleted,
                retryNames = retry,
            )
        }
    }

    private suspend fun publishImmutable(
        source: File,
        targetName: String,
    ): BackupResult<File> = withContext(dispatcher) {
        protocolMutex.withLock {
            val root = runCatching { requireRecoveryRoot(context, durability) }
                .getOrElse { return@withLock recoveryFailure(it) }
            publishImmutableLocked(root, source, targetName)
        }
    }

    private fun publishImmutableLocked(
        root: File,
        source: File,
        targetName: String,
    ): BackupResult<File> {
        val target = File(root, targetName)
        val partial = uniqueCreatingFile(root, targetName)
        val written = backupIoResult {
            requirePartialCanBeReplaced(partial)
            copyPartial(source, partial)
            publishImmutableNoReplace(partial, target, durability)
            target
        }
        if (written is BackupResult.Failure) {
            partial.delete()
        }
        return written
    }
}

internal fun protectedRecoveryNames(state: RestoreProtocolState): Set<String> = buildSet {
    add(INSTALL_EPOCH_NAME)
    add(RECOVERY_EXPORT_NAME)
    add(PUBLICATION_LOCK_NAME)
    when (val attempt = state.attempt) {
        is RestoreAttempt.Restore -> {
            attempt.undoRef?.let { protectUndo(it) }
            attempt.sourceRef?.let { protectRestoreSource(it) }
        }

        is RestoreAttempt.Rollback -> protectUndo(attempt.sourceRef)
        null -> Unit
    }
    state.activeUndo?.ref?.let { protectUndo(it) }
    val terminal = state.terminalOutbox
    if (terminal is RestoreTerminal.RestoreSucceeded && terminal.previousVersionAvailable) {
        protectUndo(UndoRef(terminal.owner))
    }
}

private fun MutableSet<String>.protectUndo(ref: UndoRef) {
    val final = undoName(ref)
    add(final)
    add("$final$CREATING_SUFFIX")
}

private fun MutableSet<String>.protectRestoreSource(ref: RestoreSourceRef) {
    val final = restoreSourceName(ref)
    add(final)
    add("$final$CREATING_SUFFIX")
}

internal fun requireRecoveryRoot(
    context: Context,
    durability: SnapshotFileDurability,
): File {
    val root = File(context.noBackupFilesDir, RECOVERY_ROOT_NAME)
    val existed = root.existsWithoutFollowingLinks()
    if (!ensureDirectoryWithoutFollowingLinks(root)) {
        throw IOException("restore recovery root unavailable")
    }
    if (!existed) durability.syncDirectory(context.noBackupFilesDir)
    return root
}

internal fun existingProtocolFile(context: Context, name: String): File? =
    protocolFile(context, name).takeIf { it.isRegularFileWithoutFollowingLinks() }

internal fun protocolFile(context: Context, name: String): File =
    File(context.noBackupFilesDir, "$RECOVERY_ROOT_NAME${File.separator}$name")

internal fun readEpoch(file: File): InstallEpoch = runCatching {
    InstallEpoch(RestoreOwnerId(file.readText(Charsets.US_ASCII).trim()))
}.getOrElse { error ->
    throw IOException("installation epoch is invalid", error)
}

internal fun readDurableEpoch(
    file: File,
    durability: SnapshotFileDurability,
): InstallEpoch {
    durability.syncFile(file)
    durability.syncDirectory(file.parentFile ?: throw IOException("epoch parent dir missing"))
    return readEpoch(file)
}

internal fun requirePartialCanBeReplaced(partial: File) {
    if (partial.existsWithoutFollowingLinks() && !partial.delete()) {
        throw IOException("partial recovery file cannot be replaced: ${partial.name}")
    }
}

internal fun copyPartial(source: File, target: File) {
    if (!source.isRegularFileWithoutFollowingLinks()) {
        throw IOException("recovery source is missing or not a regular file")
    }
    source.inputStream().use { input ->
        FileOutputStream(target, false).use { output ->
            input.copyTo(output)
        }
    }
}

internal fun writePartial(target: File, bytes: ByteArray) {
    FileOutputStream(target, false).use { output ->
        output.write(bytes)
    }
}

internal fun uniqueCreatingFile(root: File, targetName: String): File =
    File(root, "$targetName.${UUID.randomUUID()}$CREATING_SUFFIX")

/**
 * Publishes a fully-synced partial while one permanent root lock serializes every app process.
 * The locked absence check makes the same-directory atomic move no-replace for protocol writers.
 */
internal fun publishImmutableNoReplace(
    source: File,
    target: File,
    durability: SnapshotFileDurability = PlatformSnapshotFileDurability,
) {
    durability.syncFile(source)
    val parent = requireImmutableParent(target)
    synchronized(immutablePublicationProcessLock) {
        withImmutablePublicationFileLock(parent) {
            // Persist the completed partial before its final name can become visible.
            durability.syncDirectory(parent)
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                failImmutablePublish(target, "already exists")
            }
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                failImmutablePublish(target, "already exists")
            } catch (error: AtomicMoveNotSupportedException) {
                failImmutablePublish(target, "atomic publication unsupported", error)
            } catch (error: IOException) {
                failImmutablePublish(target, "publication failed", error)
            }
            durability.syncDirectory(parent)
        }
    }
}

internal inline fun <T> withImmutablePublicationFileLock(
    root: File,
    block: () -> T,
): T {
    val lockFile = File(root, PUBLICATION_LOCK_NAME)
    if (lockFile.existsWithoutFollowingLinks() && !lockFile.isRegularFileWithoutFollowingLinks()) {
        throw IOException("immutable publication lock is not a regular file")
    }
    return FileChannel.open(
        lockFile.toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
    ).use { channel ->
        channel.lock().use { block() }
    }
}

internal fun requireImmutableParent(target: File): File =
    target.parentFile ?: failImmutablePublish(target, "parent dir missing")

internal fun failImmutablePublish(
    target: File,
    reason: String,
    cause: Throwable? = null,
): Nothing = throw IOException("immutable recovery target ${target.name}: $reason", cause)

internal fun consumeCallerCacheSource(context: Context, source: File, published: File) {
    val sourcePath = runCatching { source.canonicalFile }.getOrNull() ?: return
    val cachePath = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return
    if (sourcePath.sameFileAs(published)) return
    val insideCache = sourcePath.path.startsWith(cachePath.path + File.separator)
    if (insideCache && sourcePath.isFile) sourcePath.delete()
}

internal fun deleteExact(file: File): Boolean =
    !file.existsWithoutFollowingLinks() || file.delete()

internal fun ensureDirectoryWithoutFollowingLinks(directory: File): Boolean {
    if (directory.existsWithoutFollowingLinks()) return directory.isDirectoryWithoutFollowingLinks()
    return directory.mkdirs() && directory.isDirectoryWithoutFollowingLinks()
}

internal fun File.existsWithoutFollowingLinks(): Boolean =
    runCatching { Files.exists(toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
        .getOrDefault(false)

private fun File.isDirectoryWithoutFollowingLinks(): Boolean =
    runCatching { Files.isDirectory(toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
        .getOrDefault(false)

internal fun File.isRegularFileWithoutFollowingLinks(): Boolean =
    runCatching { Files.isRegularFile(toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
        .getOrDefault(false)

internal fun File.sameFileAs(other: File): Boolean =
    runCatching { canonicalFile == other.canonicalFile }.getOrDefault(false)

internal fun recoveryFailure(error: Throwable): BackupResult.Failure =
    BackupResult.Failure(BackupError.Io(error))

internal fun undoName(ref: UndoRef): String = "undo_${ref.owner}.db"

internal fun restoreSourceName(ref: RestoreSourceRef): String =
    "staged_restore_${ref.owner}.db"

internal fun isStrictProtocolOwnedName(name: String): Boolean =
    name == PUBLICATION_LOCK_NAME ||
        name == INSTALL_EPOCH_CREATING_NAME ||
        INSTALL_EPOCH_PARTIAL_PATTERN.matches(name) ||
        name == RECOVERY_EXPORT_NAME ||
        name == RECOVERY_EXPORT_CREATING_NAME ||
        UNDO_FILE_PATTERN.matches(name) ||
        RESTORE_SOURCE_FILE_PATTERN.matches(name)

private const val RECOVERY_ROOT_NAME = "restore-recovery"
private const val PUBLICATION_LOCK_NAME = ".publication.lock"
private const val INSTALL_EPOCH_NAME = "install_epoch"
private const val INSTALL_EPOCH_CREATING_NAME = "install_epoch.creating"
private const val LEGACY_PRE_RESTORE_NAME = "pre_restore_backup.db"
private const val RECOVERY_EXPORT_NAME = "recovery_export.db"
private const val RECOVERY_EXPORT_CREATING_NAME = "recovery_export.db.creating"
private const val RECOVERY_SHARE_DIR = "recovery_share"
private const val CREATING_SUFFIX = ".creating"
private const val OWNER_PATTERN =
    "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
private const val PARTIAL_NONCE_PATTERN = OWNER_PATTERN
private val INSTALL_EPOCH_PARTIAL_PATTERN =
    Regex("install_epoch\\.$PARTIAL_NONCE_PATTERN\\.creating")
private val UNDO_FILE_PATTERN =
    Regex("undo_$OWNER_PATTERN\\.db(?:\\.creating|\\.$PARTIAL_NONCE_PATTERN\\.creating)?")
private val RESTORE_SOURCE_FILE_PATTERN =
    Regex(
        "staged_restore_$OWNER_PATTERN\\.db" +
            "(?:\\.creating|\\.$PARTIAL_NONCE_PATTERN\\.creating)?",
    )
private val SHARE_FILE_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
private val protocolMutex = Mutex()
private val immutablePublicationProcessLock = Any()
