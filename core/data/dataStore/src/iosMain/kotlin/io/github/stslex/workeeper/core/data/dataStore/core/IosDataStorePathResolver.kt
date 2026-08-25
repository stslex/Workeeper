// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.core

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS's [DataStorePathResolver]: `<Documents>/datastore/<name>.preferences_pb`, mirroring the
 * Android layout. Not bound into any graph — the iOS composition root does not exist yet.
 */
class IosDataStorePathResolver : DataStorePathResolver {

    override fun resolve(name: String): Path {
        // URLsForDirectory, not URLForDirectory: the single-URL overload's NSError out-param would
        // drag @ExperimentalForeignApi into this file.
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(directory = NSDocumentDirectory, inDomains = NSUserDomainMask)
            .firstOrNull() as? NSURL
        val documentsPath = requireNotNull(documents?.path) {
            "NSDocumentDirectory is unavailable — cannot resolve a DataStore path for $name"
        }
        return documentsPath.toPath().resolve(DATASTORE_DIRECTORY).resolve("$name.$FILE_SUFFIX")
    }

    private companion object {

        const val DATASTORE_DIRECTORY = "datastore"
        const val FILE_SUFFIX = "preferences_pb"
    }
}
