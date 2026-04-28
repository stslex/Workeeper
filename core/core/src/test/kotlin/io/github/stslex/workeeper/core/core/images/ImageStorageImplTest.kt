// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.DIRECTORY
import io.github.stslex.workeeper.core.core.images.ImageStorage.Companion.TEMP_SUBDIRECTORY
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(application = ImageStorageImplTest.TestApplication::class, sdk = [33])
internal class ImageStorageImplTest {

    private lateinit var context: Context
    private lateinit var storage: ImageStorageImpl

    private val rootDir: File
        get() = File(context.filesDir, DIRECTORY)

    private val tempDir: File
        get() = File(rootDir, TEMP_SUBDIRECTORY)

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = ImageStorageImpl(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        tempDir.mkdirs()
    }

    @AfterEach
    fun teardown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun `deleteImage returns true and removes existing file`() = runTest {
        val target = File(rootDir, "alpha.jpg").apply { writeBytes(byteArrayOf(0x1, 0x2)) }

        val deleted = storage.deleteImage(target.absolutePath)

        assertTrue(deleted)
        assertFalse(target.exists())
    }

    @Test
    fun `deleteImage returns false when path is absent`() = runTest {
        val target = File(rootDir, "missing.jpg")

        val deleted = storage.deleteImage(target.absolutePath)

        assertFalse(deleted)
    }

    @Test
    fun `deleteImage returns false on blank path`() = runTest {
        assertFalse(storage.deleteImage(""))
    }

    @Test
    fun `cleanupTempFiles removes everything in the temp subdirectory`() = runTest {
        File(tempDir, "leftover-1.jpg").writeBytes(byteArrayOf(0x1))
        File(tempDir, "leftover-2.jpg").writeBytes(byteArrayOf(0x2))

        storage.cleanupTempFiles()

        assertEquals(0, tempDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `cleanupTempFiles does not delete files outside the temp subdirectory`() = runTest {
        val canonical = File(rootDir, "keep.jpg").apply { writeBytes(byteArrayOf(0x9)) }
        File(tempDir, "leftover.jpg").writeBytes(byteArrayOf(0x1))

        storage.cleanupTempFiles()

        assertTrue(canonical.exists())
    }

    class TestApplication : Application()
}
