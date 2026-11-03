// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.stslex.workeeper.app.ios

import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpochSource
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.posix.EEXIST
import platform.posix.EINTR
import platform.posix.ENOENT
import platform.posix.O_CREAT
import platform.posix.O_DIRECTORY
import platform.posix.O_EXCL
import platform.posix.O_NOFOLLOW
import platform.posix.O_NONBLOCK
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.close
import platform.posix.errno
import platform.posix.fstat
import platform.posix.fsync
import platform.posix.linkat
import platform.posix.mkdirat
import platform.posix.open
import platform.posix.openat
import platform.posix.read
import platform.posix.stat
import platform.posix.unlinkat
import platform.posix.write
import kotlin.uuid.Uuid

/** Installation-owned storage, independent of graph generations and the backed-up database. */
fun buildIosInstallEpochSource(): InstallEpochSource = IosInstallEpochSource()

internal class IosInstallEpochSource(
    private val applicationSupport: () -> NSURL = ::installEpochApplicationSupport,
    private val markExcluded: (String) -> Unit = ::excludeInstallEpochDirectory,
    private val verifyExcluded: (String) -> Boolean = ::installEpochDirectoryIsExcluded,
    private val ioBoundary: () -> Unit = {},
) : InstallEpochSource {

    override suspend fun installEpoch(): InstallEpoch = withContext(Dispatchers.IO) {
        check(!NSThread.isMainThread) { "Installation epoch I/O must run off Main" }
        ioBoundary()
        val context = currentCoroutineContext()
        context.ensureActive()
        val root = applicationSupport()
        require(root.isFileURL()) { "Installation root must be a file URL" }
        val path = checkNotNull(root.path)
        check(NSFileManager.defaultManager.createDirectoryAtURL(root, true, null, null)) {
            "Cannot establish Application Support at $path"
        }
        installEpochDescriptor(open(path, O_RDONLY or O_DIRECTORY or O_NOFOLLOW), "open root", path) { parent ->
            if (mkdirat(parent, INSTALLATION_DIRECTORY, DIRECTORY_MODE) != 0 && errno != EEXIST) {
                installEpochIoFailure("mkdirat", path)
            }
            val directory = "$path/$INSTALLATION_DIRECTORY"
            installEpochDescriptor(
                openat(parent, INSTALLATION_DIRECTORY, O_RDONLY or O_DIRECTORY or O_NOFOLLOW),
                "open installation directory",
                directory,
            ) { descriptor ->
                checkInstallEpochIo(fsync(parent), "fsync root", path)
                markExcluded(directory)
                check(verifyExcluded(directory)) { "Backup exclusion readback failed at $directory" }
                context.ensureActive()
                readIosInstallEpoch(descriptor, directory) { context.ensureActive() }
                    ?: publishIosInstallEpoch(descriptor, directory, randomInstallEpoch()) {
                        context.ensureActive()
                    }
            }
        }
    }
}

internal const val INSTALLATION_DIRECTORY = "WorkeeperInstallation"
internal const val INSTALL_EPOCH_FILE = "install_epoch"
private const val EPOCH_LENGTH = 36
private const val DIRECTORY_MODE: UShort = 0x1c0u
private const val FILE_MODE = 0x180

private fun randomInstallEpoch() = InstallEpoch(RestoreOwnerId(Uuid.random().toString()))

private fun installEpochApplicationSupport(): NSURL = checkNotNull(
    NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        .firstOrNull() as? NSURL,
) { "Application Support is unavailable for the installation epoch" }

internal fun excludeInstallEpochDirectory(directory: String): Unit = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    check(NSURL.fileURLWithPath(directory, true).setResourceValue(true, NSURLIsExcludedFromBackupKey, error.ptr)) {
        "Cannot exclude installation directory $directory: ${error.value?.localizedDescription}"
    }
}

internal fun installEpochDirectoryIsExcluded(directory: String): Boolean = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    // A fresh URL must query the filesystem rather than reuse the setter's resource cache.
    val values = NSURL.fileURLWithPath(directory, true)
        .resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error.ptr)
    checkNotNull(values) { "Cannot read backup exclusion at $directory: ${error.value?.localizedDescription}" }
    (values[NSURLIsExcludedFromBackupKey] as? NSNumber)?.boolValue == true
}

internal fun publishIosInstallEpoch(
    descriptor: Int,
    directory: String,
    candidate: InstallEpoch,
    checkCancellation: () -> Unit = {},
): InstallEpoch {
    val temporary = ".install_epoch-${Uuid.random()}.tmp"
    val file = openat(descriptor, temporary, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW, FILE_MODE)
    if (file < 0) installEpochIoFailure("open exclusive candidate", directory)
    completeInstallEpochAttempt({
        installEpochDescriptor(file, "open candidate", directory) { output ->
            writeInstallEpochCandidate(output, directory, candidate, checkCancellation)
            checkInstallEpochIo(fsync(output), "fsync candidate", directory)
        }
        checkCancellation()
        // Exclusive publication: an incumbent must never be replaced, even across source instances.
        if (linkat(descriptor, temporary, descriptor, INSTALL_EPOCH_FILE, 0) != 0 && errno != EEXIST) {
            installEpochIoFailure("linkat exclusive publication", directory)
        }
        checkInstallEpochIo(fsync(descriptor), "fsync publication directory", directory)
        checkCancellation()
    }) {
        checkInstallEpochIo(unlinkat(descriptor, temporary, 0), "unlink owned candidate", directory)
        checkInstallEpochIo(fsync(descriptor), "fsync candidate cleanup", directory)
    }
    return checkNotNull(readIosInstallEpoch(descriptor, directory, checkCancellation)) {
        "Published installation epoch disappeared at $directory"
    }
}

private fun writeInstallEpochCandidate(
    output: Int,
    directory: String,
    candidate: InstallEpoch,
    checkCancellation: () -> Unit,
) {
    val bytes = candidate.toString().encodeToByteArray()
    bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            checkCancellation()
            val written = retryInstallEpochIo {
                write(output, pinned.addressOf(offset), (bytes.size - offset).toULong())
            }
            if (written <= 0) installEpochIoFailure("write candidate", directory)
            offset += written.toInt()
        }
    }
}

private fun readIosInstallEpoch(descriptor: Int, directory: String, checkCancellation: () -> Unit): InstallEpoch? {
    val file = openat(descriptor, INSTALL_EPOCH_FILE, O_RDONLY or O_NOFOLLOW or O_NONBLOCK)
    if (file < 0) {
        if (errno == ENOENT) return null
        installEpochIoFailure("open epoch", directory)
    }
    return installEpochDescriptor(file, "open epoch", directory) { input ->
        memScoped {
            val metadata = alloc<stat>()
            checkInstallEpochIo(fstat(input, metadata.ptr), "fstat epoch", directory)
            check(metadata.st_mode.toInt() and S_IFMT == S_IFREG && metadata.st_size == EPOCH_LENGTH.toLong()) {
                "Invalid installation epoch storage at $directory"
            }
        }
        val bytes = ByteArray(EPOCH_LENGTH + 1)
        var offset = 0
        bytes.usePinned { pinned ->
            while (offset < bytes.size) {
                checkCancellation()
                val count = retryInstallEpochIo {
                    read(input, pinned.addressOf(offset), (bytes.size - offset).toULong())
                }
                if (count < 0) installEpochIoFailure("read epoch", directory)
                if (count == 0L) break
                offset += count.toInt()
            }
        }
        check(offset == EPOCH_LENGTH) { "Invalid installation epoch length at $directory" }
        val value = bytes.copyOf(offset).decodeToString(throwOnInvalidSequence = true)
        InstallEpoch(RestoreOwnerId(value))
    }
}

internal fun <T> installEpochDescriptor(
    descriptor: Int,
    operation: String,
    path: String,
    block: (Int) -> T,
): T {
    if (descriptor < 0) installEpochIoFailure(operation, path)
    return completeInstallEpochAttempt({ block(descriptor) }) {
        checkInstallEpochIo(close(descriptor), "close", path)
    }
}

private fun <T> completeInstallEpochAttempt(operation: () -> T, cleanup: () -> Unit): T {
    val result = runCatching(operation)
    val cleaned = runCatching(cleanup)
    val failure = result.exceptionOrNull()
    if (failure != null) cleaned.exceptionOrNull()?.let(failure::addSuppressed) else cleaned.getOrThrow()
    return result.getOrThrow()
}

private fun retryInstallEpochIo(operation: () -> Long): Long {
    var result: Long
    do {
        result = operation()
    } while (result < 0 && errno == EINTR)
    return result
}

private fun checkInstallEpochIo(result: Int, operation: String, path: String) {
    if (result != 0) installEpochIoFailure(operation, path)
}

private fun installEpochIoFailure(operation: String, path: String): Nothing =
    error("$operation failed at $path: errno=$errno")
