// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.stslex.workeeper.core.data.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.getenv
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun iosDatabaseTest(block: suspend (IosDatabaseTestSandbox) -> Unit): Unit = runBlocking(Dispatchers.IO) {
    val sandbox = IosDatabaseTestSandbox()
    try {
        block(sandbox)
    } finally {
        check(NSFileManager.defaultManager.removeItemAtPath(sandbox.owned, null))
    }
}

internal class IosDatabaseTestSandbox {

    val owned = NSTemporaryDirectory() + "workeeper-room-test-${NSUUID().UUIDString}"
    val support = "$owned/Application Support"
    val root: NSURL = NSURL.fileURLWithPath(support, true)
    val file = "$support/Workeeper/${AppDatabase.NAME}"

    init {
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(owned))
        check(NSFileManager.defaultManager.createDirectoryAtPath(owned, false, null, null))
    }

    suspend fun <T> open(block: suspend (AppDatabase) -> T): T {
        val database = buildIosAppDatabase(root)
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    fun raw(block: (SQLiteConnection) -> Unit) {
        BundledSQLiteDriver().open(file).use(block)
    }

    suspend fun historical(version: Int, seed: (SQLiteConnection) -> Unit) {
        createTestDirectory("$support/Workeeper")
        val schemas = "$owned/schemas"
        val directory = "$schemas/io.github.stslex.workeeper.core.data.database.AppDatabase"
        createTestDirectory(directory)
        writeTestFile("$directory/$version.json", IosHistoricalSchemas.exports.getValue(version))
        val helper = MigrationTestHelper(
            schemaDirectoryPath = schemas,
            fileName = file,
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
            databaseFactory = AppDatabaseConstructor::initialize,
        )
        try {
            helper.createDatabase(version).use(seed)
        } finally {
            helper.finished()
        }
    }

    fun observeBeforeOpen(version: Int, schemaVersion: Int = version) {
        raw { connection ->
            assertEquals(version.toLong(), connection.readLong("PRAGMA user_version"))
            val identity = connection.readText("SELECT identity_hash FROM room_master_table WHERE id = 42")
            assertEquals(IosHistoricalSchemas.identities.getValue(schemaVersion), identity)
            assertEquals(SENTINEL_NAME, connection.readText("SELECT name FROM tag_table WHERE uuid = '$SENTINEL_UUID'"))
            println(
                "PRE-OPEN version=$version identity=$identity sentinel=$SENTINEL_NAME " +
                    "schemaBlob=${IosHistoricalSchemas.blobs.getValue(schemaVersion)}",
            )
        }
        val base = checkNotNull(testEnvironment("WORKEEPER_DATABASE_TEST_ARTIFACTS"))
        check(base.endsWith("/core/data/database/build/native-fixtures"))
        val artifact = "$base/v$version-${NSUUID().UUIDString}"
        createTestDirectory(artifact)
        check(NSFileManager.defaultManager.copyItemAtPath(file, "$artifact/pre-open.db", null))
        writeTestFile("$artifact/schema.json", IosHistoricalSchemas.exports.getValue(schemaVersion))
        writeTestFile("$artifact/source-blob.txt", IosHistoricalSchemas.blobs.getValue(schemaVersion))
        println("PRE-OPEN fixture=$artifact/pre-open.db")
    }

    companion object {
        const val SENTINEL_UUID = "11111111-1111-1111-1111-111111111111"
        const val SENTINEL_NAME = "Persisted iOS tag"
        const val CURRENT_IDENTITY = "78d89911aae7f1a3fd5a4bb4d15b84c9"
    }
}

internal fun SQLiteConnection.readLong(sql: String): Long = prepare(sql).use { statement ->
    assertTrue(statement.step())
    statement.getLong(0)
}

internal fun SQLiteConnection.readText(sql: String): String = prepare(sql).use { statement ->
    assertTrue(statement.step())
    statement.getText(0)
}

internal fun createTestDirectory(path: String) {
    check(NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null))
}

internal fun testEnvironment(name: String): String? = getenv(name)?.toKString()

internal fun writeTestFile(path: String, text: String) {
    val handle = checkNotNull(fopen(path, "wb"))
    try {
        val bytes = text.encodeToByteArray()
        bytes.usePinned { pinned ->
            assertEquals(bytes.size.toULong(), fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), handle))
        }
    } finally {
        check(fclose(handle) == 0)
    }
}

internal fun readTestFile(path: String): String {
    val handle = checkNotNull(fopen(path, "rb"))
    try {
        val bytes = ByteArray(4096)
        val count = bytes.usePinned { fread(it.addressOf(0), 1u, bytes.size.toULong(), handle) }
        return bytes.decodeToString(0, count.toInt())
    } finally {
        check(fclose(handle) == 0)
    }
}
