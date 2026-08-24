// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * O(1) truncation check over the 100-byte SQLite header. Magic and a `PRAGMA user_version` peek
 * both read page 1 only, so both pass on a file truncated in its TAIL; the in-header page count
 * against the file length is what catches it.
 */
internal object SqliteHeaderCheck {

    fun verifyComplete(source: File): BackupResult<Unit> = try {
        val header = readHeader(source)
        if (header == null) {
            BackupResult.Failure(BackupError.CorruptedBackup(reason = "truncated SQLite header"))
        } else {
            checkLength(ByteBuffer.wrap(header), source.length())
        }
    } catch (e: IOException) {
        BackupResult.Failure(
            BackupError.CorruptedBackup(reason = e.message ?: "header read failed"),
        )
    }

    /** Fills the header or answers null: a short read on a healthy file is not corruption. */
    private fun readHeader(source: File): ByteArray? {
        val header = ByteArray(HEADER_BYTES)
        var filled = 0
        source.inputStream().use { stream ->
            while (filled < HEADER_BYTES) {
                val read = stream.read(header, filled, HEADER_BYTES - filled)
                if (read < 0) break
                filled += read
            }
        }
        return header.takeIf { filled == HEADER_BYTES }
    }

    private fun checkLength(header: ByteBuffer, actual: Long): BackupResult<Unit> {
        val pageSize = readPageSize(header)
            ?: return BackupResult.Failure(
                BackupError.CorruptedBackup(reason = "illegal SQLite page size"),
            )
        // The in-header page count is authoritative only when the two counters agree.
        val counted = header.getInt(CHANGE_COUNTER_OFFSET) == header.getInt(VALID_FOR_OFFSET)
        val pages = header.getInt(PAGE_COUNT_OFFSET).toLong() and PAGE_COUNT_MASK
        val expected = if (counted && pages > 0) pageSize * pages else pageSize
        return if (actual >= expected) {
            BackupResult.Success(Unit)
        } else {
            BackupResult.Failure(
                BackupError.CorruptedBackup(
                    reason = "database truncated: $actual bytes < $expected",
                ),
            )
        }
    }

    /** Bytes 16-17; `1` is the 65536 marker. Anything outside the legal set is corruption. */
    private fun readPageSize(header: ByteBuffer): Long? {
        val raw = header.getShort(PAGE_SIZE_OFFSET).toInt() and UNSIGNED_SHORT
        if (raw == LARGE_PAGE_MARKER) return LARGE_PAGE_SIZE
        val legal = raw >= MIN_PAGE_SIZE && raw.and(raw - 1) == 0
        return raw.toLong().takeIf { legal }
    }

    private const val HEADER_BYTES = 100
    private const val PAGE_SIZE_OFFSET = 16
    private const val CHANGE_COUNTER_OFFSET = 24
    private const val PAGE_COUNT_OFFSET = 28
    private const val VALID_FOR_OFFSET = 92
    private const val UNSIGNED_SHORT = 0xFFFF
    private const val LARGE_PAGE_MARKER = 1
    private const val LARGE_PAGE_SIZE = 65_536L
    private const val MIN_PAGE_SIZE = 512
    private const val PAGE_COUNT_MASK = 0xFFFFFFFFL
}
