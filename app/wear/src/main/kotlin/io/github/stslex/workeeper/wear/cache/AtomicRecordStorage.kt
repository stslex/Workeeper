// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files

internal interface AtomicRecordStorage {
    fun read(): ByteArray?
    fun replace(bytes: ByteArray)
    fun delete(): Boolean
}

/** AtomicFile-backed single-record store. Cache bytes never use preferences or Android backup. */
internal class AtomicFileRecordStorage(file: File) : AtomicRecordStorage {

    private val atomicFile = AtomicFile(file)
    private val backupFile = File("${file.path}.bak")

    // AtomicFile must attempt backup recovery before the record can be classified as absent.
    override fun read(): ByteArray? = try {
        atomicFile.readFully()
    } catch (error: FileNotFoundException) {
        val baseAbsent = Files.notExists(atomicFile.baseFile.toPath())
        val backupAbsent = Files.notExists(backupFile.toPath())
        if (!baseAbsent || !backupAbsent) throw error
        null
    }

    override fun replace(bytes: ByteArray) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: IOException) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override fun delete(): Boolean {
        atomicFile.delete()
        return !atomicFile.baseFile.exists()
    }
}
