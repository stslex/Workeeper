// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.backup.api.error.BackupError
import io.github.stslex.workeeper.core.data.backup.api.result.BackupResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SqliteHeaderCheckTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var snapshot: File

    @BeforeEach
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.NAME)
        database = Room
            .databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .allowMainThreadQueries()
            .build()
        database.tagDao.insertAll(
            List(200) { index ->
                TagEntity(
                    uuid = Uuid.random(),
                    name = "header-characterization-$index-${"x".repeat(80)}",
                )
            },
        )
        database.close()
        val live = context.getDatabasePath(AppDatabase.NAME)
        snapshot = File(requireNotNull(live.parentFile), "header_characterization.db")
        live.copyTo(snapshot, overwrite = true)
        assertTrue(snapshot.length() > pageSize(snapshot), "fixture must contain several pages")
    }

    @AfterEach
    fun teardown() {
        database.close()
        context.deleteDatabase(AppDatabase.NAME)
        snapshot.delete()
    }

    @Test
    fun `real Workeeper snapshot with matching counters and a page count is complete`() {
        val header = header(snapshot)
        assertEquals(header.getInt(CHANGE_COUNTER_OFFSET), header.getInt(VALID_FOR_OFFSET))
        assertTrue(unsignedInt(header.getInt(PAGE_COUNT_OFFSET)) > 1L)

        assertEquals(BackupResult.Success(Unit), SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `matching counters reject a tail shorter than the authoritative page count`() {
        val declaredPages = unsignedInt(header(snapshot).getInt(PAGE_COUNT_OFFSET))
        RandomAccessFile(snapshot, "rw").use { file ->
            file.setLength(pageSize(snapshot) * (declaredPages - 1L))
        }

        assertCorrupted(SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `mismatched counters keep the complete real snapshot valid`() {
        mismatchCounters(snapshot)

        assertEquals(BackupResult.Success(Unit), SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `mismatched counters intentionally cannot reject a tail after page one`() {
        mismatchCounters(snapshot)
        RandomAccessFile(snapshot, "rw").use { file -> file.setLength(pageSize(snapshot)) }

        assertEquals(BackupResult.Success(Unit), SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `mismatched counters still reject truncation inside page one`() {
        mismatchCounters(snapshot)
        RandomAccessFile(snapshot, "rw").use { file -> file.setLength(pageSize(snapshot) - 1L) }

        assertCorrupted(SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `zero page count intentionally falls back to one complete page`() {
        writeHeaderInt(snapshot, PAGE_COUNT_OFFSET, 0)
        RandomAccessFile(snapshot, "rw").use { file -> file.setLength(pageSize(snapshot)) }

        assertEquals(BackupResult.Success(Unit), SqliteHeaderCheck.verifyComplete(snapshot))
    }

    @Test
    fun `zero page count rejects truncation inside its fallback first page`() {
        writeHeaderInt(snapshot, PAGE_COUNT_OFFSET, 0)
        RandomAccessFile(snapshot, "rw").use { file -> file.setLength(pageSize(snapshot) - 1L) }

        assertCorrupted(SqliteHeaderCheck.verifyComplete(snapshot))
    }

    private fun mismatchCounters(file: File) {
        val current = header(file).getInt(CHANGE_COUNTER_OFFSET)
        writeHeaderInt(file, VALID_FOR_OFFSET, current + 1)
    }

    private fun pageSize(file: File): Long {
        val raw = header(file).getShort(PAGE_SIZE_OFFSET).toInt() and 0xFFFF
        return if (raw == 1) 65_536L else raw.toLong()
    }

    private fun header(file: File): ByteBuffer =
        ByteBuffer.wrap(file.inputStream().use { it.readNBytes(HEADER_BYTES) })

    private fun writeHeaderInt(file: File, offset: Int, value: Int) {
        RandomAccessFile(file, "rw").use { random ->
            random.seek(offset.toLong())
            random.writeInt(value)
        }
    }

    private fun unsignedInt(value: Int): Long = value.toLong() and 0xFFFFFFFFL

    private fun assertCorrupted(result: BackupResult<Unit>) {
        assertTrue(
            result is BackupResult.Failure && result.error is BackupError.CorruptedBackup,
            "expected CorruptedBackup, got $result",
        )
    }

    private companion object {
        const val HEADER_BYTES = 100
        const val PAGE_SIZE_OFFSET = 16
        const val CHANGE_COUNTER_OFFSET = 24
        const val PAGE_COUNT_OFFSET = 28
        const val VALID_FOR_OFFSET = 92
    }
}
