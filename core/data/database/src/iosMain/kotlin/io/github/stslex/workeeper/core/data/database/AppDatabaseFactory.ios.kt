// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** Creates a caller-owned database. Call off Main and close the returned instance when finished. */
fun buildIosAppDatabase(): AppDatabase = buildIosAppDatabase(iosApplicationSupportDirectory())

internal fun iosApplicationSupportDirectory(): NSURL {
    check(!NSThread.isMainThread) { "Resolve the database directory off Main" }
    return checkNotNull(
        NSFileManager.defaultManager
            .URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL,
    ) { "Application Support is unavailable" }
}

internal fun buildIosAppDatabase(applicationSupportRoot: NSURL): AppDatabase {
    check(!NSThread.isMainThread) { "Build the database off Main" }
    require(applicationSupportRoot.isFileURL()) { "Application Support must be a file URL" }
    val directory = checkNotNull(applicationSupportRoot.URLByAppendingPathComponent("Workeeper", true))
    createDatabaseDirectory(directory)
    val file = checkNotNull(directory.URLByAppendingPathComponent(AppDatabase.NAME, false))
    val path = checkNotNull(file.path) { "Database file path is unavailable" }
    var allocated: AppDatabase? = null
    return runCatching {
        buildAppDatabase(
            Room.databaseBuilder<AppDatabase>(
                name = path,
                factory = { AppDatabaseConstructor.initialize().also { allocated = it } },
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO),
        )
    }.onFailure { failure ->
        runCatching { allocated?.close() }.exceptionOrNull()?.let(failure::addSuppressed)
    }.getOrThrow()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createDatabaseDirectory(directory: NSURL) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    error.value = null
    check(
        NSFileManager.defaultManager.createDirectoryAtURL(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = error.ptr,
        ),
    ) { "Cannot create database directory: ${error.value?.localizedDescription ?: directory.path}" }
}
