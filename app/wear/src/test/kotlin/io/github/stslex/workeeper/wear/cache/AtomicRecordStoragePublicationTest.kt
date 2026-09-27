// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import android.os.Build
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
internal class AtomicRecordStoragePublicationApi28Test : AtomicRecordStoragePublicationContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class AtomicRecordStoragePublicationApi33Test : AtomicRecordStoragePublicationContract()

/**
 * `AtomicFile.finishWrite` only logs a failed rename, so "persisted before published" is checked
 * by the storage itself. The one failure a host can inject is a non-empty directory at the target
 * path: on API 30+ the staged `<name>.new` then cannot replace it, on API 28-29 the backup
 * `<name>.bak` cannot be deleted. Rename failures from other causes stay host-uninjectable.
 */
internal abstract class AtomicRecordStoragePublicationContract {
    @TempDir
    lateinit var directory: File

    private val base get() = File(directory, "snapshot")
    private val staged: File
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File("${base.path}.new")
        } else {
            File("${base.path}.bak")
        }

    @Test
    fun completedReplacementLeavesOnlyTheBaseFile() {
        val storage = AtomicFileRecordStorage(base)
        val bytes = byteArrayOf(1, 2, 3)

        storage.replace(bytes)

        assertArrayEquals(bytes, storage.read())
        assertTrue(base.isFile)
        assertFalse(File("${base.path}.new").exists())
        assertFalse(File("${base.path}.bak").exists())
    }

    @Test
    fun unpublishedRenameIsAnIoFailureNotASilentSuccess() {
        assertTrue(base.mkdir())
        assertTrue(File(base, "occupant").createNewFile())
        val storage = AtomicFileRecordStorage(base)

        val error = assertThrows(IOException::class.java) { storage.replace(byteArrayOf(4, 5, 6)) }

        assertTrue(error.message.orEmpty().contains("did not complete"), error.message)
        assertTrue(staged.exists(), "the staged file must still be on disk after the failed publication")
        assertThrows(IOException::class.java) { storage.read() }
    }
}
